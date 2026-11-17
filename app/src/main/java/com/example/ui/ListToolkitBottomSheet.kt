package com.example.ui

import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import com.example.analytics.FirebaseMetrics
import com.example.domain.CardScheme
import com.example.domain.DedupMode
import com.example.domain.FileTextExtractor
import com.example.domain.LargeFileWorkspace
import com.example.domain.LargeFileWorkspace.BulkEditSpec
import com.example.domain.LargeFileWorkspace.BulkEditType
import com.example.domain.LargeFileWorkspace.LargeFileSession
import com.example.domain.LargeFileWorkspace.SearchScanResult
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

private enum class ToolkitStudioTab(val label: String) {
    CLEAN("Clean"),
    FORMAT("Format"),
    FILTER("Filter"),
    EDITOR("Editor")
}

private enum class ExportTargetMode {
    OUTPUT,
    RANGE
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
    onFileStatusMessage: (String) -> Unit = {},
    onQueueLargeFileForBatch: ((stagedFile: File, sourceName: String, expectedLines: Int) -> Unit)? = null
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
    var largeSession by remember { mutableStateOf<LargeFileSession?>(null) }
    var sourceRevision by remember { mutableIntStateOf(0) }
    var activePageIndex by remember { mutableIntStateOf(0) }
    var viewingOutputPages by remember { mutableStateOf(false) }
    var currentPageLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var highlightedLineInPage by remember { mutableStateOf<Int?>(null) }

