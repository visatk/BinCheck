package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ResultBucket
import com.example.domain.ValidationRecord
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ResultsCard(
    uiState: ValidatorUiState,
    fillRemainingHeight: Boolean,
    onSelectTab: (ResultBucket) -> Unit,
    onCopyAll: (title: String, joinedLines: String) -> Unit,
    onClearTab: () -> Unit,
    onCopyRecord: (ValidationRecord) -> Unit,
    onRemoveRecord: (ValidationRecord) -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.85f),
                shape = MaterialTheme.shapes.medium
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // M3 3-Tab Header with Tonal Container Elevation
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colorScheme.surfaceContainer),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResultTabItem(
                    label = stringResource(R.string.tab_live),
                    count = uiState.liveRecords.size,
                    selected = uiState.selectedTab == ResultBucket.LIVE,
                    labelColor = consoleColors.liveAccent,
                    badgeBackgroundColor = consoleColors.liveContainer,
                    badgeTextColor = consoleColors.liveAccent,
                    topAccentColor = consoleColors.liveAccent,
                    onClick = { onSelectTab(ResultBucket.LIVE) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tab_live")
                )

                ResultTabItem(
                    label = stringResource(R.string.tab_die),
                    count = uiState.dieRecords.size,
                    selected = uiState.selectedTab == ResultBucket.DIE,
                    labelColor = if (uiState.dieRecords.isNotEmpty() || uiState.selectedTab == ResultBucket.DIE) {
                        consoleColors.dieAccent
                    } else {
                        colorScheme.onSurface
                    },
                    badgeBackgroundColor = if (uiState.dieRecords.isNotEmpty()) {
                        consoleColors.dieContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    badgeTextColor = if (uiState.dieRecords.isNotEmpty()) {
                        consoleColors.dieAccent
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    topAccentColor = consoleColors.dieAccent,
                    onClick = { onSelectTab(ResultBucket.DIE) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tab_die")
                )

                ResultTabItem(
                    label = stringResource(R.string.tab_unknown),
                    count = uiState.unknownRecords.size,
                    selected = uiState.selectedTab == ResultBucket.UNKNOWN,
                    labelColor = if (uiState.unknownRecords.isNotEmpty() || uiState.selectedTab == ResultBucket.UNKNOWN) {
                        consoleColors.unknownAccent
                    } else {
                        colorScheme.onSurface
                    },
                    badgeBackgroundColor = if (uiState.unknownRecords.isNotEmpty()) {
                        consoleColors.unknownContainer
                    } else {
                        colorScheme.surfaceContainerHighest
                    },
                    badgeTextColor = if (uiState.unknownRecords.isNotEmpty()) {
                        consoleColors.unknownAccent
                    } else {
                        colorScheme.onSurfaceVariant
                    },
                    topAccentColor = consoleColors.unknownAccent,
                    onClick = { onSelectTab(ResultBucket.UNKNOWN) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tab_unknown")
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.85f)
            )

            val activeList = uiState.activeTabRecords
            if (activeList.isEmpty()) {
                val emptyBoxModifier = if (fillRemainingHeight) {
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                }
                Box(
                    modifier = emptyBoxModifier
                        .background(colorScheme.surfaceContainerLow)
                        .padding(horizontal = 16.dp),
                    contentAlignment = if (fillRemainingHeight) Alignment.Center else Alignment.CenterStart
                ) {
                    Text(
                        text = when (uiState.selectedTab) {
                            ResultBucket.LIVE -> "No Live records yet — tap Play to validate batch"
                            ResultBucket.DIE -> "No declined / invalid records"
                            ResultBucket.UNKNOWN -> "No unknown format records"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
                    )
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
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val tabTitle = when (uiState.selectedTab) {
                            ResultBucket.LIVE -> "LIVE RECORDS (${activeList.size})"
                            ResultBucket.DIE -> "DIE RECORDS (${activeList.size})"
                            ResultBucket.UNKNOWN -> "UNKNOWN RECORDS (${activeList.size})"
                        }
                        Text(
                            text = tabTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(
                                onClick = {
                                    val joined = activeList.joinToString("\n") { it.normalizedLine }
                                    onCopyAll(tabTitle, joined)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("copy_all_records_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ContentCopy,
                                    contentDescription = stringResource(R.string.copy_all),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.copy_all),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            OutlinedButton(
                                onClick = onClearTab,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                shape = MaterialTheme.shapes.small,
                                border = BorderStroke(1.dp, colorScheme.outlineVariant),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = colorScheme.error
                                ),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("clear_tab_records_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = stringResource(R.string.clear_tab),
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = stringResource(R.string.clear_tab),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            items = activeList,
                            key = { it.id }
                        ) { record ->
                            ValidationRecordRow(
                                record = record,
                                onCopy = { onCopyRecord(record) },
                                onRemove = { onRemoveRecord(record) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultTabItem(
    label: String,
    count: Int,
    selected: Boolean,
    labelColor: Color,
    badgeBackgroundColor: Color,
    badgeTextColor: Color,
    topAccentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraSmall,
        color = if (selected) colorScheme.surfaceContainerHighest else colorScheme.surfaceContainer,
        shadowElevation = if (selected) 3.dp else 0.dp,
        border = if (selected) BorderStroke(1.dp, colorScheme.outlineVariant) else null,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .then(
                if (selected) {
                    Modifier.drawWithContent {
                        drawContent()
                        drawRect(
                            color = topAccentColor,
                            topLeft = Offset(0f, 0f),
                            size = Size(size.width, 2.5.dp.toPx())
                        )
                    }
                } else {
                    Modifier
                }
            )
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = labelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(badgeBackgroundColor)
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
                            fontWeight = FontWeight.Bold,
                            color = badgeTextColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ValidationRecordRow(
    record: ValidationRecord,
    onCopy: () -> Unit,
    onRemove: () -> Unit
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
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("record_row_${record.pan}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accentColor)
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = record.normalizedLine,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(containerAccent)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = record.scheme.displayName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
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
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy line",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    IconButton(onClick = onRemove) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove line",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
