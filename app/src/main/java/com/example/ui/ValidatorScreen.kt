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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.ui.theme.ConsoleBackground
import com.example.ui.theme.ConsoleBackgroundBottom
import com.example.ui.theme.GearRoyalBlue
import com.example.ui.theme.PlayEmerald
import com.example.ui.theme.TopDividerColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ValidatorScreen(
    viewModel: ValidatorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copiedFeedback by remember { mutableStateOf<String?>(null) }

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
        copiedFeedback = "Copied to clipboard"
        scope.launch {
            delay(1800)
            copiedFeedback = null
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(ConsoleBackground, ConsoleBackgroundBottom)
                )
            )
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PlayEmerald.copy(alpha = 0.055f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.25f, size.height * 0.18f),
                        radius = size.minDimension * 0.65f
                    ),
                    center = Offset(size.width * 0.25f, size.height * 0.18f),
                    radius = size.minDimension * 0.65f
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GearRoyalBlue.copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.78f, size.height * 0.55f),
                        radius = size.minDimension * 0.65f
                    ),
                    center = Offset(size.width * 0.78f, size.height * 0.55f),
                    radius = size.minDimension * 0.65f
                )
            }
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            HorizontalDivider(
                modifier = Modifier.fillMaxWidth(),
                thickness = 1.dp,
                color = TopDividerColor
            )

            // Responsive layout driven by actual available container dimensions
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val isTwoPaneLayout = maxWidth >= 680.dp && maxHeight >= 360.dp
                val horizontalPadding = when {
                    maxWidth < 380.dp -> 14.dp
                    maxWidth < 600.dp -> 20.dp
                    else -> 28.dp
                }
                val innerCardPadding = if (maxWidth < 380.dp) 16.dp else 22.dp

                val (binDigits, scheme) = detectedPreviewInfo

                if (isTwoPaneLayout) {
                    // Canonical Two-Pane Supporting-Pane / Side-by-Side Layout for Tablets, Foldables & Landscape
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
                                    copiedFeedback = copiedFeedback,
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
                    // Adaptive Single-Column Layout for Compact & Medium Portrait Screens
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
                                copiedFeedback = copiedFeedback,
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

                            Spacer(modifier = Modifier.height(22.dp))

                            ResultsCard(
                                uiState = uiState,
                                fillRemainingHeight = false,
                                onSelectTab = viewModel::selectTab,
                                onCopyAll = { title, joined -> copyToClipboard(title, joined) },
                                onClearTab = viewModel::clearActiveTabRecords,
                                onCopyRecord = { record -> copyToClipboard("Record", record.normalizedLine) },
                                onRemoveRecord = { record -> viewModel.removeRecord(record.id, record.bucket) }
                            )

                            Spacer(modifier = Modifier.height(18.dp))

                            DeveloperChannelFooter(
                                channelUrl = channelUrl,
                                onOpenChannel = ::openDeveloperChannel,
                                onCopyChannelUrl = { copyToClipboard("Developer Channel", channelUrl) }
                            )

                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }
        }
    }
}