    var isEditingSource by remember { mutableStateOf(initialInputText.isBlank()) }
    var shuffleNonce by remember { mutableIntStateOf(0) }
    var loadedFileBadge by remember { mutableStateOf<String?>(null) }
    var inlineFeedbackMessage by remember { mutableStateOf<String?>(null) }
    var isFileBusy by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0f) }
    var progressLabel by remember { mutableStateOf<String?>(null) }

    // Editor tab find/replace state
    var findQuery by remember { mutableStateOf("") }
    var replaceWith by remember { mutableStateOf("") }
    var findUseRegex by remember { mutableStateOf(false) }
    var findIgnoreCase by remember { mutableStateOf(true) }
    var searchScanResult by remember { mutableStateOf<SearchScanResult?>(null) }

    var pendingExportMode by remember { mutableStateOf(ExportTargetMode.OUTPUT) }
    var pendingRangePair by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showLargeApplyDialog by remember { mutableStateOf(false) }

    val latestSessionState = rememberUpdatedState(largeSession)
    DisposableEffect(Unit) {
        onDispose {
            LargeFileWorkspace.cleanupSession(latestSessionState.value)
        }
    }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            LargeFileWorkspace.pruneOldWorkspaces(context.cacheDir)
        }
    }

    LaunchedEffect(activeTab) {
        FirebaseMetrics.logScreenView("ListToolkit_${activeTab.label}", "MainActivity")
    }

    var transformResult by remember {
        mutableStateOf(
            if (initialInputText.length <= 4_000) {
                ListToolkitEngine.transform(initialInputText, options)
            } else {
                ToolkitTransformResult(outputText = "")
            }
        )
    }

    suspend fun promoteRawTextToLargeWorkspace(label: String, text: String) {
        isFileBusy = true
        progressFraction = 0.1f
        progressLabel = "Staging $label into disk-stream workspace..."
        val prev = largeSession
        LargeFileWorkspace.ingestFileOrText(
            cacheDir = context.cacheDir,
            fileName = label,
            rawText = text,
            onProgress = { pct, msg ->
                scope.launch {
                    progressFraction = pct
                    progressLabel = msg
                }
            }
        ).fold(
            onSuccess = { session ->
                LargeFileWorkspace.cleanupSession(prev)
                largeSession = session
                sourceText = ""
                sourceRevision++
                activePageIndex = 0
                viewingOutputPages = false
                highlightedLineInPage = null
                isEditingSource = true
                loadedFileBadge = "${session.fileName} (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                val msg = "Disk-stream ready • ${session.totalLines} lines"
                inlineFeedbackMessage = msg
                onFileStatusMessage(msg)
            },
            onFailure = { err ->
                inlineFeedbackMessage = err.localizedMessage ?: "Failed to stage text workspace."
            }
        )
        isFileBusy = false
        progressLabel = null
    }

    val openFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isFileBusy = true
        progressFraction = 0.05f
        progressLabel = "Inspecting selected file..."
        inlineFeedbackMessage = null
        searchScanResult = null

        scope.launch {
            val reportedSize = withContext(Dispatchers.IO) {
                runCatching {
                    var sz = -1L
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst()) {
                            val idx = c.getColumnIndex(OpenableColumns.SIZE)
                            if (idx >= 0 && !c.isNull(idx)) sz = c.getLong(idx)
                        }
                    }
                    sz
                }.getOrDefault(-1L)
            }

            if (reportedSize in 0L..LargeFileWorkspace.INLINE_EDITOR_THRESHOLD_BYTES) {
                val result = FileTextExtractor.readAndParseUri(context, uri)
                result.fold(
                    onSuccess = { preview ->
                        if (!preview.isLargeStagedFile) {
                            LargeFileWorkspace.cleanupSession(largeSession)
                            largeSession = null
                            val textToLoad = if (preview.fileType == SupportedFileType.HTML) {
                                preview.allNonEmptyLines.joinToString("\n")
                            } else {
                                preview.rawContent
                            }
                            if (textToLoad.length > LargeFileWorkspace.INLINE_EDITOR_THRESHOLD_BYTES) {
                                promoteRawTextToLargeWorkspace(preview.fileName, textToLoad)
                            } else {
                                sourceText = textToLoad
                                loadedFileBadge = "${preview.fileName} (${FileTextExtractor.formatFileSize(preview.fileSizeBytes)})"
                                val msg = "Loaded ${preview.allNonEmptyLines.size} line(s) from ${preview.fileName}"
                                inlineFeedbackMessage = msg
                                onFileStatusMessage(msg)
                            }
                        } else {
                            val stagedPath = preview.stagedFilePath?.let(::File)
                            if (stagedPath != null && stagedPath.exists()) {
                                LargeFileWorkspace.ingestFileOrText(
                                    cacheDir = context.cacheDir,
                                    fileName = preview.fileName,
                                    existingFile = stagedPath,
                                    onProgress = { pct, msg ->
                                        scope.launch {
                                            progressFraction = pct
                                            progressLabel = msg
                                        }
                                    }
                                ).onSuccess { session ->
                                    stagedPath.delete()
                                    LargeFileWorkspace.cleanupSession(largeSession)
                                    largeSession = session
                                    sourceText = ""
                                    sourceRevision++
                                    activePageIndex = 0
                                    viewingOutputPages = false
                                    highlightedLineInPage = null
                                    isEditingSource = true
                                    loadedFileBadge = "${session.fileName} (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                                    val msg = "Disk-stream ready • ${session.totalLines} lines (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                                    inlineFeedbackMessage = msg
                                    onFileStatusMessage(msg)
                                }.onFailure { err ->
                                    stagedPath.delete()
                                    val msg = err.localizedMessage ?: "Failed to index file."
                                    inlineFeedbackMessage = msg
                                    onFileStatusMessage(msg)
                                }
                            }
                        }
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "Failed to read selected file."
                        inlineFeedbackMessage = msg
                        onFileStatusMessage(msg)
                    }
                )
            } else {
                // Direct streaming ingestion (0–260 MB) with zero double heap allocation
                LargeFileWorkspace.ingestUri(
                    context = context,
                    uri = uri,
                    onProgress = { pct, msg ->
                        scope.launch {
                            progressFraction = pct
                            progressLabel = msg
                        }
                    }
                ).fold(
                    onSuccess = { session ->
                        if (session.fileSizeBytes <= LargeFileWorkspace.INLINE_EDITOR_THRESHOLD_BYTES &&
                            session.totalLines <= 3_000
                        ) {
                            val headLines = LargeFileWorkspace.readHeadLines(session.sourceFile, 3_000)
                            LargeFileWorkspace.cleanupSession(largeSession)
                            LargeFileWorkspace.cleanupSession(session)
                            largeSession = null
                            sourceText = headLines.joinToString("\n")
                            loadedFileBadge = "${session.fileName} (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                            val msg = "Loaded ${session.totalLines} line(s) from ${session.fileName}"
                            inlineFeedbackMessage = msg
                            onFileStatusMessage(msg)
                        } else {
                            LargeFileWorkspace.cleanupSession(largeSession)
                            largeSession = session
                            sourceText = ""
                            sourceRevision++
                            activePageIndex = 0
                            viewingOutputPages = false
                            highlightedLineInPage = null
                            isEditingSource = true
                            loadedFileBadge = "${session.fileName} (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                            val msg = "Disk-stream ready • ${session.totalLines} lines (${FileTextExtractor.formatFileSize(session.fileSizeBytes)})"
                            inlineFeedbackMessage = msg
                            onFileStatusMessage(msg)
                        }
                    },
                    onFailure = { err ->
                        val msg = err.localizedMessage ?: "Failed to stream selected file."
                        inlineFeedbackMessage = msg
                        onFileStatusMessage(msg)
                    }
                )
            }

            isFileBusy = false
            progressLabel = null
        }
    }

    val saveFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val session = largeSession
        if (session != null) {
            isFileBusy = true
            progressFraction = 0.05f
            progressLabel = "Streaming export to file..."
            inlineFeedbackMessage = null
            val range = pendingRangePair
            val mode = pendingExportMode
            scope.launch {
                val useOutput = mode == ExportTargetMode.OUTPUT && session.outputFile != null
                val startIdx = if (mode == ExportTargetMode.RANGE && range != null) range.first else 0
                val endIdx = if (mode == ExportTargetMode.RANGE && range != null) range.second else Int.MAX_VALUE
                val result = LargeFileWorkspace.streamExportToUri(
                    context = context,
                    session = session,
                    useOutputFile = useOutput && mode != ExportTargetMode.RANGE,
                    uri = uri,
                    startLineInclusive = startIdx,
                    endLineExclusive = endIdx,
                    onProgress = { pct, msg ->
                        scope.launch {
                            progressFraction = pct
                            progressLabel = msg
                        }
                    }
                )
                isFileBusy = false
                progressLabel = null
                pendingExportMode = ExportTargetMode.OUTPUT
                pendingRangePair = null
                result.fold(
                    onSuccess = { lineCount ->
                        val msg = "Exported $lineCount line(s) via streaming write."
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
        } else {
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
    }

    // Run streaming or inline transform whenever source/options/revision changes
    LaunchedEffect(largeSession?.sessionId, sourceRevision, sourceText, options, shuffleNonce) {
        val randomSeed = if (options.sortOrder == ListSortOrder.SHUFFLE) {
            Random(System.nanoTime() + shuffleNonce)
        } else {
            Random.Default
        }
        val session = largeSession
        if (session != null) {
            isFileBusy = true
            progressFraction = 0.05f
            progressLabel = "Streaming transform (${session.activeTotalLines} lines)..."
            val (updatedSession, result) = LargeFileWorkspace.runStreamingTransform(
                session = session,
                options = options,
                random = randomSeed,
                onProgress = { pct, msg ->
                    progressFraction = pct
                    progressLabel = msg
                }
            )
            largeSession = updatedSession
            transformResult = result
            if (viewingOutputPages) {
                val safePg = activePageIndex.coerceIn(0, (updatedSession.totalOutputPages - 1).coerceAtLeast(0))
                activePageIndex = safePg
                currentPageLines = LargeFileWorkspace.readPage(
                    file = updatedSession.outputFile ?: updatedSession.sourceFile,
                    pageOffsets = if (updatedSession.outputFile != null) updatedSession.outputPageOffsets else updatedSession.pageOffsets,
                    totalLines = if (updatedSession.outputFile != null) updatedSession.outputTotalLines else updatedSession.totalLines,
                    pageIndex = safePg,
                    pageSize = updatedSession.pageSize
                )
            }
            isFileBusy = false
            progressLabel = null
        } else {
            if (isEditingSource && sourceText.length > 500) {
                delay(150L)
            }
            if (sourceText.length <= 800) {
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
    }

    // Reload active page window cleanly whenever page index, edits, or view target changes
    LaunchedEffect(
        largeSession?.sessionId,
        largeSession?.editedPages,
        largeSession?.outputFile,
        sourceRevision,
        activePageIndex,
        viewingOutputPages
    ) {
        val session = largeSession
        if (session == null) {
            currentPageLines = emptyList()
            return@LaunchedEffect
        }
        currentPageLines = if (viewingOutputPages && session.outputFile != null) {
            val safePg = activePageIndex.coerceIn(0, (session.totalOutputPages - 1).coerceAtLeast(0))
            LargeFileWorkspace.readPage(
                file = session.outputFile,
                pageOffsets = session.outputPageOffsets,
                totalLines = session.outputTotalLines,
                pageIndex = safePg,
                pageSize = session.pageSize
            )
        } else {
            val safePg = activePageIndex.coerceIn(0, (session.totalPages - 1).coerceAtLeast(0))
            LargeFileWorkspace.readPage(
                file = session.sourceFile,
                pageOffsets = session.pageOffsets,
                totalLines = session.totalLines,
                pageIndex = safePg,
                pageSize = session.pageSize,
                overlayEdits = session.editedPages
            )
        }
    }

    fun applyPageLinesChange(updatedPageLines: List<String>) {
        val session = largeSession ?: return
        val nextMap = session.editedPages.toMutableMap()
        nextMap[activePageIndex] = updatedPageLines
        largeSession = session.copy(editedPages = nextMap)
        sourceRevision++
    }

    fun commitPageEdits() {
        val session = largeSession ?: return
        if (!session.hasUnsavedPageEdits) return
        scope.launch {
            isFileBusy = true
            progressFraction = 0.1f
            progressLabel = "Committing page edits to disk..."
            LargeFileWorkspace.commitPageEditsToDisk(
                session = session,
                onProgress = { pct, msg ->
                    progressFraction = pct
                    progressLabel = msg
                }
            ).fold(
                onSuccess = { committed ->
                    largeSession = committed
                    sourceRevision++
                    activePageIndex = activePageIndex.coerceIn(0, (committed.totalPages - 1).coerceAtLeast(0))
                    val msg = "Committed page edits (${committed.totalLines} lines on disk)."
                    inlineFeedbackMessage = msg
                    onFileStatusMessage(msg)
                },
                onFailure = { err ->
                    inlineFeedbackMessage = err.localizedMessage ?: "Failed committing edits."
                }
            )
            isFileBusy = false
            progressLabel = null
        }
    }

    fun performSearchMatches() {
        if (findQuery.isEmpty()) return
        val session = largeSession
        if (session != null) {
            scope.launch {
                isFileBusy = true
                progressFraction = 0.05f
                progressLabel = "Scanning ${session.activeTotalLines} lines on disk..."
                val res = LargeFileWorkspace.searchAcrossFile(
                    session = session,
                    query = findQuery,
                    useRegex = findUseRegex,
                    ignoreCase = findIgnoreCase,
                    onProgress = { pct, msg ->
                        progressFraction = pct
                        progressLabel = msg
                    }
                )
                searchScanResult = res
                isFileBusy = false
                progressLabel = null
            }
        } else {
            scope.launch {
                val startNs = System.nanoTime()
                val res = withContext(Dispatchers.Default) {
                    val compiled = if (findUseRegex) {
                        val opts = if (findIgnoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                        runCatching { Regex(findQuery, opts) }.getOrNull()
                    } else null
                    val hits = ArrayList<LargeFileWorkspace.SearchHit>()
                    var totalMatches = 0
                    var idx = 0
                    if (!findUseRegex || compiled != null) {
                        sourceText.lineSequence().forEach { line ->
                            val matched = compiled?.containsMatchIn(line) ?: line.contains(findQuery, ignoreCase = findIgnoreCase)
                            if (matched) {
                                totalMatches++
                                if (hits.size < 300) {
                                    hits.add(
                                        LargeFileWorkspace.SearchHit(
                                            globalLineIndex = idx,
                                            pageIndex = idx / LargeFileWorkspace.PAGE_SIZE_LINES,
                                            lineInPage = idx % LargeFileWorkspace.PAGE_SIZE_LINES,
                                            preview = line.take(180)
                                        )
                                    )
                                }
                            }
                            idx++
                        }
                    }
                    SearchScanResult(
                        query = findQuery,
                        totalMatches = totalMatches,
                        hits = hits,
                        durationMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                    )
                }
                searchScanResult = res
            }
        }
    }

    fun performBulkOperation(spec: BulkEditSpec) {
        val session = largeSession
        if (session != null) {
            scope.launch {
                isFileBusy = true
                progressFraction = 0.05f
                progressLabel = "Applying ${spec.type.label} across disk workspace..."
                LargeFileWorkspace.applyBulkEdit(
                    session = session,
                    spec = spec,
                    onProgress = { pct, msg ->
                        progressFraction = pct
                        progressLabel = msg
                    }
                ).fold(
                    onSuccess = { outcome ->
                        largeSession = outcome.updatedSession
                        sourceRevision++
                        activePageIndex = 0
                        viewingOutputPages = false
                        highlightedLineInPage = null
                        searchScanResult = null
                        inlineFeedbackMessage = outcome.summaryMessage
                        onFileStatusMessage(outcome.summaryMessage)
                    },
                    onFailure = { err ->
                        inlineFeedbackMessage = err.localizedMessage ?: "Bulk edit failed."
                    }
                )
                isFileBusy = false
                progressLabel = null
            }
        } else {
            scope.launch {
                val startNs = System.nanoTime()
                val (updatedText, affected) = withContext(Dispatchers.Default) {
                    applyInlineBulkEdit(sourceText, spec)
                }
                sourceText = updatedText
                searchScanResult = null
                val elapsed = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
                inlineFeedbackMessage = "${spec.type.label}: updated ($affected change(s)) in ${elapsed}ms"
            }
        }
    }

    if (showLargeApplyDialog) {
        val session = largeSession
        val targetFile = session?.outputFile ?: session?.sourceFile
        val count = if (session?.outputFile != null) session.outputTotalLines else (session?.activeTotalLines ?: 0)
        AlertDialog(
            onDismissRequest = { showLargeApplyDialog = false },
            title = { Text("Large Result Set ($count lines)") },
            text = {
                Text(
                    "Loading all $count lines directly into the multi-line text box can freeze Android UI. " +
                        "You can either queue all $count lines straight for background batch validation, or load the first ${LargeFileWorkspace.MAX_SAFE_CONSOLE_LINES} lines into the console."
                )
            },
            confirmButton = {
                if (onQueueLargeFileForBatch != null && targetFile != null && targetFile.exists()) {
                    Button(
                        onClick = {
                            showLargeApplyDialog = false
                            val queueCopy = File(context.cacheDir, "queued_toolkit_${UUID.randomUUID().toString().take(8)}.txt")
                            val movedOrCopied = targetFile.renameTo(queueCopy)
                            if (!movedOrCopied) {
                                runCatching { targetFile.copyTo(queueCopy, overwrite = true) }
                            }
                            onQueueLargeFileForBatch(
                                queueCopy,
                                session?.fileName ?: "Toolkit Output",
                                count
                            )
                            onDismiss()
                        }
                    ) {
                        Text("Queue All $count for Check")
                    }
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(
                        onClick = {
                            showLargeApplyDialog = false
                            if (targetFile != null) {
                                scope.launch {
                                    val slice = LargeFileWorkspace.readHeadLines(
                                        file = targetFile,
                                        maxLines = LargeFileWorkspace.MAX_SAFE_CONSOLE_LINES
                                    )
                                    val cappedResult = transformResult.copy(outputText = slice.joinToString("\n"))
                                    onApplyToConsole(cappedResult.outputText, cappedResult)
                                }
                            }
                        }
                    ) {
                        Text("Load First ${LargeFileWorkspace.MAX_SAFE_CONSOLE_LINES}")
                    }
                    TextButton(onClick = { showLargeApplyDialog = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
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
            // Studio Header + 4-Tab Switcher
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
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            icon = {
                                SegmentedButtonDefaults.Icon(active = activeTab == tab) {
                                    Icon(
                                        imageVector = when (tab) {
                                            ToolkitStudioTab.CLEAN -> AppIcons.CleaningServices
                                            ToolkitStudioTab.FORMAT -> AppIcons.Tune
                                            ToolkitStudioTab.FILTER -> AppIcons.FilterList
                                            ToolkitStudioTab.EDITOR -> Icons.Default.Edit
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            },
                            modifier = Modifier.testTag("toolkit_tab_${tab.name.lowercase()}")
                        ) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelSmall,
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
                            .minimumInteractiveComponentSize()
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

                    val activeInputCount = largeSession?.activeTotalLines ?: transformResult.totalInputLines
                    FilterChip(
                        selected = isEditingSource,
                        onClick = { isEditingSource = !isEditingSource },
                        label = {
                            Text(
                                text = if (isEditingSource) "Hide Input" else "Edit Input ($activeInputCount)",
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.testTag("toolkit_toggle_edit_input")
                    )

                    TextButton(
                        onClick = {
                            val pasted = onPasteFromClipboard()
                            if (!pasted.isNullOrBlank()) {
                                if (pasted.length > LargeFileWorkspace.INLINE_EDITOR_THRESHOLD_BYTES) {
                                    scope.launch { promoteRawTextToLargeWorkspace("Clipboard Paste", pasted) }
                                } else {
                                    LargeFileWorkspace.cleanupSession(largeSession)
                                    largeSession = null
                                    sourceText = pasted
                                    loadedFileBadge = null
                                }
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
                                LargeFileWorkspace.cleanupSession(largeSession)
                                largeSession = null
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
                                LargeFileWorkspace.cleanupSession(largeSession)
                                largeSession = null
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

                // Streaming Progress Indicator Banner (only visible on heavy large-file I/O)
                AnimatedVisibility(visible = isFileBusy || !progressLabel.isNullOrBlank()) {
                    Surface(
                        color = colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toolkit_streaming_progress_banner")
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = progressLabel ?: "Processing streaming workspace...",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onPrimaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            LinearProgressIndicator(
                                progress = { progressFraction.coerceIn(0.02f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
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

                // Inline Text Editor OR O(1) Disk-Stream Paged Workspace
                val activeSession = largeSession
                if (activeSession != null && (isEditingSource || activeTab == ToolkitStudioTab.EDITOR)) {
                    LargeFilePagedViewerCard(
                        session = activeSession,
                        viewingOutput = viewingOutputPages,
                        onToggleViewOutput = { showOutput ->
                            viewingOutputPages = showOutput
                            activePageIndex = 0
                            highlightedLineInPage = null
                        },
                        currentPageIndex = activePageIndex,
                        pageLines = currentPageLines,
                        highlightedLineInPage = highlightedLineInPage,
                        isBusy = isFileBusy,
                        onSelectPage = { targetPg ->
                            activePageIndex = targetPg
                            highlightedLineInPage = null
                        },
                        onUpdateLineInPage = { lineInPg, newText ->
                            val mutable = currentPageLines.toMutableList()
                            if (lineInPg in mutable.indices) {
                                mutable[lineInPg] = newText
                                applyPageLinesChange(mutable)
                            }
                        },
                        onInsertLineAfter = { lineInPg, newText ->
                            val mutable = currentPageLines.toMutableList()
                            val insertIdx = (lineInPg + 1).coerceIn(0, mutable.size)
                            mutable.add(insertIdx, newText)
                            applyPageLinesChange(mutable)
                        },
                        onDeleteLineInPage = { lineInPg ->
                            val mutable = currentPageLines.toMutableList()
                            if (lineInPg in mutable.indices) {
                                mutable.removeAt(lineInPg)
                                applyPageLinesChange(mutable)
                            }
                        },
                        onReplaceWholePage = { updatedLines ->
                            applyPageLinesChange(updatedLines)
                        },
                        onCommitEditsToDisk = ::commitPageEdits
                    )
                } else {
                    AnimatedVisibility(visible = isEditingSource && activeSession == null) {
                        OutlinedTextField(
                            value = sourceText,
                            onValueChange = { next ->
                                if (next.length > LargeFileWorkspace.INLINE_EDITOR_THRESHOLD_BYTES) {
                                    scope.launch { promoteRawTextToLargeWorkspace("Large Input", next) }
                                } else {
                                    sourceText = next
                                }
                            },
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
                }

                // Active Tab Options for immediate visibility & focus
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)) togetherWith
                            fadeOut(animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium))
                    },
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

                        ToolkitStudioTab.EDITOR -> ToolkitEditorTabContent(
                            isLargeFileMode = activeSession != null,
                            totalLineCount = activeSession?.activeTotalLines ?: sourceText.lineSequence().count(),
                            isBusy = isFileBusy,
                            findQuery = findQuery,
                            replaceWith = replaceWith,
                            useRegex = findUseRegex,
                            ignoreCase = findIgnoreCase,
                            onFindQueryChange = { findQuery = it },
                            onReplaceWithChange = { replaceWith = it },
                            onUseRegexChange = { findUseRegex = it },
                            onIgnoreCaseChange = { findIgnoreCase = it },
                            searchResult = searchScanResult,
                            onSearchMatches = ::performSearchMatches,
                            onClearSearch = {
                                searchScanResult = null
                                highlightedLineInPage = null
                            },
                            onJumpToSearchHit = { hit ->
                                if (activeSession != null) {
                                    viewingOutputPages = false
                                    activePageIndex = hit.pageIndex.coerceIn(0, (activeSession.totalPages - 1).coerceAtLeast(0))
                                    highlightedLineInPage = hit.lineInPage
                                    isEditingSource = true
                                } else {
                                    inlineFeedbackMessage = "Match at Line ${hit.globalLineIndex + 1}: ${hit.preview}"
                                }
                            },
                            onApplyBulkOperation = ::performBulkOperation,
                            onExportLineRange = { startZero, endExcl ->
                                pendingExportMode = ExportTargetMode.RANGE
                                pendingRangePair = startZero to endExcl
                                val name = "bincheck_lines_${startZero + 1}_$endExcl.txt"
                                runCatching { saveFileLauncher.launch(name) }
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
                        .animateContentSize()
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
                        val previewText = remember(
                            transformResult.outputText,
                            transformResult.outputCount,
                            options.outputFormat
                        ) {
                            val lines = transformResult.outputText.lines().filter { it.isNotBlank() }
                            val totalOut = transformResult.outputCount.coerceAtLeast(lines.size)
                            when {
                                lines.isEmpty() -> when (options.outputFormat) {
                                    OutputFormat.EMAIL_PASS -> "No email:pass pairs found — select a file, paste a combo dump, or tweak filters."
                                    OutputFormat.URL_USER_PASS -> "No user:pass entries found — paste URL logs or tweak filters."
                                    OutputFormat.RAW_LINES -> "No non-empty lines — paste text or select a file."
                                    else -> "Nothing left after filtering — paste cards, select a file, or tweak the rules above."
                                }
                                totalOut <= 5 -> lines.joinToString("\n")
                                else -> lines.take(5).joinToString("\n") + "\n... (+${totalOut - 5} more)"
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

                val hasExportableContent = (largeSession != null && (largeSession?.activeTotalLines ?: 0) > 0) ||
                    transformResult.outputText.isNotBlank()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            LargeFileWorkspace.cleanupSession(largeSession)
                            largeSession = null
                            activePreset = ToolkitPreset.FULL_CLEAN
                            options = ListToolkitOptions.fromPreset(ToolkitPreset.FULL_CLEAN)
                            sourceText = initialInputText
                            loadedFileBadge = null
                            inlineFeedbackMessage = null
                            searchScanResult = null
                            activePageIndex = 0
                            viewingOutputPages = false
                            highlightedLineInPage = null
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
                        onClick = {
                            val session = largeSession
                            if (session != null) {
                                scope.launch {
                                    val target = session.outputFile ?: session.sourceFile
                                    val (slice, copiedLines, truncated) = LargeFileWorkspace.readSliceForClipboard(target)
                                    if (slice.isNotBlank()) {
                                        onCopyOutput(slice)
                                        if (truncated) {
                                            inlineFeedbackMessage = "Copied first $copiedLines lines (~350 KB cap). Use Save for full file."
                                        }
                                    }
                                }
                            } else {
                                val textOut = transformResult.outputText
                                if (textOut.length > LargeFileWorkspace.MAX_SAFE_CLIPBOARD_BYTES) {
                                    val safeCut = textOut.lastIndexOf('\n', startIndex = LargeFileWorkspace.MAX_SAFE_CLIPBOARD_BYTES)
                                        .takeIf { it > 0 } ?: LargeFileWorkspace.MAX_SAFE_CLIPBOARD_BYTES
                                    onCopyOutput(textOut.substring(0, safeCut))
                                    inlineFeedbackMessage = "Copied ~350 KB clipboard slice. Use Save for full output."
                                } else {
                                    onCopyOutput(textOut)
                                }
                            }
                        },
                        enabled = hasExportableContent && !isFileBusy,
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
                            pendingExportMode = ExportTargetMode.OUTPUT
                            pendingRangePair = null
                            val suggestedFileName = when (options.outputFormat) {
                                OutputFormat.EMAIL_PASS -> "bincheck_email_pass.txt"
                                OutputFormat.URL_USER_PASS -> "bincheck_user_pass.txt"
                                OutputFormat.CSV -> "bincheck_cleaned.csv"
                                OutputFormat.BIN_ONLY -> "bincheck_unique_bins.txt"
                                OutputFormat.PAN_ONLY -> "bincheck_pans.txt"
                                else -> "bincheck_cleaned.txt"
                            }
                            runCatching {
                                saveFileLauncher.launch(suggestedFileName)
                            }
                        },
                        enabled = hasExportableContent && !isFileBusy,
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
                        onClick = {
                            val session = largeSession
                            if (session != null) {
                                val count = if (session.outputFile != null) session.outputTotalLines else session.activeTotalLines
                                if (count <= LargeFileWorkspace.MAX_SAFE_CONSOLE_LINES) {
                                    scope.launch {
                                        val target = session.outputFile ?: session.sourceFile
                                        val allLines = LargeFileWorkspace.readHeadLines(target, LargeFileWorkspace.MAX_SAFE_CONSOLE_LINES)
                                        val joined = allLines.joinToString("\n")
                                        onApplyToConsole(joined, transformResult.copy(outputText = joined))
                                    }
                                } else {
                                    showLargeApplyDialog = true
                                }
                            } else {
                                onApplyToConsole(transformResult.outputText, transformResult)
                            }
                        },
                        enabled = (sourceText.isNotBlank() || hasExportableContent) && !isFileBusy,
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

private fun applyInlineBulkEdit(rawInput: String, spec: BulkEditSpec): Pair<String, Int> {
    val normalizedLines = rawInput.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    var affected = 0
    if (spec.type == BulkEditType.SORT_ASC) {
        val sorted = normalizedLines.sortedBy { it.lowercase(Locale.US) }
        return sorted.joinToString("\n") to sorted.size
    }

    val findRegex = if (spec.useRegex && spec.findQuery.isNotEmpty()) {
        val opts = if (spec.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
        runCatching { Regex(spec.findQuery, opts) }.getOrNull()
    } else null

    val parsedColumns = spec.columnIndices.split(',', ' ', ';', '|')
        .mapNotNull { it.trim().toIntOrNull()?.minus(1) }
        .filter { it >= 0 }

    val out = ArrayList<String>(normalizedLines.size)
    for (line in normalizedLines) {
        var keep = true
        var next = line
        when (spec.type) {
            BulkEditType.FIND_REPLACE -> {
                if (spec.findQuery.isNotEmpty()) {
                    next = if (findRegex != null) {
                        val rep = findRegex.replace(line, spec.replaceWith)
                        if (rep != line) affected++
                        rep
                    } else {
                        val rep = line.replace(spec.findQuery, spec.replaceWith, ignoreCase = spec.ignoreCase)
                        if (rep != line) affected++
                        rep
                    }
                }
            }
            BulkEditType.PREFIX_SUFFIX -> {
                if (line.isNotBlank()) {
                    next = "${spec.prefix}$line${spec.suffix}"
                    affected++
                }
            }
            BulkEditType.TRIM_DROP_EMPTY -> {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) {
                    keep = false
                    affected++
                } else {
                    if (trimmed != line) affected++
                    next = trimmed
                }
            }
            BulkEditType.CASE_LOWER -> {
                next = line.lowercase(Locale.US)
                if (next != line) affected++
            }
            BulkEditType.CASE_UPPER -> {
                next = line.uppercase(Locale.US)
                if (next != line) affected++
            }
            BulkEditType.NORMALIZE_DELIMITER -> {
                val delim = spec.targetDelimiter.ifEmpty { "|" }
                val joined = line.split('|', ':', ';', '/', '\t').joinToString(delim) { it.trim() }
                if (joined != line) affected++
                next = joined
            }
            BulkEditType.EXTRACT_COLUMNS -> {
                val parts = line.split('|', ':', ';', '/', ',', '\t').map { it.trim() }
                val sep = spec.targetDelimiter.ifEmpty { "|" }
                next = if (parsedColumns.isEmpty()) line else parsedColumns.mapNotNull { parts.getOrNull(it) }.joinToString(sep)
                if (next.isEmpty()) keep = false else affected++
            }
            BulkEditType.KEEP_MATCHING -> {
                val matches = if (findRegex != null) findRegex.containsMatchIn(line) else {
                    spec.findQuery.isEmpty() || line.contains(spec.findQuery, ignoreCase = spec.ignoreCase)
                }
                if (!matches) {
                    keep = false
                    affected++
                }
            }
            BulkEditType.REMOVE_MATCHING -> {
                val matches = if (findRegex != null) findRegex.containsMatchIn(line) else {
                    spec.findQuery.isNotEmpty() && line.contains(spec.findQuery, ignoreCase = spec.ignoreCase)
                }
                if (matches) {
                    keep = false
                    affected++
                }
            }
            BulkEditType.SORT_ASC -> Unit
        }
        if (keep) out.add(next)
    }
    return out.joinToString("\n") to affected
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CleanTabContent(
    activePreset: ToolkitPreset?,
    options: ListToolkitOptions,
    onSelectPreset: (ToolkitPreset) -> Unit,
    onOptionsChange: (ListToolkitOptions) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val isComboMode = options.outputFormat == OutputFormat.EMAIL_PASS || options.outputFormat == OutputFormat.URL_USER_PASS

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
                val modeText = when {
                    isComboMode -> when (mode) {
                        DedupMode.NONE -> "Keep All"
                        DedupMode.BY_PAN -> "Unique User"
                        DedupMode.BY_FULL_RECORD -> "Unique Combo"
                    }
                    options.outputFormat == OutputFormat.RAW_LINES -> when (mode) {
                        DedupMode.NONE -> "Keep All"
                        DedupMode.BY_PAN -> "First Token"
                        DedupMode.BY_FULL_RECORD -> "Unique Lines"
                    }
                    else -> mode.label
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
                            format == OutputFormat.EMAIL_PASS ||
                            format == OutputFormat.URL_USER_PASS ||
                            format == OutputFormat.RAW_LINES
                        ) {
                            false
                        } else {
                            options.dropIncomplete
                        }
                        val updatedRemoveUnparseable = if (format == OutputFormat.RAW_LINES) {
                            false
                        } else {
                            options.removeUnparseable
                        }
                        onOptionsChange(
                            options.copy(
                                outputFormat = format,
                                dropIncomplete = updatedDropIncomplete,
                                removeUnparseable = updatedRemoveUnparseable
                            )
                        )
                    },
                    label = {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = if (
                                format != OutputFormat.PAN_ONLY &&
                                format != OutputFormat.BIN_ONLY &&
                                format != OutputFormat.RAW_LINES
                            ) {
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

        val limits = listOf<Int?>(null, 50, 100, 250, 500, 5_000, 50_000)
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
    val isComboOrRawMode = options.outputFormat == OutputFormat.EMAIL_PASS ||
        options.outputFormat == OutputFormat.URL_USER_PASS ||
        options.outputFormat == OutputFormat.RAW_LINES
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
                    if (isComboOrRawMode) {
                        "Include Domains / Keywords (comma-separated)"
                    } else {
                        "Include BIN Prefixes (comma-separated)"
                    }
                )
            },
            placeholder = {
                Text(if (isComboOrRawMode) "e.g. gmail.com, outlook.com" else "e.g. 453225, 5154")
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
                Text(if (isComboOrRawMode) "Exclude Domains / Keywords" else "Exclude BIN Prefixes")
            },
            placeholder = {
                Text(if (isComboOrRawMode) "e.g. yahoo.com, temp" else "e.g. 4000, 5555")
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
