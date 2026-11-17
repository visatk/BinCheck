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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.analytics.FirebaseMetrics
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MetricsTelemetrySectionCard(
    metricsEnabled: Boolean,
    onToggleMetrics: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("firebase_metrics_card"),
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
                        imageVector = AppIcons.Assessment,
                        contentDescription = null,
                        tint = colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.firebase_metrics_title),
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.firebase_metrics_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = metricsEnabled,
                    onCheckedChange = onToggleMetrics,
                    modifier = Modifier.testTag("firebase_metrics_switch")
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val context = LocalContext.current
            val firebaseConfigured = remember(context) { FirebaseMetrics.isRuntimeConfigured(context) }
            val activeLive = metricsEnabled && firebaseConfigured
            val badgeBg = when {
                activeLive -> consoleColors.liveContainer
                metricsEnabled -> consoleColors.unknownContainer
                else -> colorScheme.surfaceContainerHigh
            }
            val badgeFg = when {
                activeLive -> consoleColors.liveAccent
                metricsEnabled -> consoleColors.unknownAccent
                else -> colorScheme.onSurfaceVariant
            }
            val statusWord = when {
                activeLive -> "ON"
                metricsEnabled -> "LOCAL"
                else -> "OFF"
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                MetricsPillBadge("Analytics: $statusWord", badgeBg, badgeFg)
                MetricsPillBadge("Crashlytics: $statusWord", badgeBg, badgeFg)
                MetricsPillBadge("Perf: $statusWord", badgeBg, badgeFg)
                if (metricsEnabled && !firebaseConfigured) {
                    MetricsPillBadge("No google-services.json", consoleColors.unknownContainer, consoleColors.unknownAccent)
                }
                MetricsPillBadge("PII Scrubbed", colorScheme.secondaryContainer, colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun MetricsPillBadge(
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
