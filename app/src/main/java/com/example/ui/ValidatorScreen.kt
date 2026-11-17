package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.analytics.FirebaseMetrics
import com.example.domain.BinLookupService
import com.example.domain.CardValidatorEngine
import com.example.domain.SandboxProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ValidatorScreen(
    viewModel: ValidatorViewModel,
    requestStartupPermissions: Boolean = false,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    var activeCompactPane by rememberSaveable { mutableStateOf(CompactWorkspacePane.SPLIT) }
    val colorScheme = MaterialTheme.colorScheme
    var permissionSnapshot by remember(context) {
        mutableStateOf(AppPermissionHelper.inspectSnapshot(context))
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                permissionSnapshot = AppPermissionHelper.inspectSnapshot(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val manualPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        permissionSnapshot = AppPermissionHelper.inspectSnapshot(context)
        val granted = grants.values.all { it }
        FirebaseMetrics.logPermissionPromptResult(granted = granted, source = "settings")
        scope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                if (granted) {
                    "Batch alerts are turned on."
                } else {
                    "Alerts stay off — you can enable them anytime in Settings."
                }
            )
        }
    }

    StartupPermissionsPromptEffect(
        enabled = requestStartupPermissions && uiState.settingsLoaded,
        alreadyPrompted = uiState.settings.hasPromptedNotifications,
        onPromptAttempted = viewModel::markNotificationPermissionPrompted,
        onResult = { granted ->
            permissionSnapshot = AppPermissionHelper.inspectSnapshot(context)
            FirebaseMetrics.logPermissionPromptResult(granted = granted, source = "startup")
            if (!granted) {
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        "Batch alerts are off — turn them on anytime under Studio → System."
                    )
                }
            }
        }
    )

    val channelUrl = stringResource(R.string.developer_channel_url)

    val detectedPreviewInfo by remember(uiState.rawInputText) {
        derivedStateOf {
            val sampleLine = uiState.rawInputText.lineSequence().firstOrNull { it.isNotBlank() }
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
            snackbarHostState.showSnackbar("Copied $label")
        }
    }

    fun pasteFromClipboard(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = clipboard?.primaryClip
        val item = if (clip != null && clip.itemCount > 0) clip.getItemAt(0) else null
        return item?.coerceToText(context)?.toString()
    }

    fun openDeveloperChannel() {
        FirebaseMetrics.logDeveloperChannelOpened(source = "workspace")
        runCatching {
            val intent = Intent(Intent.ACTION_VIEW, channelUrl.toUri()).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    if (uiState.isSettingsOpen) {
        SettingsBottomSheet(
            settings = uiState.settings,
            networkStats = uiState.networkStats,
            deviceNetworkStatus = uiState.deviceNetworkStatus,
            permissionSnapshot = permissionSnapshot,
            isTestingEndpoint = uiState.isTestingEndpoint,
            httpProbeResult = uiState.httpProbeResult,
            isTestingTelegram = uiState.isTestingTelegram,
            telegramProbeResult = uiState.telegramProbeResult,
            onDismiss = viewModel::closeSettings,
            onTestEndpoint = viewModel::testHttpEndpoint,
            onDetectTelegramChat = viewModel::detectTelegramBotAndChat,
            onTestTelegramWebhook = viewModel::sendTelegramTestWebhook,
            onClearCaches = viewModel::clearNetworkAndBinCaches,
            onRequestPermissions = {
                val missing = AppPermissionHelper.missingRuntimePermissions(context)
                if (missing.isNotEmpty()) {
                    manualPermissionLauncher.launch(missing.toTypedArray())
                } else {
                    AppPermissionHelper.openNotificationOrAppSettings(context)
                }
            },
            onOpenPermissionSettings = {
                AppPermissionHelper.openNotificationOrAppSettings(context)
            },
            onSaveSettings = viewModel::saveSettings,
            onGenerateBatchToConsole = viewModel::generateBatchIntoConsole,
            onLoadSandboxSuite = viewModel::loadSandboxVectorsIntoConsole,
            onClearAllBuckets = viewModel::clearAllResults,
            onOpenConfigStudio = viewModel::openConfigStudio,
            onToggleFirebaseMetrics = viewModel::setFirebaseMetricsQuick
        )
    }

    if (uiState.isConfigStudioOpen) {
        CustomConfigStudioBottomSheet(
            initialConfig = uiState.settings.toCustomHttpConfig(),
            savedProfiles = uiState.settings.savedCustomProfiles(),
            isTestingEndpoint = uiState.isTestingEndpoint,
            probeResult = uiState.httpProbeResult,
            onTestConfig = viewModel::testHttpEndpoint,
            onClearProbeResult = viewModel::clearHttpProbeResult,
            onSaveAndUseConfig = viewModel::applyCustomConfig,
            onSaveProfile = viewModel::saveCustomConfigProfile,
            onDeleteProfile = viewModel::deleteCustomConfigProfile,
            onDismiss = viewModel::closeConfigStudio
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
            filteredTotalCount = uiState.historyFilteredTotalCount,
            hasMore = uiState.hasMoreHistory,
            onLoadMore = viewModel::loadMoreHistory,
            onSelectFilter = viewModel::setHistoryFilter,
            onSearchQueryChanged = viewModel::setHistorySearchQuery,
            onCopyEntry = { entry -> copyToClipboard("History Record", entry.normalizedLine) },
            onDeleteEntry = viewModel::deleteHistoryEntry,
            onClearAllHistory = viewModel::clearAllHistory,
            onDismiss = viewModel::closeHistory
        )
    }

    if (uiState.isBinSheetOpen) {
        val recentLiveBins = remember(uiState.liveRecords) {
            uiState.liveRecords
                .map { BinLookupService.extractBin6(it.pan) }
                .filter { it.length == 6 }
                .distinct()
        }
        BinLookupBottomSheet(
            binInput = uiState.inspectedBinInput,
            binInfo = uiState.inspectedBinInfo,
            isLoading = uiState.isInspectingBin,
            recentLiveBins = recentLiveBins,
            onInputChanged = viewModel::onInspectedBinInputChanged,
            onLookup = { bin -> viewModel.lookupInspectedBin(bin, forceRefresh = true) },
            onCopySummary = { info -> copyToClipboard("BIN ${info.bin}", info.fullClipboardText()) },
            onDismiss = viewModel::closeBinInspector
        )
    }

    if (uiState.isToolkitOpen) {
        val liveText = remember(uiState.liveRecords) {
            uiState.liveRecords.joinToString("\n") { it.normalizedLine }
        }
        val allRecordsList = remember(uiState.liveRecords, uiState.dieRecords, uiState.unknownRecords) {
            uiState.liveRecords + uiState.dieRecords + uiState.unknownRecords
        }
        val allText = remember(allRecordsList) {
            allRecordsList.joinToString("\n") { it.normalizedLine }
        }
        ListToolkitBottomSheet(
            initialInputText = uiState.rawInputText,
            liveResultsText = liveText,
            allResultsText = allText,
            liveResultsCount = uiState.liveRecords.size,
            allResultsCount = allRecordsList.size,
            onPasteFromClipboard = ::pasteFromClipboard,
            onCopyOutput = { cleaned ->
                copyToClipboard("Cleaned List", cleaned)
            },
            onApplyToConsole = viewModel::applyToolkitOutput,
            onDismiss = viewModel::closeListToolkit,
            onFileStatusMessage = { msg ->
                scope.launch {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(msg)
                }
            }
        )
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isExpandedNavigation = maxWidth >= 840.dp

        LaunchedEffect(drawerState.isOpen) {
            if (drawerState.isOpen && !isExpandedNavigation) {
                FirebaseMetrics.logScreenView("NavigationDrawer", "MainActivity")
            }
        }

        BinCheckNavigationShell(
            isExpandedWidth = isExpandedNavigation,
            drawerState = drawerState,
            uiState = uiState,
            activePane = activeCompactPane,
            channelUrl = channelUrl,
            onSelectPane = { activeCompactPane = it },
            onOpenBinLookup = { viewModel.openBinInspector() },
            onOpenListToolkit = viewModel::openListToolkit,
            onOpenConfigStudio = viewModel::openConfigStudio,
            onOpenFileImport = viewModel::openFileImport,
            onOpenHistory = viewModel::openHistory,
            onOpenSettings = viewModel::openSettings,
            onSelectThemeMode = viewModel::setThemeModeQuick,
            onToggleDynamicColor = viewModel::setDynamicColorQuick,
            onOpenDeveloperChannel = ::openDeveloperChannel,
            onCloseDrawer = {
                scope.launch { drawerState.close() }
            }
        ) {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background),
                containerColor = colorScheme.background,
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
                    BinCheckTopBar(
                        showMenuButton = !isExpandedNavigation,
                        isRunning = uiState.isRunning,
                        isLookingUpBin = uiState.loadingBins.isNotEmpty() || uiState.isInspectingBin,
                        processedCount = uiState.processedCount,
                        totalBatchCount = uiState.totalBatchCount,
                        validationMode = uiState.settings.validationMode,
                        httpMethod = uiState.settings.httpMethod,
                        concurrency = uiState.settings.concurrency,
                        historyCount = uiState.historySummary.totalCount,
                        onOpenDrawer = {
                            scope.launch { drawerState.open() }
                        },
                        onOpenBinLookup = { viewModel.openBinInspector() },
                        onOpenHistory = viewModel::openHistory,
                        onOpenSettings = viewModel::openSettings
                    )

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth(),
                        thickness = 0.5.dp,
                        color = colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val isTwoPaneLayout = maxWidth >= 700.dp && maxHeight >= 360.dp

                        LaunchedEffect(isTwoPaneLayout, activeCompactPane) {
                            val screenName = if (isTwoPaneLayout) {
                                "Workspace_TwoPane"
                            } else {
                                when (activeCompactPane) {
                                    CompactWorkspacePane.SPLIT -> "Workspace_Split"
                                    CompactWorkspacePane.CONSOLE -> "Workspace_Console"
                                    CompactWorkspacePane.RESULTS -> "Workspace_Results"
                                }
                            }
                            FirebaseMetrics.logScreenView(screenName, "MainActivity")
                        }
                        val horizontalPadding = when {
                            maxWidth < 380.dp -> 12.dp
                            maxWidth < 600.dp -> 16.dp
                            else -> 22.dp
                        }
                        val innerCardPadding = if (maxWidth < 380.dp) 12.dp else 16.dp
                        val (binDigits, scheme) = detectedPreviewInfo

                        val renderConsole: @Composable (Dp?, Modifier) -> Unit = { height, cardModifier ->
                            ConsoleInputCard(
                                uiState = uiState,
                                consoleHeight = height,
                                cardPadding = innerCardPadding,
                                onInputChanged = viewModel::onInputTextChanged,
                                onToggleRun = viewModel::toggleValidationRun,
                                onOpenSettings = viewModel::openSettings,
                                onOpenFileImport = viewModel::openFileImport,
                                onQuickGen10x = {
                                    val patternToUse = uiState.rawInputText.lineSequence()
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
                                onOpenListToolkit = viewModel::openListToolkit,
                                onOpenConfigStudio = viewModel::openConfigStudio,
                                onQuickSwitchMode = viewModel::setValidationModeQuick,
                                onQuickLoadSandbox = {
                                    viewModel.loadSandboxVectorsIntoConsole(SandboxProvider.ALL_SANDBOXES)
                                },
                                modifier = cardModifier
                            )
                        }

                        val renderResults: @Composable (Boolean, Modifier) -> Unit = { fillHeight, cardModifier ->
                            ResultsCard(
                                uiState = uiState,
                                fillRemainingHeight = fillHeight,
                                onSelectTab = viewModel::selectTab,
                                onCopyAll = { title, joined ->
                                    val count = joined.lineSequence().count { it.isNotBlank() }
                                    FirebaseMetrics.logResultsCopied(scope = "tab_all", count = count)
                                    copyToClipboard(title, joined)
                                },
                                onClearTab = viewModel::clearActiveTabRecords,
                                onCopyRecord = { record ->
                                    FirebaseMetrics.logResultsCopied(scope = "single", count = 1)
                                    copyToClipboard("Record", record.normalizedLine)
                                },
                                onRemoveRecord = { record -> viewModel.removeRecord(record.id, record.bucket) },
                                onInspectBin = viewModel::openBinInspector,
                                modifier = cardModifier
                            )
                        }

                        if (isTwoPaneLayout) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
                            ) {
                                TopTelemetryStrip(
                                    binDigits = binDigits,
                                    detectedScheme = scheme,
                                    loadedFileName = uiState.loadedFileName,
                                    onOpenBinLookup = { viewModel.openBinInspector(binDigits) },
                                    onOpenDeveloperChannel = ::openDeveloperChannel
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    renderConsole(
                                        null,
                                        Modifier
                                            .weight(0.47f)
                                            .fillMaxHeight()
                                    )

                                    renderResults(
                                        true,
                                        Modifier
                                            .weight(0.53f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            val canPinSplitWithoutNestedScroll = maxHeight >= 540.dp
                            val adaptiveConsoleHeight = (maxHeight * 0.30f).coerceIn(170.dp, 236.dp)

                            val outerModifier = if (canPinSplitWithoutNestedScroll) {
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = horizontalPadding, vertical = 10.dp)
                            } else {
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = horizontalPadding, vertical = 10.dp)
                            }

                            Box(
                                modifier = outerModifier,
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .widthIn(max = 600.dp)
                                        .then(if (canPinSplitWithoutNestedScroll) Modifier.fillMaxHeight() else Modifier),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    TopTelemetryStrip(
                                        binDigits = binDigits,
                                        detectedScheme = scheme,
                                        loadedFileName = uiState.loadedFileName,
                                        onOpenBinLookup = { viewModel.openBinInspector(binDigits) },
                                        onOpenDeveloperChannel = ::openDeveloperChannel
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                    ) {
                                        val panes = CompactWorkspacePane.entries
                                        panes.forEachIndexed { index, pane ->
                                            val shapes = when (index) {
                                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                                panes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                            }
                                            val isSelected = activeCompactPane == pane
                                            val suffix = when (pane) {
                                                CompactWorkspacePane.SPLIT -> ""
                                                CompactWorkspacePane.CONSOLE -> if (uiState.inputLineCount > 0) " (${uiState.inputLineCount})" else ""
                                                CompactWorkspacePane.RESULTS -> {
                                                    val total = uiState.liveRecords.size + uiState.dieRecords.size + uiState.unknownRecords.size
                                                    if (total > 0) " ($total)" else ""
                                                }
                                            }
                                            ToggleButton(
                                                checked = isSelected,
                                                onCheckedChange = { activeCompactPane = pane },
                                                shapes = shapes,
                                                colors = ToggleButtonDefaults.toggleButtonColors(
                                                    containerColor = colorScheme.surfaceContainerHigh,
                                                    contentColor = colorScheme.onSurfaceVariant,
                                                    checkedContainerColor = colorScheme.primaryContainer,
                                                    checkedContentColor = colorScheme.onPrimaryContainer
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(42.dp)
                                                    .testTag("compact_pane_${pane.name.lowercase()}")
                                            ) {
                                                Text(
                                                    text = stringResource(pane.labelRes) + suffix,
                                                    style = if (isSelected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelMedium,
                                                    maxLines = 1
                                                )
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    AnimatedContent(
                                        targetState = activeCompactPane,
                                        transitionSpec = {
                                            (fadeIn(animationSpec = spring(stiffness = Spring.StiffnessMediumLow)) +
                                                scaleIn(
                                                    initialScale = 0.985f,
                                                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                                                )) togetherWith fadeOut(animationSpec = spring(stiffness = Spring.StiffnessMedium))
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (canPinSplitWithoutNestedScroll) Modifier.weight(1f) else Modifier),
                                        label = "compact_pane_transition"
                                    ) { pane ->
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .then(if (canPinSplitWithoutNestedScroll) Modifier.fillMaxHeight() else Modifier)
                                        ) {
                                            when (pane) {
                                                CompactWorkspacePane.SPLIT -> {
                                                    renderConsole(adaptiveConsoleHeight, Modifier)
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    renderResults(
                                                        canPinSplitWithoutNestedScroll,
                                                        if (canPinSplitWithoutNestedScroll) Modifier.weight(1f) else Modifier
                                                    )
                                                }

                                                CompactWorkspacePane.CONSOLE -> {
                                                    renderConsole(
                                                        if (canPinSplitWithoutNestedScroll) null else 260.dp,
                                                        if (canPinSplitWithoutNestedScroll) Modifier.weight(1f) else Modifier
                                                    )
                                                }

                                                CompactWorkspacePane.RESULTS -> {
                                                    renderResults(
                                                        canPinSplitWithoutNestedScroll,
                                                        if (canPinSplitWithoutNestedScroll) Modifier.weight(1f) else Modifier
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
        }
    }
}
