package com.example.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.HistoryFilter
import com.example.data.local.ValidationHistoryEntity
import com.example.ui.theme.ActionButtonBorder
import com.example.ui.theme.ActionButtonSurface
import com.example.ui.theme.BadgeNeutralBg
import com.example.ui.theme.CardContainerSurface
import com.example.ui.theme.DieCrimson
import com.example.ui.theme.DieCrimsonBadgeBg
import com.example.ui.theme.GearRoyalBlue
import com.example.ui.theme.InputConsoleSurface
import com.example.ui.theme.InterFontFamily
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.PlayEmeraldBadgeBg
import com.example.ui.theme.TextPrimaryLight
import com.example.ui.theme.TextSecondaryMuted
import com.example.ui.theme.UnknownAmber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HistoryLogBottomSheet(
    entries: List<ValidationHistoryEntity>,
    summary: HistoryUiSummary,
    activeFilter: HistoryFilter,
    searchQuery: String,
    onSelectFilter: (HistoryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCopyEntry: (ValidationHistoryEntity) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = CardContainerSurface,
        contentColor = TextPrimaryLight,
        scrimColor = Color.Black.copy(alpha = 0.72f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GearRoyalBlue.copy(alpha = 0.16f))
                            .border(1.dp, GearRoyalBlue.copy(alpha = 0.4f), RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = GearRoyalBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.history_log_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimaryLight,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.history_log_subtitle),
                            fontSize = 11.5.sp,
                            color = TextSecondaryMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_history_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close history log",
                        tint = TextSecondaryMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Filter Chips (ALL / SUCCESS / FAILURE) + Clear All
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HistoryFilterChip(
                        label = stringResource(R.string.filter_all),
                        count = summary.totalCount,
                        selected = activeFilter == HistoryFilter.ALL,
                        accentColor = GearRoyalBlue,
                        badgeBg = BadgeNeutralBg,
                        onClick = { onSelectFilter(HistoryFilter.ALL) },
                        testTag = "history_filter_all"
                    )

                    HistoryFilterChip(
                        label = stringResource(R.string.filter_success),
                        count = summary.successCount,
                        selected = activeFilter == HistoryFilter.SUCCESS,
                        accentColor = PlayEmerald,
                        badgeBg = PlayEmeraldBadgeBg,
                        onClick = { onSelectFilter(HistoryFilter.SUCCESS) },
                        testTag = "history_filter_success"
                    )

                    HistoryFilterChip(
                        label = stringResource(R.string.filter_failure),
                        count = summary.failureCount,
                        selected = activeFilter == HistoryFilter.FAILURE,
                        accentColor = DieCrimson,
                        badgeBg = DieCrimsonBadgeBg,
                        onClick = { onSelectFilter(HistoryFilter.FAILURE) },
                        testTag = "history_filter_failure"
                    )
                }

                if (summary.totalCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(InputConsoleSurface)
                            .border(1.dp, DieCrimson.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                            .clickable(onClick = onClearAllHistory)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("clear_all_history_button")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = stringResource(R.string.clear_history),
                                tint = DieCrimson,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = stringResource(R.string.clear_history),
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = DieCrimson
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                placeholder = {
                    Text(
                        text = "Search by record, scheme, source file, or status...",
                        fontSize = 12.sp,
                        color = TextSecondaryMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        tint = TextSecondaryMuted,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = TextSecondaryMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                textStyle = TextStyle(
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 12.5.sp,
                    color = TextPrimaryLight
                ),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GearRoyalBlue,
                    unfocusedBorderColor = ActionButtonBorder,
                    focusedContainerColor = InputConsoleSurface,
                    unfocusedContainerColor = InputConsoleSurface,
                    cursorColor = GearRoyalBlue
                )
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = ActionButtonBorder)
            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(InputConsoleSurface)
                        .border(1.dp, ActionButtonBorder, RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Outlined.History,
                            contentDescription = null,
                            tint = TextSecondaryMuted.copy(alpha = 0.6f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                searchQuery.isNotBlank() -> "No history entries match \"$searchQuery\""
                                activeFilter == HistoryFilter.SUCCESS -> "No successful validation records saved yet"
                                activeFilter == HistoryFilter.FAILURE -> "No failed validation records saved yet"
                                else -> "No validation history logged yet — run a batch to record results"
                            },
                            fontSize = 12.sp,
                            color = TextSecondaryMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = entries,
                        key = { it.id }
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            formattedTime = dateFormatter.format(Date(entry.checkedAtMillis)),
                            onCopy = { onCopyEntry(entry) },
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryFilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    accentColor: Color,
    badgeBg: Color,
    onClick: () -> Unit,
    testTag: String
) {
    val bgColor = if (selected) accentColor.copy(alpha = 0.16f) else ActionButtonSurface
    val borderColor = if (selected) accentColor else ActionButtonBorder

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) accentColor else TextPrimaryLight
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeBg)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = count.toString(),
                    fontFamily = JetBrainsMonoFamily,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) accentColor else TextSecondaryMuted
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntryRow(
    entry: ValidationHistoryEntity,
    formattedTime: String,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val accentColor = when {
        entry.isSuccess -> PlayEmerald
        entry.status == "DIE" -> DieCrimson
        else -> UnknownAmber
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(6.dp))
            .background(InputConsoleSurface)
            .border(1.dp, ActionButtonBorder, RoundedCornerShape(6.dp))
            .clickable(onClick = onCopy)
            .testTag("history_entry_${entry.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.5.dp)
                .fillMaxHeight()
                .background(accentColor)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
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
                        imageVector = if (entry.isSuccess) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                        contentDescription = if (entry.isSuccess) "Success" else "Failure",
                        tint = accentColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = entry.normalizedLine,
                        fontFamily = JetBrainsMonoFamily,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryLight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            contentDescription = "Copy history record",
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onDelete)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Delete history entry",
                            tint = TextSecondaryMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                fontFamily = InterFontFamily,
                fontSize = 11.5.sp,
                color = TextSecondaryMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                MetadataTag(
                    text = if (entry.isSuccess) "SUCCESS • ${entry.status}" else "FAILURE • ${entry.status}",
                    color = accentColor
                )
                MetadataTag(text = entry.scheme, color = TextPrimaryLight)
                MetadataTag(text = entry.sourceName, color = GearRoyalBlue)
                MetadataTag(text = "${entry.latencyMs}ms", color = TextSecondaryMuted)
                MetadataTag(text = formattedTime, color = TextSecondaryMuted)
            }
        }
    }
}

@Composable
private fun MetadataTag(
    text: String,
    color: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            fontFamily = JetBrainsMonoFamily,
            fontSize = 10.sp,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
