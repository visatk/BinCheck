package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.BinInfo
import com.example.domain.BinLookupService
import com.example.domain.ResultBucket
import com.example.domain.ValidationRecord
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import com.example.ui.theme.PlusJakartaSansFamily
import com.example.ui.theme.SpaceGroteskFamily

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ResultsCard(
    uiState: ValidatorUiState,
    fillRemainingHeight: Boolean,
    onSelectTab: (ResultBucket) -> Unit,
    onCopyAll: (title: String, joinedLines: String) -> Unit,
    onClearTab: () -> Unit,
    onCopyRecord: (ValidationRecord) -> Unit,
    onRemoveRecord: (ValidationRecord) -> Unit,
    onInspectBin: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val haptics = LocalHapticFeedback.current

    val totalProcessedInSession = uiState.liveRecords.size + uiState.dieRecords.size + uiState.unknownRecords.size
    val liveRatePercent = remember(uiState.liveRecords.size, totalProcessedInSession) {
        if (totalProcessedInSession > 0) {
            ((uiState.liveRecords.size * 100f) / totalProcessedInSession).toInt()
        } else {
            0
        }
    }

    val activeBorderColor by animateColorAsState(
        targetValue = when {
            uiState.activeTabRecords.isEmpty() -> colorScheme.outlineVariant.copy(alpha = 0.45f)
            uiState.selectedTab == ResultBucket.LIVE -> consoleColors.liveAccent.copy(alpha = 0.42f)
            uiState.selectedTab == ResultBucket.DIE -> consoleColors.dieAccent.copy(alpha = 0.34f)
            else -> consoleColors.unknownAccent.copy(alpha = 0.38f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "results_card_border"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)),
        shape = MaterialTheme.shapes.large,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(0.75.dp, activeBorderColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colorScheme.surfaceContainer)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BucketToggleItem(
                    label = stringResource(R.string.tab_live),
                    count = uiState.liveRecords.size,
                    selected = uiState.selectedTab == ResultBucket.LIVE,
                    icon = AppIcons.CheckCircle,
                    accentColor = consoleColors.liveAccent,
                    containerColor = consoleColors.liveContainer,
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
                    onClick = { onSelectTab(ResultBucket.LIVE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_live")
                )

                BucketToggleItem(
                    label = stringResource(R.string.tab_die),
                    count = uiState.dieRecords.size,
                    selected = uiState.selectedTab == ResultBucket.DIE,
                    icon = AppIcons.ErrorOutline,
                    accentColor = consoleColors.dieAccent,
                    containerColor = consoleColors.dieContainer,
                    shapes = ButtonGroupDefaults.connectedMiddleButtonShapes(),
                    onClick = { onSelectTab(ResultBucket.DIE) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_die")
                )

                BucketToggleItem(
                    label = stringResource(R.string.tab_unknown),
                    count = uiState.unknownRecords.size,
                    selected = uiState.selectedTab == ResultBucket.UNKNOWN,
                    icon = AppIcons.HelpOutline,
                    accentColor = consoleColors.unknownAccent,
                    containerColor = consoleColors.unknownContainer,
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
                    onClick = { onSelectTab(ResultBucket.UNKNOWN) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tab_unknown")
                )
            }

            HorizontalDivider(
                thickness = 0.5.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            AnimatedVisibility(visible = totalProcessedInSession > 0) {
                val liveWeight = (uiState.liveRecords.size.toFloat() / totalProcessedInSession.coerceAtLeast(1)).coerceAtLeast(0.001f)
                val dieWeight = (uiState.dieRecords.size.toFloat() / totalProcessedInSession.coerceAtLeast(1)).coerceAtLeast(0.001f)
                val unknownWeight = (uiState.unknownRecords.size.toFloat() / totalProcessedInSession.coerceAtLeast(1)).coerceAtLeast(0.001f)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                ) {
                    if (uiState.liveRecords.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(liveWeight)
                                .height(3.dp)
                                .background(consoleColors.liveAccent)
                        )
                    }
                    if (uiState.dieRecords.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(dieWeight)
                                .height(3.dp)
                                .background(consoleColors.dieAccent)
                        )
                    }
                    if (uiState.unknownRecords.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .weight(unknownWeight)
                                .height(3.dp)
                                .background(consoleColors.unknownAccent)
                        )
                    }
                }
            }

            val activeList = uiState.activeTabRecords
            if (activeList.isEmpty()) {
                val emptyAccent = when (uiState.selectedTab) {
                    ResultBucket.LIVE -> consoleColors.liveAccent
                    ResultBucket.DIE -> consoleColors.dieAccent
                    ResultBucket.UNKNOWN -> consoleColors.unknownAccent
                }
                val emptyContainer = when (uiState.selectedTab) {
                    ResultBucket.LIVE -> consoleColors.liveContainer
                    ResultBucket.DIE -> consoleColors.dieContainer
                    ResultBucket.UNKNOWN -> consoleColors.unknownContainer
                }
                val emptyIcon = when (uiState.selectedTab) {
                    ResultBucket.LIVE -> AppIcons.CheckCircle
                    ResultBucket.DIE -> AppIcons.ErrorOutline
                    ResultBucket.UNKNOWN -> AppIcons.HelpOutline
                }
                val emptyHeadline = when (uiState.selectedTab) {
                    ResultBucket.LIVE -> "No Live records yet"
                    ResultBucket.DIE -> "Nothing declined yet"
                    ResultBucket.UNKNOWN -> "No unresolved lines"
                }
                val emptySubtitle = when (uiState.selectedTab) {
                    ResultBucket.LIVE -> "Paste cards or import a file above, then tap Validate"
                    ResultBucket.DIE -> "Cards that fail Luhn, expiry, or gateway checks show up here"
                    ResultBucket.UNKNOWN -> "Broken lines or timed-out responses will wait here"
                }

                if (fillRemainingHeight) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = emptyContainer.copy(alpha = 0.72f),
                            border = BorderStroke(0.75.dp, emptyAccent.copy(alpha = 0.28f)),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = emptyIcon,
                                    contentDescription = null,
                                    tint = emptyAccent,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = emptyHeadline,
                            style = MaterialTheme.typography.titleSmallEmphasized,
                            color = colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = emptySubtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        if (uiState.selectedTab == ResultBucket.LIVE) {
                            Spacer(modifier = Modifier.height(12.dp))
                            FilledTonalButton(
                                onClick = { onInspectBin("") },
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(38.dp)
                                    .testTag("open_bin_lookup_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.AccountBalance,
                                    contentDescription = stringResource(R.string.bin_lookup_chip),
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.bin_lookup_chip),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 74.dp)
                            .background(colorScheme.surfaceContainerLow)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = emptyContainer.copy(alpha = 0.72f),
                                border = BorderStroke(0.75.dp, emptyAccent.copy(alpha = 0.28f)),
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = emptyIcon,
                                        contentDescription = null,
                                        tint = emptyAccent,
                                        modifier = Modifier.size(17.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = emptyHeadline,
                                    style = MaterialTheme.typography.titleSmallEmphasized,
                                    color = colorScheme.onSurface
                                )
                                Text(
                                    text = emptySubtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (uiState.selectedTab == ResultBucket.LIVE) {
                            Spacer(modifier = Modifier.width(8.dp))
                            FilledTonalButton(
                                onClick = { onInspectBin("") },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.surfaceContainerHigh,
                                    contentColor = colorScheme.onSurface
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("open_bin_lookup_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.AccountBalance,
                                    contentDescription = stringResource(R.string.bin_lookup_chip),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.bin_lookup_chip),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            } else {
                val bodyModifier = if (fillRemainingHeight) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier.fillMaxWidth()
                }
                Column(
                    modifier = bodyModifier
                        .background(colorScheme.surfaceContainerLow)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tabTitle = when (uiState.selectedTab) {
                            ResultBucket.LIVE -> "LIVE (${activeList.size})"
                            ResultBucket.DIE -> "DIE (${activeList.size})"
                            ResultBucket.UNKNOWN -> "UNKNOWN (${activeList.size})"
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.align(Alignment.CenterVertically)
                        ) {
                            Text(
                                text = tabTitle,
                                style = MaterialTheme.typography.labelLargeEmphasized,
                                fontFamily = SpaceGroteskFamily,
                                color = colorScheme.onSurface
                            )
                            if (totalProcessedInSession > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = consoleColors.liveContainer.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = "$liveRatePercent% Live",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = consoleColors.liveAccent,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.selectedTab == ResultBucket.LIVE) {
                                FilledTonalButton(
                                    onClick = {
                                        val firstPan = activeList.firstOrNull()?.pan.orEmpty()
                                        onInspectBin(firstPan)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                    shape = CircleShape,
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = colorScheme.surfaceContainerHigh,
                                        contentColor = colorScheme.onSurface
                                    ),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("open_bin_lookup_button")
                                ) {
                                    Icon(
                                        imageVector = AppIcons.AccountBalance,
                                        contentDescription = stringResource(R.string.bin_lookup_chip),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = stringResource(R.string.bin_lookup_chip),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = PlusJakartaSansFamily,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val joined = activeList.joinToString("\n") { it.normalizedLine }
                                    onCopyAll(tabTitle, joined)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("copy_all_records_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_all),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = stringResource(R.string.copy_all),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            TextButton(
                                onClick = onClearTab,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                shape = CircleShape,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = colorScheme.error
                                ),
                                modifier = Modifier
                                    .height(34.dp)
                                    .testTag("clear_tab_records_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.DeleteOutline,
                                    contentDescription = stringResource(R.string.clear_tab),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.clear_tab),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = PlusJakartaSansFamily,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }

                    val distinctLiveSchemes = remember(uiState.liveRecords) {
                        uiState.liveRecords.map { it.scheme.displayName }.distinct().take(3)
                    }
                    val distinctLiveBinsCount = remember(uiState.liveRecords) {
                        uiState.liveRecords.map { BinLookupService.extractBin6(it.pan) }
                            .filter { it.length == 6 }
                            .distinct()
                            .size
                    }

                    AnimatedVisibility(
                        visible = uiState.selectedTab == ResultBucket.LIVE && activeList.isNotEmpty()
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = consoleColors.liveContainer.copy(alpha = 0.42f),
                            border = BorderStroke(0.75.dp, consoleColors.liveAccent.copy(alpha = 0.30f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .testTag("live_merchandising_strip")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = AppIcons.CheckCircle,
                                        contentDescription = null,
                                        tint = consoleColors.liveAccent,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = buildString {
                                            append("${activeList.size} Verified")
                                            if (distinctLiveBinsCount > 0) {
                                                append(" • $distinctLiveBinsCount BIN")
                                                if (distinctLiveBinsCount > 1) append("s")
                                            }
                                            if (distinctLiveSchemes.isNotEmpty()) {
                                                append(" (${distinctLiveSchemes.joinToString("/")})")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(
                                    text = "$liveRatePercent% HIT RATE",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Bold,
                                    color = consoleColors.liveAccent
                                )
                            }
                        }
                    }

                    val listModifier = if (fillRemainingHeight) {
                        Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 300.dp)
                    }

                    LazyColumn(
                        modifier = listModifier,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = activeList,
                            key = { it.id },
                            contentType = { it.bucket }
                        ) { record ->
                            val bin6 = BinLookupService.extractBin6(record.pan)
                            val binInfo = if (record.bucket == ResultBucket.LIVE && bin6.length == 6) {
                                uiState.binLookupMap[bin6]
                            } else {
                                null
                            }
                            val isBinLoading = record.bucket == ResultBucket.LIVE && bin6 in uiState.loadingBins
                            ValidationRecordRow(
                                record = record,
                                binInfo = binInfo,
                                isBinLoading = isBinLoading,
                                onInspectBin = { onInspectBin(record.pan) },
                                onCopy = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onCopyRecord(record)
                                },
                                onRemove = { onRemoveRecord(record) },
                                modifier = Modifier.animateItem(
                                    fadeInSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                    fadeOutSpec = spring(stiffness = Spring.StiffnessMedium)
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BucketToggleItem(
    label: String,
    count: Int,
    selected: Boolean,
    icon: ImageVector,
    accentColor: Color,
    containerColor: Color,
    shapes: ToggleButtonShapes,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        shapes = shapes,
        colors = ToggleButtonDefaults.toggleButtonColors(
            containerColor = colorScheme.surfaceContainerLow,
            contentColor = colorScheme.onSurfaceVariant,
            checkedContainerColor = containerColor,
            checkedContentColor = accentColor
        ),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        modifier = modifier.height(42.dp)
    ) {
        if (selected) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            fontFamily = PlusJakartaSansFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(
                    if (selected) accentColor.copy(alpha = 0.16f) else colorScheme.surfaceContainerHighest
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = count,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "badge_count_$label"
            ) { targetCount ->
                Text(
                    text = targetCount.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.Bold,
                    color = if (selected || targetCount > 0) accentColor else colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ValidationRecordRow(
    record: ValidationRecord,
    binInfo: BinInfo?,
    isBinLoading: Boolean,
    onInspectBin: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current
    val accentColor = when (record.bucket) {
        ResultBucket.LIVE -> consoleColors.liveAccent
        ResultBucket.DIE -> consoleColors.dieAccent
        ResultBucket.UNKNOWN -> consoleColors.unknownAccent
    }
    val containerAccent = when (record.bucket) {
        ResultBucket.LIVE -> consoleColors.liveContainer
        ResultBucket.DIE -> consoleColors.dieContainer
        ResultBucket.UNKNOWN -> consoleColors.unknownContainer
    }

    Surface(
        onClick = onCopy,
        shape = MaterialTheme.shapes.medium,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.75.dp, accentColor.copy(alpha = 0.24f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("record_row_${record.pan}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(38.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.normalizedLine,
                    style = MaterialTheme.typography.labelMedium,
                    fontFamily = JetBrainsMonoFamily,
                    fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(containerAccent.copy(alpha = 0.78f))
                            .padding(horizontal = 7.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = record.scheme.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = PlusJakartaSansFamily,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "${record.message} (${record.latencyMs}ms)",
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (record.bucket == ResultBucket.LIVE) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        onClick = onInspectBin,
                        shape = CircleShape,
                        color = colorScheme.surfaceContainer,
                        border = BorderStroke(0.5.dp, colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.testTag("live_bin_badge_${record.pan}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            if (isBinLoading && binInfo == null) {
                                LoadingIndicator(
                                    modifier = Modifier.size(12.dp),
                                    color = consoleColors.liveAccent
                                )
                                Text(
                                    text = "Looking up BIN ${BinLookupService.extractBin6(record.pan)}...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            } else if (binInfo != null) {
                                Icon(
                                    imageVector = AppIcons.AccountBalance,
                                    contentDescription = null,
                                    tint = consoleColors.liveAccent,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = binInfo.compactSummary(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Icon(
                                    imageVector = AppIcons.AccountBalance,
                                    contentDescription = null,
                                    tint = colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "BIN ${BinLookupService.extractBin6(record.pan)} • Lookup",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCopy) {
                    Icon(
                        imageVector = AppIcons.ContentCopy,
                        contentDescription = "Copy line",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = AppIcons.DeleteOutline,
                        contentDescription = "Remove line",
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }
    }
}
