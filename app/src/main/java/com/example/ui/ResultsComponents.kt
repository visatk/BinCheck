package com.example.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ResultBucket
import com.example.domain.ValidationRecord
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.BadgeNeutralBg
import com.example.ui.theme.CardContainerBorder
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.CardSpecularHighlight
import com.example.ui.theme.DieCrimson
import com.example.ui.theme.DieCrimsonBadgeBg
import com.example.ui.theme.InputConsoleSurface
import com.example.ui.theme.InputPlaceholderColor
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.PlayEmeraldBadgeBg
import com.example.ui.theme.TabActiveSurface
import com.example.ui.theme.TabInactiveSurface
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.UnknownAmber
import com.example.ui.theme.UnknownAmberBadgeBg

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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(6.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        CardSpecularHighlight.copy(alpha = 0.65f),
                        CardContainerBorder.copy(alpha = 0.45f)
                    )
                ),
                shape = RoundedCornerShape(6.dp)
            )
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
        color = CardContainerSurface,
        shape = RoundedCornerShape(6.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // 3-Tab Header Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(TabInactiveSurface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ResultTabItem(
                    label = stringResource(R.string.tab_live),
                    count = uiState.liveRecords.size,
                    selected = uiState.selectedTab == ResultBucket.LIVE,
                    labelColor = PlayEmerald,
                    badgeBackgroundColor = PlayEmeraldBadgeBg,
                    badgeTextColor = PlayEmerald,
                    topAccentColor = PlayEmerald,
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
                    labelColor = TextPrimaryLight,
                    badgeBackgroundColor = if (uiState.dieRecords.isNotEmpty()) DieCrimsonBadgeBg else BadgeNeutralBg,
                    badgeTextColor = if (uiState.dieRecords.isNotEmpty()) DieCrimson else TextPrimaryLight,
                    topAccentColor = DieCrimson,
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
                    labelColor = TextPrimaryLight,
                    badgeBackgroundColor = if (uiState.unknownRecords.isNotEmpty()) UnknownAmberBadgeBg else BadgeNeutralBg,
                    badgeTextColor = if (uiState.unknownRecords.isNotEmpty()) UnknownAmber else TextPrimaryLight,
                    topAccentColor = UnknownAmber,
                    onClick = { onSelectTab(ResultBucket.UNKNOWN) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .testTag("tab_unknown")
                )
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = Color(0xFF13151A)
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
                        .height(48.dp)
                }
                Box(
                    modifier = emptyBoxModifier
                        .background(ActionButtonSurface)
                        .padding(horizontal = 16.dp),
                    contentAlignment = if (fillRemainingHeight) Alignment.Center else Alignment.CenterStart
                ) {
                    Text(
                        text = when (uiState.selectedTab) {
                            ResultBucket.LIVE -> "No Live records yet — press ▷ to validate batch"
                            ResultBucket.DIE -> "No declined / invalid records"
                            ResultBucket.UNKNOWN -> "No unknown format records"
                        },
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 11.sp,
                        color = InputPlaceholderColor.copy(alpha = 0.75f)
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
                        .background(ActionButtonSurface)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val tabTitle = when (uiState.selectedTab) {
                            ResultBucket.LIVE -> "LIVE RECORDS (${activeList.size})"
                            ResultBucket.DIE -> "DIE RECORDS (${activeList.size})"
                            ResultBucket.UNKNOWN -> "UNKNOWN RECORDS (${activeList.size})"
                        }
                        Text(
                            text = tabTitle,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextSecondaryMuted,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(InputConsoleSurface)
                                    .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
                                    .clickable {
                                        val joined = activeList.joinToString("\n") { it.normalizedLine }
                                        onCopyAll(tabTitle, joined)
                                    }
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("copy_all_records_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.ContentCopy,
                                        contentDescription = stringResource(R.string.copy_all),
                                        tint = PlayEmerald,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.copy_all),
                                        fontSize = 11.sp,
                                        color = TextPrimaryLight
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(InputConsoleSurface)
                                    .border(1.dp, ActionButtonBorder, RoundedCornerShape(4.dp))
                                    .clickable(onClick = onClearTab)
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                                    .testTag("clear_tab_records_button")
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Outlined.DeleteOutline,
                                        contentDescription = stringResource(R.string.clear_tab),
                                        tint = DieCrimson,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = stringResource(R.string.clear_tab),
                                        fontSize = 11.sp,
                                        color = TextSecondaryMuted
                                    )
                                }
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
    val containerModifier = if (selected) {
        modifier
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(3.dp),
                ambientColor = Color.Black,
                spotColor = Color.Black
            )
            .background(TabActiveSurface)
            .border(
                width = 1.dp,
                color = Color(0xFF2B303C),
                shape = RoundedCornerShape(3.dp)
            )
            .drawWithContent {
                drawContent()
                drawRect(
                    color = topAccentColor.copy(alpha = 0.85f),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, 2.dp.toPx())
                )
            }
    } else {
        modifier.background(TabInactiveSurface)
    }

    Box(
        modifier = containerModifier
            .minimumInteractiveComponentSize()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.5.sp,
                color = labelColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBackgroundColor)
                    .padding(horizontal = 6.5.dp, vertical = 2.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "badge_count_$label"
                ) { targetCount ->
                    Text(
                        text = targetCount.toString(),
                        fontFamily = InterFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.5.sp,
                        color = badgeTextColor
                    )
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
    val accentColor = when (record.bucket) {
        ResultBucket.LIVE -> PlayEmerald
        ResultBucket.DIE -> DieCrimson
        ResultBucket.UNKNOWN -> UnknownAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(5.dp))
            .background(InputConsoleSurface)
            .border(1.dp, ActionButtonBorder, RoundedCornerShape(5.dp))
            .clickable(onClick = onCopy)
            .testTag("record_row_${record.pan}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.normalizedLine,
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimaryLight,
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
                            .clip(RoundedCornerShape(3.dp))
                            .background(accentColor.copy(alpha = 0.16f))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = record.scheme.displayName,
                            fontFamily = JetBrainsMonoFamily,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }
                    Text(
                        text = "${record.message} (${record.latencyMs}ms)",
                        fontFamily = InterFontFamily,
                        fontSize = 11.sp,
                        color = TextSecondaryMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onCopy)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "Copy line",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(onClick = onRemove)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteOutline,
                        contentDescription = "Remove line",
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
