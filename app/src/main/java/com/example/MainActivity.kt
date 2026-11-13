package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.AppThemeMode
import com.example.ui.BinCheckSplashOverlay
import com.example.ui.ValidatorScreen
import com.example.ui.ValidatorViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val validatorViewModel: ValidatorViewModel = viewModel(factory = ValidatorViewModel.Factory)
            val uiState by validatorViewModel.uiState.collectAsStateWithLifecycle()
            var showSplash by rememberSaveable { mutableStateOf(true) }

            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (uiState.settings.themeMode) {
                AppThemeMode.SYSTEM -> systemDark
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
            }

            DisposableEffect(darkTheme) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    },
                    navigationBarStyle = if (darkTheme) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    }
                )
                onDispose {}
            }

            MyApplicationTheme(
                darkTheme = darkTheme,
                dynamicColor = uiState.settings.useDynamicColor
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    ValidatorScreen(
                        viewModel = validatorViewModel,
                        requestStartupPermissions = !showSplash,
                        modifier = Modifier.fillMaxSize()
                    )

                    BinCheckSplashOverlay(
                        visible = showSplash,
                        onFinished = { showSplash = false }
                    )
                }
            }
        }
    }
}
