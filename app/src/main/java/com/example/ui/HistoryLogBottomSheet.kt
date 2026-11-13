package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.HistoryFilter
import com.example.data.local.ValidationHistoryEntity
import com.example.ui.theme.LocalConsoleColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun HistoryLogBottomSheet(
    entries: List<ValidationHistoryEntity>,
    summary: HistoryUiSummary,
    activeFilter: HistoryFilter,
    searchQuery: String,
    filteredTotalCount: Int = entries.size,
    hasMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSelectFilter: (HistoryFilter) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onCopyEntry: (ValidationHistoryEntity) -> Unit,
    onDeleteEntry: (Long) -> Unit,
    onClearAllHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val listState = rememberLazyListState()
    val dateFormatter = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    val shouldAutoLoadMore by remember(hasMore, entries.size) {
        derivedStateOf {
            if (!hasMore || entries.isEmpty()) return@derivedStateOf false
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= entries.size - 4
        }
    }

    LaunchedEffect(shouldAutoLoadMore) {
        if (shouldAutoLoadMore) {
            onLoadMore()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface
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
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.secondaryContainer,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppIcons.History,
                                contentDescription = null,
                                tint = colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.history_log_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.history_log_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
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
                        tint = colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // M3 FilterChip Bar (All / Success / Failure) + Clear All
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    HistoryM3FilterChip(
                        label = stringResource(R.string.filter_all),
                        count = summary.totalCount,
                        selected = activeFilter == HistoryFilter.ALL,
                        onClick = { onSelectFilter(HistoryFilter.ALL) },
                        testTag = "history_filter_all"
                    )

                    HistoryM3FilterChip(
                        label = stringResource(R.string.filter_success),
                        count = summary.successCount,
                        selected = activeFilter == HistoryFilter.SUCCESS,
                        onClick = { onSelectFilter(HistoryFilter.SUCCESS) },
                        testTag = "history_filter_success"
                    )

                    HistoryM3FilterChip(
                        label = stringResource(R.string.filter_failure),
                        count = summary.failureCount,
                        selected = activeFilter == HistoryFilter.FAILURE,
                        onClick = { onSelectFilter(HistoryFilter.FAILURE) },
                        testTag = "history_filter_failure"
                    )
                }

                if (summary.totalCount > 0) {
                    OutlinedButton(
                        onClick = onClearAllHistory,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, colorScheme.error.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = colorScheme.error),
                        modifier = Modifier
                            .height(34.dp)
                            .align(Alignment.CenterVertically)
                            .testTag("clear_all_history_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.DeleteSweep,
                            contentDescription = stringResource(R.string.clear_history),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.clear_history),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("history_search_input"),
                placeholder = {
                    Text(
                        text = "Search by record, scheme, source file, or status...",
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                textStyle = MaterialTheme.typography.labelMedium,
                singleLine = true,
                shape = MaterialTheme.shapes.small
            )

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            if (entries.isEmpty()) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(1.dp, colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 188.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer.copy(alpha = 0.72f),
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = AppIcons.History,
                                        contentDescription = null,
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "No matches found"
                                    activeFilter == HistoryFilter.SUCCESS -> "No successful runs yet"
                                    activeFilter == HistoryFilter.FAILURE -> "No failed runs yet"
                                    else -> "History log is empty"
                                },
                                style = MaterialTheme.typography.titleSmallEmphasized,
                                color = colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = when {
                                    searchQuery.isNotBlank() -> "No history entries match \"$searchQuery\""
                                    activeFilter == HistoryFilter.SUCCESS -> "No successful validation records saved yet"
                                    activeFilter == HistoryFilter.FAILURE -> "No failed validation records saved yet"
                                    else -> "Run any batch in Luhn, Multi-API, or Webhook mode to audit results here."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 420.dp)
                        .testTag("history_lazy_list"),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = entries,
                        key = { it.id },
                        contentType = { it.status }
                    ) { entry ->
                        HistoryEntryRow(
                            entry = entry,
                            formattedTime = dateFormatter.format(Date(entry.checkedAtMillis)),
                            accentColor = when {
                                entry.isSuccess -> consoleColors.liveAccent
                                entry.status == "DIE" -> consoleColors.dieAccent
                                else -> consoleColors.unknownAccent
                            },
                            onCopy = { onCopyEntry(entry) },
                            onDelete = { onDeleteEntry(entry.id) }
                        )
                    }

                    item(key = "history_pagination_footer") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 6.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Showing ${entries.size} of ${filteredTotalCount.coerceAtLeast(entries.size)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                modifier = Modifier.testTag("history_pagination_status")
                            )
                            if (hasMore) {
                                FilledTonalButton(
                                    onClick = onLoadMore,
                                    shape = MaterialTheme.shapes.small,
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(36.dp)
                                        .testTag("history_load_more_button")
                                ) {
                                    Text(
                                        text = stringResource(R.string.history_load_more),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryM3FilterChip(
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = "$label ($count)",
                style = MaterialTheme.typography.labelMedium
            )
        },
        leadingIcon = if (selected) {
            {
                Icon(
                    imageVector = Icons.Filled.Done,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else {
            null
        },
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.testTag(testTag)
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryEntryRow(
    entry: ValidationHistoryEntity,
    formattedTime: String,
    accentColor: Color,
    onCopy: () -> Unit,
    onDelete: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        onClick = onCopy,
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("history_entry_${entry.id}")
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

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 10.dp)
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
                            imageVector = if (entry.isSuccess) {
                                AppIcons.CheckCircle
                            } else {
                                AppIcons.ErrorOutline
                            },
                            contentDescription = if (entry.isSuccess) "Success" else "Failure",
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = entry.normalizedLine,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCopy) {
                            Icon(
                                imageVector = AppIcons.ContentCopy,
                                contentDescription = "Copy history record",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                imageVector = AppIcons.DeleteOutline,
                                contentDescription = "Delete history entry",
                                tint = colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = entry.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
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
                    MetadataTag(text = entry.scheme, color = colorScheme.onSurface)
                    MetadataTag(text = entry.sourceName, color = colorScheme.secondary)
                    MetadataTag(text = "${entry.latencyMs}ms", color = colorScheme.onSurfaceVariant)
                    MetadataTag(text = formattedTime, color = colorScheme.onSurfaceVariant)
                }
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
            .clip(MaterialTheme.shapes.extraSmall)
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
