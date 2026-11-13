package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CardScheme
import com.example.domain.HttpRequestMethod
import com.example.domain.ValidationMode
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BinCheckTopBar(
    showMenuButton: Boolean,
    isRunning: Boolean,
    isLookingUpBin: Boolean,
    processedCount: Int,
    totalBatchCount: Int,
    validationMode: ValidationMode,
    httpMethod: HttpRequestMethod,
    concurrency: Int,
    historyCount: Int,
    onOpenDrawer: () -> Unit,
    onOpenBinLookup: () -> Unit,
    onOpenHistory: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val engineSummary = when {
        validationMode.fixedDelayMs != null ->
            "${validationMode.badgeLabel} • ${validationMode.fixedDelayMs / 1000L}s delay"
        validationMode == ValidationMode.REMOTE_WEBHOOK ->
            "HTTP ${httpMethod.label} • ${concurrency}x"
        else ->
            "${validationMode.badgeLabel} • ${concurrency}x"
    }

    TopAppBar(
        modifier = modifier,
        windowInsets = WindowInsets(0, 0, 0, 0),
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            titleContentColor = colorScheme.onSurface
        ),
        navigationIcon = {
            if (showMenuButton) {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("open_navigation_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Menu,
                        contentDescription = stringResource(R.string.nav_open_drawer),
                        tint = colorScheme.onSurface
                    )
                }
            } else {
                Box(modifier = Modifier.padding(start = 14.dp, end = 6.dp)) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = colorScheme.primaryContainer,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "BC",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFamily,
                                fontWeight = FontWeight.Bold,
                                color = colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = colorScheme.onSurface,
                        maxLines = 1
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isRunning) consoleColors.liveAccent else colorScheme.primary)
                        )
                        Text(
                            text = if (isRunning && totalBatchCount > 0) {
                                "Validating $processedCount / $totalBatchCount"
                            } else {
                                engineSummary
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isRunning) consoleColors.liveAccent else colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                AnimatedVisibility(visible = isRunning || isLookingUpBin) {
                    LoadingIndicator(
                        modifier = Modifier
                            .size(20.dp)
                            .testTag("topbar_loading_indicator"),
                        color = consoleColors.liveAccent
                    )
                }
            }
        },
        actions = {
            FilledTonalIconButton(
                onClick = onOpenBinLookup,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = colorScheme.surfaceContainerHigh,
                    contentColor = colorScheme.onSurface
                ),
                modifier = Modifier.testTag("topbar_bin_lookup_button")
            ) {
                Icon(
                    imageVector = AppIcons.AccountBalance,
                    contentDescription = stringResource(R.string.bin_lookup_chip),
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier.testTag("open_history_button")
            ) {
                BadgedBox(
                    badge = {
                        if (historyCount > 0) {
                            Badge(
                                containerColor = colorScheme.primaryContainer,
                                contentColor = colorScheme.onPrimaryContainer
                            ) {
                                Text(
                                    text = if (historyCount > 99) "99+" else historyCount.toString(),
                                    style = MaterialTheme.typography.labelSmall
                                )
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = AppIcons.History,
                        contentDescription = stringResource(R.string.history_chip),
                        tint = colorScheme.onSurface
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TopTelemetryStrip(
    binDigits: String,
    detectedScheme: CardScheme,
    loadedFileName: String?,
    onOpenBinLookup: () -> Unit,
    onOpenDeveloperChannel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                onClick = onOpenBinLookup,
                shape = CircleShape,
                color = if (detectedScheme != CardScheme.UNKNOWN) {
                    colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    colorScheme.surfaceContainerHigh
                },
                modifier = Modifier.testTag("telemetry_bin_pill")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppIcons.AccountBalance,
                        contentDescription = null,
                        tint = if (detectedScheme != CardScheme.UNKNOWN) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = if (detectedScheme != CardScheme.UNKNOWN) {
                            "BIN $binDigits • ${detectedScheme.displayName}"
                        } else {
                            "BIN $binDigits • Inspect"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        fontWeight = FontWeight.Medium,
                        color = if (detectedScheme != CardScheme.UNKNOWN) {
                            colorScheme.onPrimaryContainer
                        } else {
                            colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            if (!loadedFileName.isNullOrBlank()) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppIcons.Description,
                            contentDescription = null,
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = loadedFileName,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        Surface(
            onClick = onOpenDeveloperChannel,
            shape = CircleShape,
            color = colorScheme.surfaceContainerHigh.copy(alpha = 0.72f),
            modifier = Modifier.testTag("header_developer_channel_button")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelegramPaperPlaneIcon(
                    color = consoleColors.telegramAccent,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = stringResource(R.string.developer_channel_handle),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun TelegramPaperPlaneIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.08f, h * 0.48f)
            lineTo(w * 0.92f, h * 0.14f)
            lineTo(w * 0.74f, h * 0.88f)
            lineTo(w * 0.46f, h * 0.64f)
            lineTo(w * 0.32f, h * 0.78f)
            lineTo(w * 0.35f, h * 0.56f)
            lineTo(w * 0.76f, h * 0.26f)
            lineTo(w * 0.28f, h * 0.53f)
            close()
        }
        drawPath(
            path = path,
            color = color
        )
    }
}
