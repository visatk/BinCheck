package com.example.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.R
import com.example.ui.theme.LocalConsoleColors

@Immutable
data class AppPermissionSnapshot(
    val notificationsGranted: Boolean,
    val networkAccessGranted: Boolean,
    val documentPickerReady: Boolean = true,
    val requiresRuntimeNotificationPrompt: Boolean
)

object AppPermissionHelper {

    fun requiredRuntimePermissions(sdkInt: Int = Build.VERSION.SDK_INT): List<String> {
        return if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyList()
        }
    }

    fun missingRuntimePermissions(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): List<String> {
        return requiredRuntimePermissions(sdkInt).filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED
        }
    }

    fun areNotificationsAllowed(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): Boolean {
        val runtimeGranted = if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
        return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun inspectSnapshot(
        context: Context,
        sdkInt: Int = Build.VERSION.SDK_INT
    ): AppPermissionSnapshot {
        val internetGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.INTERNET
        ) == PackageManager.PERMISSION_GRANTED
        val networkStateGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_NETWORK_STATE
        ) == PackageManager.PERMISSION_GRANTED
        return AppPermissionSnapshot(
            notificationsGranted = areNotificationsAllowed(context, sdkInt),
            networkAccessGranted = internetGranted && networkStateGranted,
            documentPickerReady = true,
            requiresRuntimeNotificationPrompt = missingRuntimePermissions(context, sdkInt).isNotEmpty()
        )
    }

    fun openNotificationOrAppSettings(context: Context) {
        val notificationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching {
            context.startActivity(notificationIntent)
        }.onFailure {
            val fallback = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallback) }
        }
    }
}

@Composable
fun StartupPermissionsPromptEffect(
    enabled: Boolean,
    onResult: (allGranted: Boolean) -> Unit
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current
    var hasPromptedThisLaunch by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val allGranted = grants.values.all { it }
        onResult(allGranted)
    }

    LaunchedEffect(enabled) {
        if (!enabled || isInspection || hasPromptedThisLaunch) return@LaunchedEffect
        val missing = AppPermissionHelper.missingRuntimePermissions(context)
        hasPromptedThisLaunch = true
        if (missing.isNotEmpty()) {
            launcher.launch(missing.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PermissionsAccessSectionCard(
    snapshot: AppPermissionSnapshot,
    onRequestPermissions: () -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("permissions_access_card"),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppIcons.VerifiedUser,
                    contentDescription = null,
                    tint = colorScheme.primary,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.permissions_section_title),
                        style = MaterialTheme.typography.titleSmallEmphasized,
                        color = colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.permissions_section_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(10.dp))

            PermissionStatusRow(
                title = "Batch Alerts",
                subtitle = "Lets you know when a background run finishes",
                statusLabel = if (snapshot.notificationsGranted) "Allowed" else "Off",
                isGranted = snapshot.notificationsGranted,
                actionLabel = if (!snapshot.notificationsGranted) {
                    if (snapshot.requiresRuntimeNotificationPrompt) "Allow" else "Settings"
                } else {
                    null
                },
                onActionClick = {
                    if (snapshot.requiresRuntimeNotificationPrompt) {
                        onRequestPermissions()
                    } else {
                        onOpenSystemSettings()
                    }
                },
                testTag = "permission_notifications_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionStatusRow(
                title = "Network & API Access",
                subtitle = "Needed for BIN lookups, gateways, and Telegram alerts",
                statusLabel = if (snapshot.networkAccessGranted) "Active" else "Restricted",
                isGranted = snapshot.networkAccessGranted,
                actionLabel = null,
                onActionClick = {},
                testTag = "permission_network_row"
            )

            Spacer(modifier = Modifier.height(8.dp))

            PermissionStatusRow(
                title = "Document Picker (SAF)",
                subtitle = "Reads only the .txt or .html file you pick — no broad storage access",
                statusLabel = if (snapshot.documentPickerReady) "Ready" else "Off",
                isGranted = snapshot.documentPickerReady,
                actionLabel = null,
                onActionClick = {},
                testTag = "permission_files_row"
            )
        }
    }
}

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    statusLabel: String,
    isGranted: Boolean,
    actionLabel: String?,
    onActionClick: () -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                Surface(
                    shape = MaterialTheme.shapes.extraSmall,
                    color = if (isGranted) consoleColors.liveContainer else consoleColors.unknownContainer
                ) {
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isGranted) consoleColors.liveAccent else consoleColors.unknownAccent,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        if (actionLabel != null) {
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalButton(
                onClick = onActionClick,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .height(36.dp)
                    .testTag("${testTag}_action")
            ) {
                Text(actionLabel, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
