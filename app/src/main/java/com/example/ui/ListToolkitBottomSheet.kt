package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.CardScheme
import com.example.domain.DedupMode
import com.example.domain.FileTextExtractor
import com.example.domain.ListSortOrder
import com.example.domain.ListToolkitEngine
import com.example.domain.ListToolkitOptions
import com.example.domain.OutputFormat
import com.example.domain.SupportedFileType
import com.example.domain.ToolkitPreset
import com.example.domain.ToolkitTransformResult
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalConsoleColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

private enum class ToolkitStudioTab(val label: String) {
    CLEAN("Clean"),
    FORMAT("Format"),
    FILTER("Filter")
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
fun ListToolkitBottomSheet(
    initialInputText: String,
    liveResultsText: String,
    allResultsText: String,
    liveResultsCount: Int,
    allResultsCount: Int,
    onPasteFromClipboard: () -> String?,
    onCopyOutput: (String) -> Unit,
    onApplyToConsole: (String, ToolkitTransformResult) -> Unit,
    onDismiss: () -> Unit,
    onFileStatusMessage: (String) -> Unit = {}
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val colorScheme = MaterialTheme.colorScheme
    val consoleColors = LocalConsoleColors.current

    var activeTab by remember { mutableStateOf(ToolkitStudioTab.CLEAN) }
    var activePreset by remember { mutableStateOf<ToolkitPreset?>(ToolkitPreset.FULL_CLEAN) }
    var options by remember { mutableStateOf(ListToolkitOptions.fromPreset(ToolkitPreset.FULL_CLEAN)) }
    var sourceText by remember(initialInputText) { mutableStateOf(initialInputText) }
    var isEditingSource by remember { mutableStateOf(initialInputText.isBlank()) }
    var shuffleNonce by remember { mutableIntStateOf(0) }
    var loadedFileBadge by remember { mutableStateOf<String?>(null) }
    var inlineFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFileBusy by remember { mutableStateOf(false) }

    var transformResult by remember {
        mutableStateOf(
            if (initialInputText.length <= 4_000) {
                ListToolkitEngine.transform(initialInputText, options)
            } else {
                ToolkitTransformResult(outputText = "")
            }
        )
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isFileBusy = true
        inlineFeedbackMessage = null
        scope.launch {
            val result = FileTextExtractor.readAndParseUri(context, uri)
            isFileBusy = false
            result.fold(
                onSuccess = { preview ->
                    val textToLoad = if (preview.fileType == SupportedFileType.HTML) {
                        preview.allNonEmptyLines.joinToString("\n")
                    } else {
                        preview.rawContent
                    }
                    sourceText = textToLoad
                    loadedFileBadge = "${preview.fileName} (${FileTextExtractor.formatFileSize(preview.fileSizeBytes)})"
                    val msg = "Loaded ${preview.allNonEmptyLines.size} line(s) from ${preview.fileName}"
                    inlineFeedbackMessage = msg
                    onFileStatusMessage(msg)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Failed to read selected file."
                    inlineFeedbackMessage = msg
                    onFileStatusMessage(msg)
                }
            )
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val textToSave = transformResult.outputText
        if (textToSave.isBlank()) return@rememberLauncherForActivityResult
        isFileBusy = true
        inlineFeedbackMessage = null
        scope.launch {
            val result = FileTextExtractor.writeTextToUri(context, uri, textToSave)
            isFileBusy = false
            result.fold(
                onSuccess = { lineCount ->
                    val msg = "Exported $lineCount line(s) to file."
                    inlineFeedbackMessage = msg
                    onFileStatusMessage(msg)
                },
                onFailure = { err ->
                    val msg = err.localizedMessage ?: "Could not save file."
                    inlineFeedbackMessage = msg
                    onFileStatusMessage(msg)
                }
            )
        }
    }

    LaunchedEffect(sourceText, options, shuffleNonce) {
        val randomSeed = if (options.sortOrder == ListSortOrder.SHUFFLE) {
            Random(System.nanoTime() + shuffleNonce)
        } else {
            Random.Default
        }
        if (sourceText.length <= 4_000) {
            transformResult = ListToolkitEngine.transform(
                rawInput = sourceText,
                options = options,
                random = randomSeed
            )
        } else {
            transformResult = withContext(Dispatchers.Default) {
                ListToolkitEngine.transform(
                    rawInput = sourceText,
                    options = options,
                    random = randomSeed
                )
            }
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
                .navigationBarsPadding()
        ) {
            // Compact Studio Header + 3-Tab Switcher
            Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.toolkit_title),
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            color = colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.toolkit_subtitle),
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("close_list_toolkit_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close toolkit",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                val tabs = ToolkitStudioTab.entries
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    tabs.forEachIndexed { index, tab ->
                        SegmentedButton(
                            selected = activeTab == tab,
                            onClick = { activeTab = tab },
                            shape = SegmentedButtonDefaults.itemShape(index, tabs.size),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = activeTab == tab) {
                                    Icon(
                                        imageVector = when (tab) {
                                            ToolkitStudioTab.CLEAN -> AppIcons.CleaningServices
                                            ToolkitStudioTab.FORMAT -> AppIcons.Tune
                                            ToolkitStudioTab.FILTER -> AppIcons.FilterList
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("toolkit_tab_${tab.name.lowercase()}")
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (activeTab == tab) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Generous Scrollable Body
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Source Actions (Select File, Edit Input, Paste, Pull Live/All)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalButton(
                        onClick = {
                            runCatching {
                                openFileLauncher.launch(
                                    arrayOf("text/*", "application/json", "application/octet-stream", "*/*")
                                )
                            }
                        },
                        enabled = !isFileBusy,
                        shape = CircleShape,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("toolkit_select_file_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.FolderOpen,
                            contentDescription = stringResource(R.string.toolkit_select_file),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.toolkit_select_file),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    FilterChip(
                        selected = isEditingSource,
                        onClick = { isEditingSource = !isEditingSource },
                        label = {
                            Text(
                                text = if (isEditingSource) "Hide Input" else "Edit Input (${transformResult.totalInputLines})",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("toolkit_toggle_edit_input")
                    )

                    TextButton(
                        onClick = {
                            val pasted = onPasteFromClipboard()
                            if (!pasted.isNullOrBlank()) {
                                sourceText = pasted
                                loadedFileBadge = null
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("toolkit_paste_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Paste", style = MaterialTheme.typography.labelSmall)
                    }

                    if (liveResultsCount > 0) {
                        TextButton(
                            onClick = {
                                sourceText = liveResultsText
                                loadedFileBadge = "Live Bucket ($liveResultsCount)"
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("toolkit_pull_live_button")
                        ) {
                            Text("Pull Live ($liveResultsCount)", style = MaterialTheme.typography.labelSmall)
                        }
                    }

                    if (allResultsCount > liveResultsCount) {
                        TextButton(
                            onClick = {
                                sourceText = allResultsText
                                loadedFileBadge = "All Results ($allResultsCount)"
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("toolkit_pull_all_button")
                        ) {
                            Text("Pull All ($allResultsCount)", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (!loadedFileBadge.isNullOrBlank() || !inlineFeedbackMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        loadedFileBadge?.let { badge ->
                            Surface(
                                shape = CircleShape,
                                color = colorScheme.secondaryContainer.copy(alpha = 0.7f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = AppIcons.Description,
                                        contentDescription = null,
                                        tint = colorScheme.onSecondaryContainer,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSecondaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        inlineFeedbackMessage?.let { msg ->
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("toolkit_file_status_text")
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = isEditingSource) {
                    OutlinedTextField(
                        value = sourceText,
                        onValueChange = { sourceText = it },
                        label = { Text("Raw Input / Messy List") },
                        placeholder = { Text("Paste cards, email:pass dumps, HTML logs, CSV, or BINs...") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
                        minLines = 3,
                        maxLines = 5,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toolkit_source_input")
                    )
                }

                // Active Tab Options for immediate visibility & focus
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "toolkit_tab_transition"
                ) { tab ->
                    when (tab) {
                        ToolkitStudioTab.CLEAN -> CleanTabContent(
                            activePreset = activePreset,
                            options = options,
                            onSelectPreset = { preset ->
                                activePreset = preset
                                options = ListToolkitOptions.fromPreset(preset)
                            },
                            onOptionsChange = { next ->
                                activePreset = null
                                options = next
                            }
                        )

                        ToolkitStudioTab.FORMAT -> FormatTabContent(
                            options = options,
                            onOptionsChange = { next ->
                                activePreset = null
                                options = next
                            },
                            onReshuffle = { shuffleNonce++ }
                        )

                        ToolkitStudioTab.FILTER -> FilterTabContent(
                            options = options,
                            onOptionsChange = { next ->
                                activePreset = null
                                options = next
                            }
                        )
                    }
                }

                // Live Output Preview Box
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = colorScheme.surfaceContainerLowest,
                    border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("toolkit_preview_card")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Output Preview (${options.outputFormat.label})",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.onSurfaceVariant
                            )
                            if (transformResult.outputCount > 0) {
                                Text(
                                    text = "${transformResult.outputCount} ready",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    color = consoleColors.liveAccent
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        val previewText = remember(transformResult.outputText, options.outputFormat) {
                            val lines = transformResult.outputText.lines().filter { it.isNotBlank() }
                            when {
                                lines.isEmpty() -> if (options.outputFormat == OutputFormat.EMAIL_PASS) {
                                    "No email:pass pairs found — select a file, paste a combo dump, or tweak filters."
                                } else {
                                    "Nothing left after filtering — paste cards, select a file, or tweak the rules above."
                                }
                                lines.size <= 5 -> lines.joinToString("\n")
                                else -> lines.take(5).joinToString("\n") + "\n... (+${lines.size - 5} more)"
                            }
                        }

                        Text(
                            text = previewText,
                            fontFamily = JetBrainsMonoFamily,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (transformResult.outputText.isNotBlank()) {
                                colorScheme.onSurface
                            } else {
                                colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            },
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("toolkit_preview_output_text")
                        )
                    }
                }
            }

            HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.45f))

            // Compact Always-Visible Live Diff Strip + Sticky Action Footer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ToolkitStatPill(
                        text = "${transformResult.totalInputLines} in → ${transformResult.outputCount} out",
                        container = if (transformResult.outputCount > 0) consoleColors.liveContainer else colorScheme.surfaceContainerHigh,
                        content = if (transformResult.outputCount > 0) consoleColors.liveAccent else colorScheme.onSurfaceVariant,
                        testTag = "toolkit_stat_io"
                    )
                    if (transformResult.removedDuplicates > 0) {
                        ToolkitStatPill(
                            text = "-${transformResult.removedDuplicates} dup",
                            container = colorScheme.secondaryContainer,
                            content = colorScheme.onSecondaryContainer
                        )
                    }
                    if (transformResult.removedExpired > 0) {
                        ToolkitStatPill(
                            text = "-${transformResult.removedExpired} exp",
                            container = consoleColors.dieContainer,
                            content = consoleColors.dieAccent
                        )
                    }
                    if (transformResult.removedInvalidLuhn > 0 || transformResult.removedInvalidLengthOrCvv > 0) {
                        val badTotal = transformResult.removedInvalidLuhn + transformResult.removedInvalidLengthOrCvv
                        ToolkitStatPill(
                            text = "-$badTotal bad",
                            container = consoleColors.dieContainer,
                            content = consoleColors.dieAccent
                        )
                    }
                    if (transformResult.removedUnparseable + transformResult.removedIncomplete > 0) {
                        val junkTotal = transformResult.removedUnparseable + transformResult.removedIncomplete
                        ToolkitStatPill(
                            text = "-$junkTotal junk",
                            container = consoleColors.unknownContainer,
                            content = consoleColors.unknownAccent
                        )
                    }
                    if (transformResult.removedByFilter > 0) {
                        ToolkitStatPill(
                            text = "-${transformResult.removedByFilter} filt",
                            container = colorScheme.tertiaryContainer,
                            content = colorScheme.onTertiaryContainer
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            activePreset = ToolkitPreset.FULL_CLEAN
                            options = ListToolkitOptions.fromPreset(ToolkitPreset.FULL_CLEAN)
                            sourceText = initialInputText
                            loadedFileBadge = null
                            inlineFeedbackMessage = null
                        },
                        modifier = Modifier.testTag("toolkit_reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Reset toolkit options",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    OutlinedButton(
                        onClick = { onCopyOutput(transformResult.outputText) },
                        enabled = transformResult.outputText.isNotBlank(),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.85f)
                            .height(48.dp)
                            .testTag("toolkit_copy_output_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.ContentCopy,
                            contentDescription = stringResource(R.string.toolkit_copy_output),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Copy",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            val suggestedFileName = when (options.outputFormat) {
                                OutputFormat.EMAIL_PASS -> "bincheck_email_pass.txt"
                                OutputFormat.CSV -> "bincheck_cleaned.csv"
                                OutputFormat.BIN_ONLY -> "bincheck_unique_bins.txt"
                                OutputFormat.PAN_ONLY -> "bincheck_pans.txt"
                                else -> "bincheck_cleaned.txt"
                            }
                            runCatching {
                                saveFileLauncher.launch(suggestedFileName)
                            }
                        },
                        enabled = transformResult.outputText.isNotBlank() && !isFileBusy,
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(0.95f)
                            .height(48.dp)
                            .testTag("toolkit_export_save_button")
                    ) {
                        Icon(
                            imageVector = AppIcons.SaveAlt,
                            contentDescription = stringResource(R.string.toolkit_save_file),
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Save",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = { onApplyToConsole(transformResult.outputText, transformResult) },
                        enabled = sourceText.isNotBlank() || transformResult.outputText.isNotBlank(),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                        modifier = Modifier
                            .weight(1.2f)
                            .height(48.dp)
                            .testTag("toolkit_apply_console_button")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = stringResource(R.string.toolkit_apply_console),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = stringResource(R.string.toolkit_apply_console),
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CleanTabContent(
    activePreset: ToolkitPreset?,
    options: ListToolkitOptions,
    onSelectPreset: (ToolkitPreset) -> Unit,
    onOptionsChange: (ListToolkitOptions) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isEmailPassMode = options.outputFormat == OutputFormat.EMAIL_PASS

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Quick Recipes",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ToolkitPreset.entries.forEach { preset ->
                FilterChip(
                    selected = activePreset == preset,
                    onClick = { onSelectPreset(preset) },
                    leadingIcon = if (preset == ToolkitPreset.EXTRACT_EMAIL_PASS) {
                        {
                            Icon(
                                imageVector = AppIcons.MailOutline,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else {
                        null
                    },
                    label = {
                        Text(
                            text = preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                    },
                    modifier = Modifier.testTag("toolkit_preset_${preset.name.lowercase()}")
                )
            }
        }

        Text(
            text = "Duplicate Removal",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        val dedupModes = DedupMode.entries
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            dedupModes.forEachIndexed { index, mode ->
                val modeText = if (isEmailPassMode) {
                    when (mode) {
                        DedupMode.NONE -> "Keep All"
                        DedupMode.BY_PAN -> "Unique Email"
                        DedupMode.BY_FULL_RECORD -> "Unique Combo"
                    }
                } else {
                    mode.label
                }
                SegmentedButton(
                    selected = options.dedupMode == mode,
                    onClick = { onOptionsChange(options.copy(dedupMode = mode)) },
                    shape = SegmentedButtonDefaults.itemShape(index, dedupModes.size),
                    modifier = Modifier.testTag("toolkit_dedup_${mode.name.lowercase()}")
                ) {
                    Text(
                        text = modeText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1
                    )
                }
            }
        }

        ToolkitSwitchRow(
            title = "Remove Expired Cards",
            subtitle = "Prune past MM/YYYY dates or invalid months",
            checked = options.removeExpired,
            onCheckedChange = { onOptionsChange(options.copy(removeExpired = it)) },
            testTag = "toolkit_switch_expired"
        )

        ToolkitSwitchRow(
            title = "Remove Invalid Luhn",
            subtitle = "Drop PANs failing Mod-10 checksum",
            checked = options.removeInvalidLuhn,
            onCheckedChange = { onOptionsChange(options.copy(removeInvalidLuhn = it)) },
            testTag = "toolkit_switch_luhn"
        )

        ToolkitSwitchRow(
            title = "Strict Scheme Length & CVV",
            subtitle = "Enforce brand PAN length (15/16) & CVV digits (3/4)",
            checked = options.removeInvalidCvvOrLength,
            onCheckedChange = { onOptionsChange(options.copy(removeInvalidCvvOrLength = it)) },
            testTag = "toolkit_switch_strict_cvv"
        )

        ToolkitSwitchRow(
            title = "Drop Junk & Incomplete Lines",
            subtitle = "Strip HTML/log noise and lines missing expiry or CVV",
            checked = options.dropIncomplete && options.removeUnparseable,
            onCheckedChange = { enabled ->
                onOptionsChange(options.copy(dropIncomplete = enabled, removeUnparseable = enabled))
            },
            testTag = "toolkit_switch_drop_junk"
        )

        ToolkitSwitchRow(
            title = "Auto-Solve 'x' Wildcards",
            subtitle = "Resolve wildcard masks into Luhn-valid records",
            checked = options.solveWildcards,
            onCheckedChange = { onOptionsChange(options.copy(solveWildcards = it)) },
            testTag = "toolkit_switch_solve_wildcards"
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FormatTabContent(
    options: ListToolkitOptions,
    onOptionsChange: (ListToolkitOptions) -> Unit,
    onReshuffle: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Output Format",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutputFormat.entries.forEach { format ->
                val selected = options.outputFormat == format
                FilterChip(
                    selected = selected,
                    onClick = {
                        val updatedDropIncomplete = if (
                            format == OutputFormat.PAN_ONLY ||
                            format == OutputFormat.BIN_ONLY ||
                            format == OutputFormat.EMAIL_PASS
                        ) {
                            false
                        } else {
                            options.dropIncomplete
                        }
                        onOptionsChange(
                            options.copy(
                                outputFormat = format,
                                dropIncomplete = updatedDropIncomplete
                            )
                        )
                    },
                    label = {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = if (format != OutputFormat.PAN_ONLY && format != OutputFormat.BIN_ONLY) {
                                JetBrainsMonoFamily
                            } else {
                                MaterialTheme.typography.labelSmall.fontFamily
                            }
                        )
                    },
                    modifier = Modifier.testTag("toolkit_format_${format.name.lowercase()}")
                )
            }
        }

        Text(
            text = "Example: ${options.outputFormat.example}",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            color = colorScheme.primary
        )

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text(
            text = "Sort & Order",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ListSortOrder.entries.forEach { sort ->
                val selected = options.sortOrder == sort
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (sort == ListSortOrder.SHUFFLE && selected) {
                            onReshuffle()
                        } else {
                            onOptionsChange(options.copy(sortOrder = sort))
                        }
                    },
                    label = { Text(sort.label, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toolkit_sort_${sort.name.lowercase()}")
                )
            }
        }

        HorizontalDivider(color = colorScheme.outlineVariant.copy(alpha = 0.4f))

        Text(
            text = "Max Output Lines",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        val limits = listOf<Int?>(null, 50, 100, 250, 500)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            limits.forEach { limit ->
                FilterChip(
                    selected = options.limitCount == limit,
                    onClick = { onOptionsChange(options.copy(limitCount = limit)) },
                    label = { Text(limit?.toString() ?: "All", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toolkit_limit_${limit ?: "all"}")
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterTabContent(
    options: ListToolkitOptions,
    onOptionsChange: (ListToolkitOptions) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val currentYear = remember { Calendar.getInstance().get(Calendar.YEAR) }
    val isEmailPassMode = options.outputFormat == OutputFormat.EMAIL_PASS
    val selectableSchemes = remember {
        listOf(
            CardScheme.VISA,
            CardScheme.MASTERCARD,
            CardScheme.AMEX,
            CardScheme.DISCOVER,
            CardScheme.JCB,
            CardScheme.DINERS,
            CardScheme.UNIONPAY,
            CardScheme.MAESTRO
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Card Network / Brand",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = options.selectedSchemes.isEmpty(),
                onClick = { onOptionsChange(options.copy(selectedSchemes = emptySet())) },
                label = { Text("All", style = MaterialTheme.typography.labelSmall) },
                modifier = Modifier.testTag("toolkit_scheme_all")
            )
            selectableSchemes.forEach { scheme ->
                val selected = scheme in options.selectedSchemes
                FilterChip(
                    selected = selected,
                    onClick = {
                        val nextSet = if (selected) {
                            options.selectedSchemes - scheme
                        } else {
                            options.selectedSchemes + scheme
                        }
                        onOptionsChange(options.copy(selectedSchemes = nextSet))
                    },
                    label = { Text(scheme.displayName, style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toolkit_scheme_${scheme.name.lowercase()}")
                )
            }
        }

        OutlinedTextField(
            value = options.includeBinPrefixes,
            onValueChange = { onOptionsChange(options.copy(includeBinPrefixes = it)) },
            label = {
                Text(
                    if (isEmailPassMode) {
                        "Include Domains / Keywords (comma-separated)"
                    } else {
                        "Include BIN Prefixes (comma-separated)"
                    }
                )
            },
            placeholder = {
                Text(if (isEmailPassMode) "e.g. gmail.com, outlook.com" else "e.g. 453225, 5154")
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("toolkit_include_bins_input")
        )

        OutlinedTextField(
            value = options.excludeBinPrefixes,
            onValueChange = { onOptionsChange(options.copy(excludeBinPrefixes = it)) },
            label = {
                Text(if (isEmailPassMode) "Exclude Domains / Keywords" else "Exclude BIN Prefixes")
            },
            placeholder = {
                Text(if (isEmailPassMode) "e.g. yahoo.com, temp" else "e.g. 4000, 5555")
            },
            singleLine = true,
            shape = MaterialTheme.shapes.small,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFamily),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("toolkit_exclude_bins_input")
        )

        Text(
            text = "Minimum Expiry Year",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = colorScheme.onSurfaceVariant
        )

        val yearOptions = remember(currentYear) {
            listOf<Int?>(null, currentYear + 1, currentYear + 2, currentYear + 3, currentYear + 5)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            yearOptions.forEach { year ->
                FilterChip(
                    selected = options.minExpiryYear == year,
                    onClick = { onOptionsChange(options.copy(minExpiryYear = year)) },
                    label = { Text(year?.let { "$it+" } ?: "Any", style = MaterialTheme.typography.labelSmall) },
                    modifier = Modifier.testTag("toolkit_min_year_${year ?: "any"}")
                )
            }
        }
    }
}

@Composable
private fun ToolkitSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = { onCheckedChange(!checked) },
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLowest,
        border = BorderStroke(0.75.dp, colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag)
            )
        }
    }
}

@Composable
private fun ToolkitStatPill(
    text: String,
    container: Color,
    content: Color,
    testTag: String? = null
) {
    Surface(
        shape = CircleShape,
        color = container,
        modifier = if (testTag != null) Modifier.testTag(testTag) else Modifier
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = JetBrainsMonoFamily,
            fontWeight = FontWeight.SemiBold,
            color = content,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}
