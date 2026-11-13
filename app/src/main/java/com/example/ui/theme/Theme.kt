package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val ConsoleDarkColorScheme = darkColorScheme(
    primary = ConsoleDarkPrimary,
    onPrimary = ConsoleDarkOnPrimary,
    primaryContainer = ConsoleDarkPrimaryContainer,
    onPrimaryContainer = ConsoleDarkOnPrimaryContainer,
    secondary = ConsoleDarkSecondary,
    onSecondary = ConsoleDarkOnSecondary,
    secondaryContainer = ConsoleDarkSecondaryContainer,
    onSecondaryContainer = ConsoleDarkOnSecondaryContainer,
    tertiary = ConsoleDarkTertiary,
    onTertiary = ConsoleDarkOnTertiary,
    tertiaryContainer = ConsoleDarkTertiaryContainer,
    onTertiaryContainer = ConsoleDarkOnTertiaryContainer,
    error = ConsoleDarkError,
    onError = ConsoleDarkOnError,
    errorContainer = ConsoleDarkErrorContainer,
    onErrorContainer = ConsoleDarkOnErrorContainer,
    background = ConsoleDarkBackground,
    onBackground = ConsoleDarkOnBackground,
    surface = ConsoleDarkSurface,
    onSurface = ConsoleDarkOnSurface,
    surfaceVariant = ConsoleDarkSurfaceVariant,
    onSurfaceVariant = ConsoleDarkOnSurfaceVariant,
    outline = ConsoleDarkOutline,
    outlineVariant = ConsoleDarkOutlineVariant,
    surfaceContainerLowest = ConsoleDarkSurfaceContainerLowest,
    surfaceContainerLow = ConsoleDarkSurfaceContainerLow,
    surfaceContainer = ConsoleDarkSurfaceContainer,
    surfaceContainerHigh = ConsoleDarkSurfaceContainerHigh,
    surfaceContainerHighest = ConsoleDarkSurfaceContainerHighest
)

private val ConsoleLightColorScheme = lightColorScheme(
    primary = ConsoleLightPrimary,
    onPrimary = ConsoleLightOnPrimary,
    primaryContainer = ConsoleLightPrimaryContainer,
    onPrimaryContainer = ConsoleLightOnPrimaryContainer,
    secondary = ConsoleLightSecondary,
    onSecondary = ConsoleLightOnSecondary,
    secondaryContainer = ConsoleLightSecondaryContainer,
    onSecondaryContainer = ConsoleLightOnSecondaryContainer,
    tertiary = ConsoleLightTertiary,
    onTertiary = ConsoleLightOnTertiary,
    tertiaryContainer = ConsoleLightTertiaryContainer,
    onTertiaryContainer = ConsoleLightOnTertiaryContainer,
    error = ConsoleLightError,
    onError = ConsoleLightOnError,
    errorContainer = ConsoleLightErrorContainer,
    onErrorContainer = ConsoleLightOnErrorContainer,
    background = ConsoleLightBackground,
    onBackground = ConsoleLightOnBackground,
    surface = ConsoleLightSurface,
    onSurface = ConsoleLightOnSurface,
    surfaceVariant = ConsoleLightSurfaceVariant,
    onSurfaceVariant = ConsoleLightOnSurfaceVariant,
    outline = ConsoleLightOutline,
    outlineVariant = ConsoleLightOutlineVariant,
    surfaceContainerLowest = ConsoleLightSurfaceContainerLowest,
    surfaceContainerLow = ConsoleLightSurfaceContainerLow,
    surfaceContainer = ConsoleLightSurfaceContainer,
    surfaceContainerHigh = ConsoleLightSurfaceContainerHigh,
    surfaceContainerHighest = ConsoleLightSurfaceContainerHighest
)

@Immutable
data class ConsoleSemanticColors(
    val liveAccent: Color,
    val liveContainer: Color,
    val dieAccent: Color,
    val dieContainer: Color,
    val unknownAccent: Color,
    val unknownContainer: Color,
    val telegramAccent: Color,
    val telegramContainer: Color,
    val onTelegramAccent: Color,
    val isDark: Boolean
)

val LocalConsoleColors = staticCompositionLocalOf {
    ConsoleSemanticColors(
        liveAccent = LiveEmeraldDark,
        liveContainer = LiveEmeraldContainerDark,
        dieAccent = DieCrimsonDark,
        dieContainer = DieCrimsonContainerDark,
        unknownAccent = UnknownAmberDark,
        unknownContainer = UnknownAmberContainerDark,
        telegramAccent = TelegramSkyDark,
        telegramContainer = TelegramSkyContainerDark,
        onTelegramAccent = Color(0xFF041A27),
        isDark = true
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val supportsDynamicColor = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colorScheme = when {
        dynamicColor && supportsDynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor && supportsDynamicColor && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> ConsoleDarkColorScheme
        else -> ConsoleLightColorScheme
    }

    val semanticColors = if (darkTheme) {
        ConsoleSemanticColors(
            liveAccent = LiveEmeraldDark,
            liveContainer = LiveEmeraldContainerDark,
            dieAccent = colorScheme.error,
            dieContainer = colorScheme.errorContainer,
            unknownAccent = UnknownAmberDark,
            unknownContainer = UnknownAmberContainerDark,
            telegramAccent = TelegramSkyDark,
            telegramContainer = TelegramSkyContainerDark,
            onTelegramAccent = Color(0xFF041A27),
            isDark = true
        )
    } else {
        ConsoleSemanticColors(
            liveAccent = LiveEmeraldLight,
            liveContainer = LiveEmeraldContainerLight,
            dieAccent = colorScheme.error,
            dieContainer = colorScheme.errorContainer,
            unknownAccent = UnknownAmberLight,
            unknownContainer = UnknownAmberContainerLight,
            telegramAccent = TelegramSkyLight,
            telegramContainer = TelegramSkyContainerLight,
            onTelegramAccent = Color.White,
            isDark = false
        )
    }

    CompositionLocalProvider(LocalConsoleColors provides semanticColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = AppShapes,
            typography = Typography,
            content = content
        )
    }
}
