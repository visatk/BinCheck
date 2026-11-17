package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.FileTextExtractor
import com.example.domain.LargeFileWorkspace
import com.example.domain.LargeFileWorkspace.BulkEditSpec
import com.example.domain.LargeFileWorkspace.BulkEditType
import com.example.domain.LargeFileWorkspace.LargeFileSession
import com.example.domain.LargeFileWorkspace.SearchHit
import com.example.domain.LargeFileWorkspace.SearchScanResult
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LargeFilePagedViewerCard(
    session: LargeFileSession,
    viewingOutput: Boolean,
    onToggleViewOutput: (Boolean) -> Unit,
    currentPageIndex: Int,
    pageLines: List<String>,
    highlightedLineInPage: Int?,
    isBusy: Boolean,
    onSelectPage: (Int) -> Unit,
    onUpdateLineInPage: (lineInPage: Int, newText: String) -> Unit,
    onInsertLineAfter: (lineInPage: Int, newText: String) -> Unit,
    onDeleteLineInPage: (lineInPage: Int) -> Unit,
    onReplaceWholePage: (List<String>) -> Unit,
    onCommitEditsToDisk: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    val totalLinesCount = if (viewingOutput) session.outputTotalLines else session.activeTotalLines
    val totalPagesCount = if (viewingOutput) session.totalOutputPages else session.totalPages
    val safePage = currentPageIndex.coerceIn(0, (totalPagesCount - 1).coerceAtLeast(0))
    val baseLineNumber = safePage * session.pageSize

    var jumpLineInput by rememberSaveable { mutableStateOf("") }
    var editingLinePair by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var editingWholePageText by remember { mutableStateOf<String?>(null) }

    editingLinePair?.let { (lineIdx, currentVal) ->
        var draft by remember(lineIdx, currentVal) { mutableStateOf(currentVal) }
        AlertDialog(
            onDismissRequest = { editingLinePair = null },
            title = {
                Text(
                    text = "Edit Line #${baseLineNumber + lineIdx + 1}",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Line content") },
                    minLines = 2,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toolkit_edit_line_dialog_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onUpdateLineInPage(lineIdx, draft)
                        editingLinePair = null
                    },
                    modifier = Modifier.testTag("toolkit_edit_line_dialog_save")
                ) {
                    Text("Save Line")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            onInsertLineAfter(lineIdx, draft)
                            editingLinePair = null
                        }
                    ) {
                        Text("Duplicate Below")
                    }
                    TextButton(
                        onClick = {
                            onDeleteLineInPage(lineIdx)
                            editingLinePair = null
                        }
                    ) {
                        Text("Delete", color = cs.error)
                    }
                    TextButton(onClick = { editingLinePair = null }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }

    editingWholePageText?.let { initialText ->
        var pageDraft by remember(initialText) { mutableStateOf(initialText) }
        AlertDialog(
            onDismissRequest = { editingWholePageText = null },
            title = {
                Text(
                    text = "Batch Edit Page ${safePage + 1} (${pageLines.size} lines)",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                OutlinedTextField(
                    value = pageDraft,
                    onValueChange = { pageDraft = it },
                    label = { Text("Lines on page ${safePage + 1}") },
                    minLines = 8,
                    maxLines = 14,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toolkit_batch_page_dialog_input")
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val updatedLines = pageDraft.replace("\r\n", "\n").replace('\r', '\n').split('\n')
                        onReplaceWholePage(updatedLines)
                        editingWholePageText = null
                    },
                    modifier = Modifier.testTag("toolkit_batch_page_dialog_save")
                ) {
                    Text("Apply Page")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWholePageText = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = cs.surfaceContainerLowest,
        border = BorderStroke(1.dp, cs.primary.copy(alpha = 0.35f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("toolkit_large_file_viewer")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Disk-Stream Paged Workspace",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                    val activeSize = if (viewingOutput) session.outputSizeBytes else session.fileSizeBytes
                    Text(
                        text = "${FileTextExtractor.formatFileSize(activeSize)} • $totalLinesCount lines • Page ${safePage + 1}/$totalPagesCount",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = JetBrainsMonoFamily,
                        color = cs.onSurfaceVariant
                    )
                }

                if (!viewingOutput && session.hasUnsavedPageEdits) {
                    FilledTonalButton(
                        onClick = onCommitEditsToDisk,
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_commit_page_edits_button")
                    ) {
                        Text(
                            text = "Commit ${session.editedPages.size} Pg",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            if (session.outputFile != null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !viewingOutput,
                        onClick = { onToggleViewOutput(false) },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("toolkit_view_source_pages")
                    ) {
                        Text("Editable Source (${session.totalLines})", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    SegmentedButton(
                        selected = viewingOutput,
                        onClick = { onToggleViewOutput(true) },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("toolkit_view_output_pages")
                    ) {
                        Text("Output (${session.outputTotalLines})", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }

            // Navigation & Jump Controls
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                itemVerticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = { onSelectPage(0) },
                    enabled = !isBusy && safePage > 0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text("|<", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onSelectPage((safePage - 1).coerceAtLeast(0)) },
                    enabled = !isBusy && safePage > 0,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toolkit_page_prev_button")
                ) {
                    Text("< Prev", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onSelectPage((safePage + 1).coerceAtMost(totalPagesCount - 1)) },
                    enabled = !isBusy && safePage < totalPagesCount - 1,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toolkit_page_next_button")
                ) {
                    Text("Next >", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = { onSelectPage((totalPagesCount - 1).coerceAtLeast(0)) },
                    enabled = !isBusy && safePage < totalPagesCount - 1,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Text(">|", style = MaterialTheme.typography.labelSmall)
                }

                if (!viewingOutput) {
                    FilledTonalButton(
                        onClick = { editingWholePageText = pageLines.joinToString("\n") },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_edit_whole_page_button")
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit Page", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onInsertLineAfter(pageLines.lastIndex, "") },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.minimumInteractiveComponentSize()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(2.dp))
                        Text("Line", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = jumpLineInput,
                    onValueChange = { jumpLineInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Go to Line # (1..${totalLinesCount.coerceAtLeast(1)})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("toolkit_jump_line_input")
                )
                FilledTonalButton(
                    onClick = {
                        val lineNum = jumpLineInput.toIntOrNull()?.coerceIn(1, totalLinesCount.coerceAtLeast(1)) ?: 1
                        val targetPage = ((lineNum - 1) / session.pageSize).coerceIn(0, totalPagesCount - 1)
                        onSelectPage(targetPage)
                    },
                    enabled = jumpLineInput.isNotBlank() && !isBusy,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("toolkit_jump_line_button")
                ) {
                    Text("Jump", style = MaterialTheme.typography.labelSmall)
                }
            }

            HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.4f))

            if (pageLines.isEmpty()) {
                Text(
                    text = "No lines on this page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 260.dp)
                        .testTag("toolkit_paged_lines_list"),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(pageLines, key = { idx, _ -> "${safePage}_$idx" }) { idx, text ->
                        val isHighlighted = highlightedLineInPage == idx
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = if (isHighlighted) {
                                consoleColors.liveContainer.copy(alpha = 0.7f)
                            } else {
                                cs.surfaceContainerLow
                            },
                            border = BorderStroke(
                                0.5.dp,
                                if (isHighlighted) consoleColors.liveAccent else cs.outlineVariant.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (!viewingOutput) {
                                        Modifier.clickable { editingLinePair = idx to text }
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag("toolkit_page_line_$idx")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${baseLineNumber + idx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = cs.onSurfaceVariant
                                )
                                Text(
                                    text = text.ifEmpty { "<empty line>" },
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    color = if (text.isEmpty()) cs.onSurfaceVariant.copy(alpha = 0.5f) else cs.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                if (!viewingOutput) {
                                    IconButton(
                                        onClick = { onDeleteLineInPage(idx) },
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete line",
                                            tint = cs.onSurfaceVariant,
                                            modifier = Modifier.size(14.dp)
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ToolkitEditorTabContent(
    isLargeFileMode: Boolean,
    totalLineCount: Int,
    isBusy: Boolean,
    findQuery: String,
    replaceWith: String,
    useRegex: Boolean,
    ignoreCase: Boolean,
    onFindQueryChange: (String) -> Unit,
    onReplaceWithChange: (String) -> Unit,
    onUseRegexChange: (Boolean) -> Unit,
    onIgnoreCaseChange: (Boolean) -> Unit,
    searchResult: SearchScanResult?,
    onSearchMatches: () -> Unit,
    onClearSearch: () -> Unit,
    onJumpToSearchHit: (SearchHit) -> Unit,
    onApplyBulkOperation: (BulkEditSpec) -> Unit,
    onExportLineRange: (startInclusive: Int, endExclusive: Int) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    var prefixInput by rememberSaveable { mutableStateOf("") }
    var suffixInput by rememberSaveable { mutableStateOf("") }
    var colIndicesInput by rememberSaveable { mutableStateOf("1,2") }
    var colDelimInput by rememberSaveable { mutableStateOf("|") }
    var rangeFromInput by rememberSaveable { mutableStateOf("1") }
    var rangeToInput by rememberSaveable { mutableStateOf("100000") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 1. Streaming Find, Regex Search & Replace Card
        Surface(
            color = cs.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(0.75.dp, cs.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (isLargeFileMode) {
                        "Find & Replace Across Full Disk File ($totalLineCount lines)"
                    } else {
                        "Find, Search & Replace"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )

                OutlinedTextField(
                    value = findQuery,
                    onValueChange = onFindQueryChange,
                    label = { Text("Find text or regex pattern") },
                    placeholder = { Text("e.g. @gmail.com or ^4532\\d+") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toolkit_editor_find_input")
                )

                OutlinedTextField(
                    value = replaceWith,
                    onValueChange = onReplaceWithChange,
                    label = { Text("Replace with (optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toolkit_editor_replace_input")
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = useRegex,
                        onClick = { onUseRegexChange(!useRegex) },
                        label = { Text("Regex", style = MaterialTheme.typography.labelSmall) },
                        modifier = Modifier.testTag("toolkit_editor_regex_chip")
                    )
                    FilterChip(
                        selected = ignoreCase,
                        onClick = { onIgnoreCaseChange(!ignoreCase) },
                        label = { Text("Ignore Case", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilledTonalButton(
                        onClick = onSearchMatches,
                        enabled = findQuery.isNotEmpty() && !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_editor_find_button")
                    ) {
                        Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Find Matches", style = MaterialTheme.typography.labelSmall)
                    }
                    Button(
                        onClick = {
                            onApplyBulkOperation(
                                BulkEditSpec(
                                    type = BulkEditType.FIND_REPLACE,
                                    findQuery = findQuery,
                                    replaceWith = replaceWith,
                                    useRegex = useRegex,
                                    ignoreCase = ignoreCase
                                )
                            )
                        },
                        enabled = findQuery.isNotEmpty() && !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_editor_replace_all_button")
                    ) {
                        Text("Replace All", style = MaterialTheme.typography.labelSmall)
                    }
                }

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            onApplyBulkOperation(
                                BulkEditSpec(
                                    type = BulkEditType.KEEP_MATCHING,
                                    findQuery = findQuery,
                                    useRegex = useRegex,
                                    ignoreCase = ignoreCase
                                )
                            )
                        },
                        enabled = findQuery.isNotEmpty() && !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_editor_keep_matching_button")
                    ) {
                        Text("Keep Matching Only", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            onApplyBulkOperation(
                                BulkEditSpec(
                                    type = BulkEditType.REMOVE_MATCHING,
                                    findQuery = findQuery,
                                    useRegex = useRegex,
                                    ignoreCase = ignoreCase
                                )
                            )
                        },
                        enabled = findQuery.isNotEmpty() && !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_editor_remove_matching_button")
                    ) {
                        Text("Remove Matching", style = MaterialTheme.typography.labelSmall)
                    }
                }

                AnimatedVisibility(visible = searchResult != null) {
                    searchResult?.let { sr ->
                        Surface(
                            color = cs.surfaceContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("toolkit_search_results_panel")
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${sr.totalMatches} match(es) in ${sr.durationMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = JetBrainsMonoFamily,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sr.totalMatches > 0) consoleColors.liveAccent else cs.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = onClearSearch,
                                        modifier = Modifier
                                            .minimumInteractiveComponentSize()
                                            .size(28.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear search", modifier = Modifier.size(14.dp))
                                    }
                                }
                                sr.hits.take(12).forEach { hit ->
                                    Surface(
                                        shape = CircleShape,
                                        color = cs.surfaceContainerHigh,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onJumpToSearchHit(hit) }
                                    ) {
                                        Text(
                                            text = "L${hit.globalLineIndex + 1} (Pg ${hit.pageIndex + 1}): ${hit.preview}",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontFamily = JetBrainsMonoFamily,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                        )
                                    }
                                }
                                if (sr.hits.size > 12) {
                                    Text(
                                        text = "Showing first 12 of ${sr.totalMatches} matches — tap any to jump.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = cs.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 2. Bulk Text & Column Operations (Works seamlessly on 100-200 MB!)
        Surface(
            color = cs.surfaceContainerLowest,
            shape = MaterialTheme.shapes.medium,
            border = BorderStroke(0.75.dp, cs.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Bulk Line, Case & Column Tools",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = { onApplyBulkOperation(BulkEditSpec(type = BulkEditType.TRIM_DROP_EMPTY)) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_trim_button")
                    ) {
                        Text("Trim & Drop Empty", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onApplyBulkOperation(BulkEditSpec(type = BulkEditType.CASE_LOWER)) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_lower_button")
                    ) {
                        Text("lowercase", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onApplyBulkOperation(BulkEditSpec(type = BulkEditType.CASE_UPPER)) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_upper_button")
                    ) {
                        Text("UPPERCASE", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = {
                            onApplyBulkOperation(BulkEditSpec(type = BulkEditType.NORMALIZE_DELIMITER, targetDelimiter = "|"))
                        },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_norm_pipe_button")
                    ) {
                        Text("Normalize → |", style = MaterialTheme.typography.labelSmall)
                    }
                    OutlinedButton(
                        onClick = { onApplyBulkOperation(BulkEditSpec(type = BulkEditType.SORT_ASC)) },
                        enabled = !isBusy,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_sort_button")
                    ) {
                        Text("Sort A → Z (Disk)", style = MaterialTheme.typography.labelSmall)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = prefixInput,
                        onValueChange = { prefixInput = it },
                        label = { Text("Prefix") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toolkit_bulk_prefix_input")
                    )
                    OutlinedTextField(
                        value = suffixInput,
                        onValueChange = { suffixInput = it },
                        label = { Text("Suffix") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("toolkit_bulk_suffix_input")
                    )
                }

                FilledTonalButton(
                    onClick = {
                        onApplyBulkOperation(
                            BulkEditSpec(
                                type = BulkEditType.PREFIX_SUFFIX,
                                prefix = prefixInput,
                                suffix = suffixInput
                            )
                        )
                    },
                    enabled = (prefixInput.isNotEmpty() || suffixInput.isNotEmpty()) && !isBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .minimumInteractiveComponentSize()
                        .testTag("toolkit_bulk_prefix_suffix_button")
                ) {
                    Text("Apply Prefix / Suffix to All Lines", style = MaterialTheme.typography.labelSmall)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = colIndicesInput,
                        onValueChange = { colIndicesInput = it },
                        label = { Text("Columns (1-based e.g. 1,2)") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(1.3f)
                    )
                    OutlinedTextField(
                        value = colDelimInput,
                        onValueChange = { colDelimInput = it.take(3) },
                        label = { Text("Join Sep") },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        modifier = Modifier.weight(0.7f)
                    )
                    FilledTonalButton(
                        onClick = {
                            onApplyBulkOperation(
                                BulkEditSpec(
                                    type = BulkEditType.EXTRACT_COLUMNS,
                                    columnIndices = colIndicesInput,
                                    targetDelimiter = colDelimInput.ifEmpty { "|" }
                                )
                            )
                        },
                        enabled = colIndicesInput.isNotBlank() && !isBusy,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("toolkit_bulk_extract_columns_button")
                    ) {
                        Text("Extract", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        // 3. Large-File Range Split / Direct Export
        if (isLargeFileMode) {
            Surface(
                color = cs.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                border = BorderStroke(0.75.dp, cs.outlineVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Split & Export Line Range (Streaming)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = rangeFromInput,
                            onValueChange = { rangeFromInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("From Line") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = rangeToInput,
                            onValueChange = { rangeToInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("To Line") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                            modifier = Modifier.weight(1f)
                        )
                        FilledTonalButton(
                            onClick = {
                                val from1 = (rangeFromInput.toIntOrNull() ?: 1).coerceAtLeast(1)
                                val to1 = (rangeToInput.toIntOrNull() ?: totalLineCount).coerceAtLeast(from1)
                                onExportLineRange(from1 - 1, to1)
                            },
                            enabled = !isBusy,
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Text("Export Part", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
