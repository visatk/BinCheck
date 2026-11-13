package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.CardValidatorEngine
import kotlinx.coroutines.launch

@Composable
fun ValidatorScreen(
    viewModel: ValidatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val colorScheme = MaterialTheme.colorScheme

    val channelUrl = stringResource(R.string.developer_channel_url)

    val detectedPreviewInfo by remember(uiState.rawInputText) {
        derivedStateOf {
            val sampleLine = uiState.rawInputText.lines().firstOrNull { it.isNotBlank() }
                ?: "453225005536xxx3|08|2028|113"
            val panToken = sampleLine.split('|', '/', ':').firstOrNull()?.trim().orEmpty()
            val binPrefix = panToken.filter { it.isDigit() }.take(6)
            val scheme = CardValidatorEngine.detectScheme(panToken)
            Pair(binPrefix.ifEmpty { "453225" }, scheme)
        }
    }

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar("Copied $label to clipboard")
        }
    }

    fun pasteFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val item = clipboard?.primaryClip?.getItemAt(0)
        return item?.coerceToText(context)?.toString()
    }

    fun openDeveloperChannel() {
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(channelUrl)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsBottomSheet(
            settings = uiState.settings,
            onDismiss = viewModel::closeSettings,
            onSaveSettings = viewModel::saveSettings,
            onGenerateBatchToConsole = viewModel::generateBatchIntoConsole,
            onClearAllBuckets = viewModel::clearAllResults
        )
    }

    if (uiState.isFileImportOpen) {
        FileImportBottomSheet(
            isImporting = uiState.isImportingFile,
            errorMessage = uiState.fileImportError,
            preview = uiState.importedFilePreview,
            hasExistingConsoleText = uiState.rawInputText.isNotBlank(),
            onDismiss = viewModel::closeFileImport,
            onFileSelected = viewModel::onFileUriSelected,
            onClearPreview = viewModel::clearFilePreview,
            onApplyImportedFile = viewModel::applyImportedFile
        )
    }

    if (uiState.isHistoryOpen) {
        HistoryLogBottomSheet(
            entries = uiState.historyEntries,
            summary = uiState.historySummary,
            activeFilter = uiState.historyFilter,
            searchQuery = uiState.historySearchQuery,
            onSelectFilter = viewModel::setHistoryFilter,
            onSearchQueryChanged = viewModel::setHistorySearchQuery,
            onCopyEntry = { entry -> copyToClipboard("History Record", entry.normalizedLine) },
            onDeleteEntry = viewModel::deleteHistoryEntry,
            onClearAllHistory = viewModel::clearAllHistory,
            onDismiss = viewModel::closeHistory
        )
    }

    val topBg = colorScheme.background
    val bottomBg = colorScheme.surfaceContainerLowest
    val primaryGlow = colorScheme.primary.copy(alpha = 0.08f)
    val secondaryGlow = colorScheme.tertiary.copy(alpha = 0.065f)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(colors = listOf(topBg, bottomBg))
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(primaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.25f, size.height * 0.18f),
                        radius = size.minDimension * 0.68f
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.18f),
                    radius = size.minDimension * 0.68f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(secondaryGlow, Color.Transparent),
                        center = Offset(size.width * 0.78f, size.height * 0.58f),
                        radius = size.minDimension * 0.68f
                    ),
                    center = Offset(size.width * 0.78f, size.height * 0.58f),
                    radius = size.minDimension * 0.68f
                )
            },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = colorScheme.inverseSurface,
                    contentColor = colorScheme.inverseOnSurface,
                    shape = MaterialTheme.shapes.small
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = colorScheme.outlineVariant.copy(alpha = 0.6f)
            )

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isTwoPaneLayout = maxWidth >= 680.dp && maxHeight >= 360.dp
                val horizontalPadding = when {
                    maxWidth < 380.dp -> 14.dp
                    maxWidth < 600.dp -> 20.dp
                    else -> 28.dp
                }
                val innerCardPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
                val (binDigits, scheme) = detectedPreviewInfo

                if (isTwoPaneLayout) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = 14.dp)
                    ) {
                        TopTelemetryStrip(
                            isRunning = uiState.isRunning,
                            validationMode = uiState.settings.validationMode,
                            concurrency = uiState.settings.concurrency,
                            binDigits = binDigits,
                            detectedScheme = scheme,
                            loadedFileName = uiState.loadedFileName,
                            historyCount = uiState.historySummary.totalCount,
                            onOpenHistory = viewModel::openHistory,
                            onOpenDeveloperChannel = ::openDeveloperChannel
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(0.48f)
                                    .fillMaxHeight(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ConsoleInputCard(
                                    uiState = uiState,
                                    consoleHeight = null,
                                    cardPadding = innerCardPadding,
                                    onInputChanged = viewModel::onInputTextChanged,
                                    onToggleRun = viewModel::toggleValidationRun,
                                    onOpenSettings = viewModel::openSettings,
                                    onOpenFileImport = viewModel::openFileImport,
                                    onQuickGen10x = {
                                        val patternToUse = uiState.rawInputText.lines()
                                            .firstOrNull { it.isNotBlank() }
                                            ?: uiState.settings.patternTemplate
                                        viewModel.generateBatchIntoConsole(patternToUse, 10)
                                    },
                                    onExpandWildcards = viewModel::expandCurrentConsoleWildcards,
                                    onPasteOrFill = {
                                        val pasted = pasteFromClipboard()
                                        if (!pasted.isNullOrBlank()) {
                                            viewModel.onInputTextChanged(pasted)
                                        } else {
                                            viewModel.expandCurrentConsoleWildcards()
                                        }
                                    },
                                    onClearInput = { viewModel.onInputTextChanged("") },
                                    onClearBanner = viewModel::clearStatusBanner,
                                    modifier = Modifier.weight(1f)
                                )

                                DeveloperChannelFooter(
                                    channelUrl = channelUrl,
                                    onOpenChannel = ::openDeveloperChannel,
                                    onCopyChannelUrl = { copyToClipboard("Developer Channel", channelUrl) }
                                )
                            }

                            ResultsCard(
                                uiState = uiState,
                                fillRemainingHeight = true,
                                onSelectTab = viewModel::selectTab,
                                onCopyAll = { title, joined -> copyToClipboard(title, joined) },
                                onClearTab = viewModel::clearActiveTabRecords,
                                onCopyRecord = { record -> copyToClipboard("Record", record.normalizedLine) },
                                onRemoveRecord = { record -> viewModel.removeRecord(record.id, record.bucket) },
                                modifier = Modifier
                                    .weight(0.52f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                } else {
                    val adaptiveConsoleHeight = (maxHeight * 0.30f).coerceIn(175.dp, 260.dp)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = 580.dp)
                                .padding(horizontal = horizontalPadding),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.height(12.dp))

                            TopTelemetryStrip(
                                isRunning = uiState.isRunning,
                                validationMode = uiState.settings.validationMode,
                                concurrency = uiState.settings.concurrency,
                                binDigits = binDigits,
                                detectedScheme = scheme,
                                loadedFileName = uiState.loadedFileName,
                                historyCount = uiState.historySummary.totalCount,
                                onOpenHistory = viewModel::openHistory,
                                onOpenDeveloperChannel = ::openDeveloperChannel
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            ConsoleInputCard(
                                uiState = uiState,
                                consoleHeight = adaptiveConsoleHeight,
                                cardPadding = innerCardPadding,
                                onInputChanged = viewModel::onInputTextChanged,
                                onToggleRun = viewModel::toggleValidationRun,
                                onOpenSettings = viewModel::openSettings,
                                onOpenFileImport = viewModel::openFileImport,
                                onQuickGen10x = {
                                    val patternToUse = uiState.rawInputText.lines()
                                        .firstOrNull { it.isNotBlank() }
                                        ?: uiState.settings.patternTemplate
                                    viewModel.generateBatchIntoConsole(patternToUse, 10)
                                },
                                onExpandWildcards = viewModel::expandCurrentConsoleWildcards,
                                onPasteOrFill = {
                                    val pasted = pasteFromClipboard()
                                    if (!pasted.isNullOrBlank()) {
                                        viewModel.onInputTextChanged(pasted)
                                    } else {
                                        viewModel.expandCurrentConsoleWildcards()
                                    }
                                },
                                onClearInput = { viewModel.onInputTextChanged("") },
                                onClearBanner = viewModel::clearStatusBanner
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            ResultsCard(
                                uiState = uiState,
                                fillRemainingHeight = false,
                                onSelectTab = viewModel::selectTab,
                                onCopyAll = { title, joined -> copyToClipboard(title, joined) },
                                onClearTab = viewModel::clearActiveTabRecords,
                                onCopyRecord = { record -> copyToClipboard("Record", record.normalizedLine) },
                                onRemoveRecord = { record -> viewModel.removeRecord(record.id, record.bucket) }
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            DeveloperChannelFooter(
                                channelUrl = channelUrl,
                                onOpenChannel = ::openDeveloperChannel,
                                onCopyChannelUrl = { copyToClipboard("Developer Channel", channelUrl) }
                            )

                            Spacer(modifier = Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}
