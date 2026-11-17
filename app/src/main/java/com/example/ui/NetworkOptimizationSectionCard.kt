package com.example.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DeviceNetworkStatus
import com.example.domain.NetworkTelemetrySnapshot
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NetworkOptimizationSectionCard(
    networkStats: NetworkTelemetrySnapshot,
    deviceNetworkStatus: DeviceNetworkStatus,
    deduplicateBatchInputs: Boolean,
    wifiOnlyBatch: Boolean,
    batterySaverMode: Boolean,
    onToggleDedupCache: (Boolean) -> Unit,
    onToggleWifiOnly: (Boolean) -> Unit,
    onToggleBatterySaver: (Boolean) -> Unit,
    onClearCaches: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.Speed,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.network_optimizer_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.network_optimizer_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("network_telemetry_strip")
            ) {
                TelemetryMetricBadge(
                    label = "HTTP: ${networkStats.httpRequestsSent}",
                    container = colorScheme.surfaceContainerHigh,
                    content = colorScheme.onSurface
                )
                TelemetryMetricBadge(
                    label = "Saved: ${networkStats.totalRequestsSaved}",
                    container = consoleColors.liveContainer,
                    content = consoleColors.liveAccent
                )
                TelemetryMetricBadge(
                    label = "Retries: ${networkStats.retriesPerformed}",
                    container = colorScheme.secondaryContainer,
                    content = colorScheme.onSecondaryContainer
                )
                if (networkStats.circuitBreakerTrips > 0) {
                    TelemetryMetricBadge(
                        label = "Breakers: ${networkStats.circuitBreakerTrips}",
                        container = consoleColors.unknownContainer,
                        content = consoleColors.unknownAccent
                    )
                }
                if (deviceNetworkStatus.isMetered) {
                    TelemetryMetricBadge(
                        label = "Metered Data",
                        container = colorScheme.tertiaryContainer,
                        content = colorScheme.onTertiaryContainer
                    )
                }
                if (deviceNetworkStatus.shouldThrottleForBatteryOrData) {
                    TelemetryMetricBadge(
                        label = "OS Eco Active",
                        container = consoleColors.liveContainer,
                        content = consoleColors.liveAccent
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))
            Spacer(modifier = Modifier.height(10.dp))

            OptimizerSwitchRow(
                title = "Skip Duplicate Requests",
                subtitle = "Reuses fresh cache hits and merges identical lookups in flight",
                icon = AppIcons.SyncAlt,
                checked = deduplicateBatchInputs,
                onCheckedChange = onToggleDedupCache,
                testTag = "dedup_cache_switch"
            )

            Spacer(modifier = Modifier.height(8.dp))

            OptimizerSwitchRow(
                title = "Wait for Wi-Fi on Remote Runs",
                subtitle = "Holds remote API and webhook batches until you're on Wi-Fi",
                icon = AppIcons.Public,
                checked = wifiOnlyBatch,
                onCheckedChange = onToggleWifiOnly,
                testTag = "wifi_only_batch_switch"
            )

            Spacer(modifier = Modifier.height(8.dp))

            OptimizerSwitchRow(
                title = "Gentle Battery & Data Mode",
                subtitle = "Caps workers to 2x, pauses low-battery runs, and skips metered BIN sync",
                icon = AppIcons.BatterySaver,
                checked = batterySaverMode,
                onCheckedChange = onToggleBatterySaver,
                testTag = "battery_saver_switch"
            )

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onClearCaches,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("clear_network_cache_button"),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = AppIcons.CleaningServices,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Clear Cached Data & Reset Breakers",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun OptimizerSwitchRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = colorScheme.secondary,
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun TelemetryMetricBadge(
    label: String,
    container: Color,
    content: Color
) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            color = content
        )
    }
}
