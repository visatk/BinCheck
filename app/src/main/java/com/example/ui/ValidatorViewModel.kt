package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.data.AppThemeMode
import com.example.data.ValidatorPreferencesRepository
import com.example.data.ValidatorSettings
import com.example.data.local.AppDatabase
import com.example.data.local.HistoryFilter
import com.example.data.local.ValidationHistoryEntity
import com.example.data.local.ValidationHistoryRepository
import com.example.domain.BinInfo
import com.example.domain.BinLookupService
import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.CustomHttpClient
import com.example.domain.CustomHttpConfig
import com.example.domain.DeviceNetworkStatus
import com.example.domain.FileTextExtractor
import com.example.domain.HttpProbeResult
import com.example.domain.ImportedFilePreview
import com.example.domain.NetworkClientProvider
import com.example.domain.NetworkTelemetrySnapshot
import com.example.domain.ResultBucket
import com.example.domain.TelegramProbeResult
import com.example.domain.TelegramWebhookClient
import com.example.domain.TelegramWebhookConfig
import com.example.domain.ToolkitTransformResult
import com.example.domain.ValidationApiProviders
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import com.example.worker.BatchValidationWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.TimeUnit

@Immutable
data class HistoryUiSummary(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0
)

@Immutable
data class ValidatorUiState(
    val rawInputText: String = "",
    val isRunning: Boolean = false,
    val processedCount: Int = 0,
    val totalBatchCount: Int = 0,
    val selectedTab: ResultBucket = ResultBucket.LIVE,
    val liveRecords: List<ValidationRecord> = emptyList(),
    val dieRecords: List<ValidationRecord> = emptyList(),
    val unknownRecords: List<ValidationRecord> = emptyList(),
    val isSettingsOpen: Boolean = false,
    val isFileImportOpen: Boolean = false,
    val isHistoryOpen: Boolean = false,
    val isToolkitOpen: Boolean = false,
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val historySearchQuery: String = "",
    val historyEntries: List<ValidationHistoryEntity> = emptyList(),
    val historyFilteredTotalCount: Int = 0,
    val hasMoreHistory: Boolean = false,
    val historySummary: HistoryUiSummary = HistoryUiSummary(),
    val isImportingFile: Boolean = false,
    val fileImportError: String? = null,
    val importedFilePreview: ImportedFilePreview? = null,
    val loadedFileName: String? = null,
    val settings: ValidatorSettings = ValidatorSettings(),
    val isTestingEndpoint: Boolean = false,
    val httpProbeResult: HttpProbeResult? = null,
    val isTestingTelegram: Boolean = false,
    val telegramProbeResult: TelegramProbeResult? = null,
    val binLookupMap: Map<String, BinInfo> = emptyMap(),
    val loadingBins: Set<String> = emptySet(),
    val isBinSheetOpen: Boolean = false,
    val inspectedBinInput: String = "",
    val inspectedBinInfo: BinInfo? = null,
    val isInspectingBin: Boolean = false,
    val networkStats: NetworkTelemetrySnapshot = NetworkTelemetrySnapshot(),
    val deviceNetworkStatus: DeviceNetworkStatus = DeviceNetworkStatus(),
    val statusBannerMessage: String? = null
) {
    val activeTabRecords: List<ValidationRecord>
        get() = when (selectedTab) {
            ResultBucket.LIVE -> liveRecords
            ResultBucket.DIE -> dieRecords
            ResultBucket.UNKNOWN -> unknownRecords
        }

    val inputLineCount: Int
        get() = rawInputText.lines().count { it.isNotBlank() }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class ValidatorViewModel(
    application: Application,
    private val preferencesRepository: ValidatorPreferencesRepository,
    private val historyRepository: ValidationHistoryRepository,
    private val workManager: WorkManager
) : AndroidViewModel(application) {

    private val _localState = MutableStateFlow(ValidatorUiState())
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    private val _historyQuery = MutableStateFlow("")
    private val _historyPageLimit = MutableStateFlow(ValidationHistoryRepository.DEFAULT_PAGE_SIZE)
    private val _binInputStream = MutableStateFlow("")

    private var activeWorkId: UUID? = null
    private var workerObserverJob: Job? = null
    private var batchResultsObserverJob: Job? = null

    init {
        NetworkClientProvider.init(application)
        BinLookupService.attachDao(AppDatabase.getInstance(application).binCacheDao())
        refreshDeviceAndNetworkTelemetry()
        observeDebouncedBinInput()
        reconnectOngoingBatchWork()
    }

    private fun observeDebouncedBinInput() {
        viewModelScope.launch {
            _binInputStream
                .map { it.filter { ch -> ch.isDigit() } }
                .distinctUntilChanged()
                .debounce(400L)
                .filter { digits -> digits.length >= 6 && _localState.value.isBinSheetOpen }
                .collect { digits ->
                    val bin6 = BinLookupService.extractBin6(digits)
                    val cached = _localState.value.binLookupMap[bin6] ?: BinLookupService.getCached(bin6)
                    if (cached == null || !cached.isNetworkVerified) {
                        lookupInspectedBin(bin6, forceRefresh = false)
                    } else {
                        _localState.update { it.copy(inspectedBinInfo = cached) }
                    }
                }
        }
    }

    private val debouncedHistoryQueryFlow = _historyQuery
        .map { it.trim() }
        .debounce(220L)
        .distinctUntilChanged()

    private val pagedHistoryQueryParams = combine(
        _historyFilter,
        debouncedHistoryQueryFlow,
        _historyPageLimit
    ) { filter, query, limit ->
        Triple(filter, query, limit)
    }

    private val pagedHistoryFlow = pagedHistoryQueryParams.flatMapLatest { (filter, query, limit) ->
        historyRepository.observePagedHistory(filter = filter, query = query, limit = limit)
    }

    private val filteredHistoryCountFlow = combine(
        _historyFilter,
        debouncedHistoryQueryFlow
    ) { filter, query -> filter to query }.flatMapLatest { (filter, query) ->
        historyRepository.observeFilteredCount(filter = filter, query = query)
    }

    private val historySummaryFlow = combine(
        historyRepository.totalHistoryCount,
        historyRepository.successHistoryCount,
        historyRepository.failureHistoryCount
    ) { total, success, failure ->
        HistoryUiSummary(totalCount = total, successCount = success, failureCount = failure)
    }

    val uiState: StateFlow<ValidatorUiState> = combine(
        _localState,
        preferencesRepository.settingsFlow,
        combine(pagedHistoryFlow, filteredHistoryCountFlow) { entries, filteredTotal -> entries to filteredTotal },
        historySummaryFlow,
        combine(_historyFilter, _historyQuery) { f, q -> f to q }
    ) { state, prefs, pagedPair, summary, filterPair ->
        val entries = pagedPair.first
        val filteredTotal = pagedPair.second
        state.copy(
            settings = prefs,
            historyEntries = entries,
            historyFilteredTotalCount = filteredTotal,
            hasMoreHistory = entries.size < filteredTotal,
            historySummary = summary,
            historyFilter = filterPair.first,
            historySearchQuery = filterPair.second,
            networkStats = NetworkClientProvider.telemetry.snapshot()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ValidatorUiState()
    )

    fun refreshDeviceAndNetworkTelemetry() {
        val deviceStatus = NetworkClientProvider.inspectDeviceNetworkStatus(getApplication())
        _localState.update {
            it.copy(
                deviceNetworkStatus = deviceStatus,
                networkStats = NetworkClientProvider.telemetry.snapshot()
            )
        }
    }

    fun clearNetworkAndBinCaches() {
        viewModelScope.launch {
            BinLookupService.clearAllCaches()
            ValidationApiProviders.clearCache()
            CustomHttpClient.clearCache()
            NetworkClientProvider.clearDiskHttpCache()
            NetworkClientProvider.circuitBreaker.resetAll()
            NetworkClientProvider.telemetry.reset()
            _localState.update {
                it.copy(
                    networkStats = NetworkClientProvider.telemetry.snapshot(),
                    statusBannerMessage = "Network, BIN, and circuit caches cleared"
                )
            }
        }
    }

    fun onInputTextChanged(newText: String) {
        _localState.update {
            it.copy(
                rawInputText = newText,
                loadedFileName = if (newText.isBlank()) null else it.loadedFileName,
                statusBannerMessage = null
            )
        }
    }

    fun selectTab(bucket: ResultBucket) {
        _localState.update { it.copy(selectedTab = bucket) }
    }

    fun openSettings() {
        refreshDeviceAndNetworkTelemetry()
        _localState.update {
            it.copy(
                isSettingsOpen = true,
                httpProbeResult = null,
                telegramProbeResult = null,
                statusBannerMessage = null
            )
        }
    }

    fun closeSettings() {
        _localState.update {
            it.copy(
                isSettingsOpen = false,
                httpProbeResult = null,
                telegramProbeResult = null
            )
        }
    }

    fun testHttpEndpoint(config: CustomHttpConfig) {
        if (_localState.value.isTestingEndpoint) return
        _localState.update { it.copy(isTestingEndpoint = true, httpProbeResult = null) }
        viewModelScope.launch {
            val probe = CustomHttpClient.testEndpoint(config)
            _localState.update {
                it.copy(
                    isTestingEndpoint = false,
                    httpProbeResult = probe,
                    networkStats = NetworkClientProvider.telemetry.snapshot()
                )
            }
        }
    }

    fun detectTelegramBotAndChat() {
        if (_localState.value.isTestingTelegram) return
        _localState.update { it.copy(isTestingTelegram = true, telegramProbeResult = null) }
        viewModelScope.launch {
            val probe = TelegramWebhookClient.verifyAndDetectChat()
            _localState.update {
                it.copy(
                    isTestingTelegram = false,
                    telegramProbeResult = probe,
                    networkStats = NetworkClientProvider.telemetry.snapshot()
                )
            }
        }
    }

    fun sendTelegramTestWebhook(chatId: String) {
        if (_localState.value.isTestingTelegram) return
        _localState.update { it.copy(isTestingTelegram = true, telegramProbeResult = null) }
        viewModelScope.launch {
            val probe = TelegramWebhookClient.sendTestMessage(chatId)
            _localState.update {
                it.copy(
                    isTestingTelegram = false,
                    telegramProbeResult = probe,
                    networkStats = NetworkClientProvider.telemetry.snapshot()
                )
            }
        }
    }

    fun openFileImport() {
        _localState.update {
            it.copy(
                isFileImportOpen = true,
                fileImportError = null,
                statusBannerMessage = null
            )
        }
    }

    fun closeFileImport() {
        _localState.update {
            it.copy(
                isFileImportOpen = false,
                fileImportError = null
            )
        }
    }

    fun openHistory() {
        _historyPageLimit.value = ValidationHistoryRepository.DEFAULT_PAGE_SIZE
        _localState.update { it.copy(isHistoryOpen = true, statusBannerMessage = null) }
    }

    fun closeHistory() {
        _localState.update { it.copy(isHistoryOpen = false) }
    }

    fun openListToolkit() {
        _localState.update { it.copy(isToolkitOpen = true, statusBannerMessage = null) }
    }

    fun closeListToolkit() {
        _localState.update { it.copy(isToolkitOpen = false) }
    }

    fun applyToolkitOutput(outputText: String, result: ToolkitTransformResult) {
        val banner = if (result.totalRemoved > 0) {
            "Toolkit: ${result.outputCount} kept (${result.totalRemoved} removed)"
        } else {
            "Toolkit applied ${result.outputCount} formatted record(s)"
        }
        _localState.update {
            it.copy(
                rawInputText = outputText,
                loadedFileName = if (outputText.isBlank()) null else it.loadedFileName,
                isToolkitOpen = false,
                statusBannerMessage = banner
            )
        }
    }

    fun loadMoreHistory() {
        _historyPageLimit.update { current ->
            (current + ValidationHistoryRepository.DEFAULT_PAGE_SIZE)
                .coerceAtMost(ValidationHistoryRepository.MAX_HISTORY_ROWS)
        }
    }

    fun openBinInspector(initialPanOrBin: String = "") {
        val binDigits = BinLookupService.extractBin6(initialPanOrBin)
        val cached = if (binDigits.length == 6) {
            _localState.value.binLookupMap[binDigits] ?: BinLookupService.getCached(binDigits)
        } else {
            null
        }
        _localState.update {
            it.copy(
                isBinSheetOpen = true,
                inspectedBinInput = binDigits,
                inspectedBinInfo = cached,
                isInspectingBin = false
            )
        }
        if (binDigits.length == 6 && (cached == null || !cached.isNetworkVerified)) {
            lookupInspectedBin(binDigits, forceRefresh = true)
        }
    }

    fun closeBinInspector() {
        _localState.update { it.copy(isBinSheetOpen = false, isInspectingBin = false) }
    }

    fun onInspectedBinInputChanged(newInput: String) {
        val digitsOnly = newInput.filter { it.isDigit() }.take(8)
        _localState.update { it.copy(inspectedBinInput = digitsOnly) }
        _binInputStream.value = digitsOnly
    }

    fun lookupInspectedBin(panOrBin: String = _localState.value.inspectedBinInput, forceRefresh: Boolean = true) {
        val bin6 = BinLookupService.extractBin6(panOrBin)
        if (bin6.length < 6) {
            _localState.update {
                it.copy(statusBannerMessage = "Enter at least 6 digits to lookup BIN/IIN")
            }
            return
        }
        _localState.update {
            it.copy(
                inspectedBinInput = panOrBin.filter { ch -> ch.isDigit() }.take(8).ifEmpty { bin6 },
                isInspectingBin = true,
                loadingBins = it.loadingBins + bin6
            )
        }
        viewModelScope.launch {
            val info = BinLookupService.lookupBin(bin6, forceRefresh = forceRefresh)
            _localState.update { prev ->
                val updatedMap = if (info != null) prev.binLookupMap + (bin6 to info) else prev.binLookupMap
                prev.copy(
                    isInspectingBin = false,
                    inspectedBinInfo = info,
                    binLookupMap = updatedMap,
                    loadingBins = prev.loadingBins - bin6,
                    networkStats = NetworkClientProvider.telemetry.snapshot()
                )
            }
        }
    }

    private fun enrichLiveRecords(liveRecords: List<ValidationRecord>) {
        val settings = uiState.value.settings
        val deviceStatus = NetworkClientProvider.inspectDeviceNetworkStatus(getApplication())
        if (!settings.autoBinLookupForLive || liveRecords.isEmpty()) return
        if ((settings.batterySaverMode || deviceStatus.shouldThrottleForBatteryOrData) && deviceStatus.isMetered) {
            return
        }

        val distinctBins = liveRecords
            .map { BinLookupService.extractBin6(it.pan) }
            .filter { it.length == 6 }
            .distinct()

        for (bin6 in distinctBins) {
            val state = _localState.value
            val cached = state.binLookupMap[bin6]
            if ((cached != null && cached.isNetworkVerified) || bin6 in state.loadingBins) {
                continue
            }
            _localState.update { it.copy(loadingBins = it.loadingBins + bin6) }
            viewModelScope.launch {
                val info = BinLookupService.lookupBin(bin6, forceRefresh = false)
                _localState.update { prev ->
                    val nextMap = if (info != null) prev.binLookupMap + (bin6 to info) else prev.binLookupMap
                    prev.copy(
                        binLookupMap = nextMap,
                        loadingBins = prev.loadingBins - bin6,
                        networkStats = NetworkClientProvider.telemetry.snapshot()
                    )
                }
            }
        }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyPageLimit.value = ValidationHistoryRepository.DEFAULT_PAGE_SIZE
        _historyFilter.value = filter
    }

    fun setHistorySearchQuery(query: String) {
        _historyPageLimit.value = ValidationHistoryRepository.DEFAULT_PAGE_SIZE
        _historyQuery.value = query
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            historyRepository.deleteEntry(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            historyRepository.clearAllHistory()
            _localState.update { it.copy(statusBannerMessage = "History log cleared") }
        }
    }

    fun clearFilePreview() {
        _localState.update {
            it.copy(
                importedFilePreview = null,
                fileImportError = null
            )
        }
    }

    fun onFileUriSelected(uri: Uri) {
        _localState.update {
            it.copy(
                isFileImportOpen = true,
                isImportingFile = true,
                fileImportError = null
            )
        }

        viewModelScope.launch {
            val result = FileTextExtractor.readAndParseUri(getApplication(), uri)
            result.fold(
                onSuccess = { preview ->
                    _localState.update {
                        it.copy(
                            isImportingFile = false,
                            importedFilePreview = preview,
                            fileImportError = if (preview.allNonEmptyLines.isEmpty()) {
                                "Selected file has no readable text lines."
                            } else {
                                null
                            }
                        )
                    }
                },
                onFailure = { err ->
                    _localState.update {
                        it.copy(
                            isImportingFile = false,
                            importedFilePreview = null,
                            fileImportError = err.localizedMessage ?: "Failed to read selected file."
                        )
                    }
                }
            )
        }
    }

    fun applyImportedFile(
        append: Boolean,
        onlyDelimitedRecords: Boolean,
        startImmediately: Boolean
    ) {
        val preview = _localState.value.importedFilePreview ?: return
        val chosenLines = if (onlyDelimitedRecords && preview.delimitedRecordLines.isNotEmpty()) {
            preview.delimitedRecordLines
        } else {
            preview.allNonEmptyLines
        }

        if (chosenLines.isEmpty()) {
            _localState.update {
                it.copy(fileImportError = "No lines available to load from ${preview.fileName}.")
            }
            return
        }

        val payload = chosenLines.joinToString("\n")
        _localState.update { prev ->
            val existing = prev.rawInputText.trim()
            val merged = if (append && existing.isNotEmpty()) {
                "$existing\n$payload"
            } else {
                payload
            }
            prev.copy(
                rawInputText = merged,
                loadedFileName = preview.fileName,
                isFileImportOpen = false,
                fileImportError = null,
                statusBannerMessage = "Loaded ${chosenLines.size} line(s) from ${preview.fileName}"
            )
        }

        if (startImmediately) {
            startBatchRun()
        }
    }

    fun clearStatusBanner() {
        _localState.update { it.copy(statusBannerMessage = null) }
    }

    fun toggleValidationRun() {
        if (_localState.value.isRunning) {
            stopBatchRun()
        } else {
            startBatchRun()
        }
    }

    private fun stopBatchRun() {
        activeWorkId?.let { workManager.cancelWorkById(it) }
        workerObserverJob?.cancel()
        batchResultsObserverJob?.cancel()
        activeWorkId = null
        _localState.update {
            it.copy(
                isRunning = false,
                statusBannerMessage = "Batch stopped (${it.processedCount}/${it.totalBatchCount} processed)"
            )
        }
    }

    private fun startBatchRun() {
        val currentState = uiState.value
        val lines = currentState.rawInputText
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (lines.isEmpty()) {
            _localState.update {
                it.copy(
                    statusBannerMessage = "Console is empty. Import a .txt/.html file, paste records, or tap ⚙."
                )
            }
            return
        }

        workerObserverJob?.cancel()
        batchResultsObserverJob?.cancel()

        val settings = currentState.settings
        val deviceStatus = NetworkClientProvider.inspectDeviceNetworkStatus(getApplication())
        val isNetworkEngine = settings.validationMode != ValidationMode.LOCAL_DETERMINISTIC ||
            (settings.telegramWebhookEnabled && settings.telegramChatId.isNotBlank())

        val effectiveConcurrency = if (settings.batterySaverMode || deviceStatus.shouldThrottleForBatteryOrData) {
            settings.concurrency.coerceAtMost(2)
        } else {
            settings.concurrency
        }

        val batchId = UUID.randomUUID().toString()
        val sourceName = currentState.loadedFileName ?: "Console"

        viewModelScope.launch {
            historyRepository.enqueueBatch(
                batchId = batchId,
                lines = lines,
                sourceName = sourceName
            )

            val effectiveDelayMs = settings.effectiveDelayMs
            val inputData = workDataOf(
                BatchValidationWorker.KEY_BATCH_ID to batchId,
                BatchValidationWorker.KEY_VALIDATION_MODE to settings.validationMode.name,
                BatchValidationWorker.KEY_WEBHOOK_URL to settings.webhookUrl,
                BatchValidationWorker.KEY_HTTP_METHOD to settings.httpMethod.name,
                BatchValidationWorker.KEY_HTTP_HEADERS to settings.httpHeaders,
                BatchValidationWorker.KEY_HTTP_BODY_TEMPLATE to settings.httpBodyTemplate,
                BatchValidationWorker.KEY_HTTP_TIMEOUT_SECONDS to settings.httpTimeoutSeconds,
                BatchValidationWorker.KEY_HTTP_MAX_RETRIES to settings.httpMaxRetries,
                BatchValidationWorker.KEY_HTTP_LIVE_KEYWORDS to settings.httpLiveKeywords,
                BatchValidationWorker.KEY_HTTP_DIE_KEYWORDS to settings.httpDieKeywords,
                BatchValidationWorker.KEY_GZIP_REQUESTS to settings.gzipCustomRequests,
                BatchValidationWorker.KEY_DEDUPLICATE_INPUTS to settings.deduplicateBatchInputs,
                BatchValidationWorker.KEY_AUTO_RESOLVE_WILDCARDS to settings.autoResolveWildcards,
                BatchValidationWorker.KEY_CONCURRENCY to effectiveConcurrency,
                BatchValidationWorker.KEY_BATCH_DELAY_MS to effectiveDelayMs,
                BatchValidationWorker.KEY_SOURCE_NAME to sourceName,
                BatchValidationWorker.KEY_TELEGRAM_ENABLED to settings.telegramWebhookEnabled,
                BatchValidationWorker.KEY_TELEGRAM_CHAT_ID to settings.telegramChatId,
                BatchValidationWorker.KEY_TELEGRAM_LIVE_ONLY to settings.telegramNotifyLiveOnly
            )

            val constraintsBuilder = Constraints.Builder()
            if (isNetworkEngine) {
                constraintsBuilder.setRequiredNetworkType(
                    if (settings.wifiOnlyBatch) NetworkType.UNMETERED else NetworkType.CONNECTED
                )
            }
            if (settings.batterySaverMode) {
                constraintsBuilder.setRequiresBatteryNotLow(true)
            }

            val workRequest = OneTimeWorkRequestBuilder<BatchValidationWorker>()
                .setInputData(inputData)
                .setConstraints(constraintsBuilder.build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
                .addTag("batch:$batchId")
                .build()

            activeWorkId = workRequest.id

            val runningStatus = when {
                isNetworkEngine && settings.wifiOnlyBatch && deviceStatus.isMetered ->
                    "Queued for Wi-Fi (Wi-Fi Only mode active)..."
                settings.validationMode.hasFixedDelay ->
                    "Worker running (${settings.validationMode.badgeLabel} • ${effectiveDelayMs / 1000L}s delay)..."
                settings.batterySaverMode || deviceStatus.shouldThrottleForBatteryOrData ->
                    "Eco Worker (${effectiveConcurrency}x • ${effectiveDelayMs}ms delay)..."
                else ->
                    "Worker running (${effectiveConcurrency}x concurrent • ${effectiveDelayMs}ms delay)..."
            }

            _localState.update {
                it.copy(
                    isRunning = true,
                    processedCount = 0,
                    totalBatchCount = lines.size,
                    deviceNetworkStatus = deviceStatus,
                    statusBannerMessage = runningStatus
                )
            }

            observeBatchResultsAndWork(
                workId = workRequest.id,
                batchId = batchId,
                fallbackTotal = lines.size
            )

            workManager.enqueueUniqueWork(
                BatchValidationWorker.UNIQUE_BATCH_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    private fun reconnectOngoingBatchWork() {
        viewModelScope.launch {
            val workInfos = runCatching {
                workManager.getWorkInfosForUniqueWorkFlow(BatchValidationWorker.UNIQUE_BATCH_WORK_NAME)
            }.getOrNull() ?: return@launch

            workInfos.collect { infos ->
                val runningInfo = infos.firstOrNull {
                    it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
                } ?: return@collect

                if (activeWorkId == runningInfo.id) return@collect
                val resumedBatchId = runningInfo.progress.getString(BatchValidationWorker.KEY_BATCH_ID)
                    ?: runningInfo.tags.firstOrNull { it.startsWith("batch:") }?.removePrefix("batch:")
                    ?: return@collect
                val total = runningInfo.progress.getInt(BatchValidationWorker.KEY_TOTAL, 0)
                val processed = runningInfo.progress.getInt(BatchValidationWorker.KEY_PROCESSED, 0)

                activeWorkId = runningInfo.id
                _localState.update {
                    it.copy(
                        isRunning = true,
                        processedCount = processed,
                        totalBatchCount = total,
                        statusBannerMessage = "Reconnected to background worker ($processed/$total)..."
                    )
                }
                observeBatchResultsAndWork(
                    workId = runningInfo.id,
                    batchId = resumedBatchId,
                    fallbackTotal = total
                )
            }
        }
    }

    private fun observeBatchResultsAndWork(
        workId: UUID,
        batchId: String,
        fallbackTotal: Int
    ) {
        workerObserverJob?.cancel()
        batchResultsObserverJob?.cancel()

        batchResultsObserverJob = viewModelScope.launch {
            val seenIds = HashSet<String>()
            historyRepository.observeBatchResults(batchId).collect { batchEntities ->
                val newLive = mutableListOf<ValidationRecord>()
                val newDie = mutableListOf<ValidationRecord>()
                val newUnknown = mutableListOf<ValidationRecord>()

                for (entity in batchEntities) {
                    val recordId = "hist_${entity.id}"
                    if (!seenIds.add(recordId)) continue

                    val bucket = runCatching { ResultBucket.valueOf(entity.status) }
                        .getOrDefault(ResultBucket.UNKNOWN)
                    val scheme = runCatching { CardScheme.valueOf(entity.scheme) }
                        .getOrDefault(CardScheme.UNKNOWN)
                    val record = ValidationRecord(
                        id = recordId,
                        rawLine = entity.rawLine,
                        normalizedLine = entity.normalizedLine,
                        pan = entity.pan,
                        month = entity.month,
                        year = entity.year,
                        cvv = entity.cvv,
                        scheme = scheme,
                        bucket = bucket,
                        message = entity.message,
                        latencyMs = entity.latencyMs
                    )
                    when (bucket) {
                        ResultBucket.LIVE -> newLive.add(0, record)
                        ResultBucket.DIE -> newDie.add(0, record)
                        ResultBucket.UNKNOWN -> newUnknown.add(0, record)
                    }
                }

                if (newLive.isNotEmpty() || newDie.isNotEmpty() || newUnknown.isNotEmpty()) {
                    _localState.update { prev ->
                        prev.copy(
                            liveRecords = newLive + prev.liveRecords,
                            dieRecords = newDie + prev.dieRecords,
                            unknownRecords = newUnknown + prev.unknownRecords
                        )
                    }
                    if (newLive.isNotEmpty()) {
                        enrichLiveRecords(newLive)
                    }
                }
            }
        }

        workerObserverJob = viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(workId).collect { workInfo ->
                if (workInfo == null) return@collect
                val processedFromProgress = workInfo.progress.getInt(BatchValidationWorker.KEY_PROCESSED, -1)
                val totalFromProgress = workInfo.progress.getInt(BatchValidationWorker.KEY_TOTAL, -1)
                val processedFromOutput = workInfo.outputData.getInt(BatchValidationWorker.KEY_PROCESSED, -1)

                when (workInfo.state) {
                    WorkInfo.State.ENQUEUED,
                    WorkInfo.State.RUNNING -> {
                        if (processedFromProgress >= 0) {
                            _localState.update { prev ->
                                val resolvedTotal = if (totalFromProgress > 0) totalFromProgress else prev.totalBatchCount
                                prev.copy(
                                    isRunning = true,
                                    totalBatchCount = resolvedTotal,
                                    processedCount = processedFromProgress.coerceAtMost(resolvedTotal.coerceAtLeast(processedFromProgress)),
                                    networkStats = NetworkClientProvider.telemetry.snapshot()
                                )
                            }
                        }
                    }

                    WorkInfo.State.SUCCEEDED -> {
                        val finalCount = if (processedFromOutput >= 0) processedFromOutput else fallbackTotal
                        activeWorkId = null
                        _localState.update { prev ->
                            prev.copy(
                                isRunning = false,
                                processedCount = finalCount,
                                rawInputText = "",
                                loadedFileName = null,
                                networkStats = NetworkClientProvider.telemetry.snapshot(),
                                statusBannerMessage = "Worker completed $finalCount item(s) • Saved to History"
                            )
                        }
                        batchResultsObserverJob?.cancel()
                        workerObserverJob?.cancel()
                    }

                    WorkInfo.State.CANCELLED -> {
                        activeWorkId = null
                        _localState.update { prev ->
                            prev.copy(
                                isRunning = false,
                                networkStats = NetworkClientProvider.telemetry.snapshot(),
                                statusBannerMessage = "Batch worker cancelled (${prev.processedCount}/${prev.totalBatchCount})"
                            )
                        }
                        batchResultsObserverJob?.cancel()
                        workerObserverJob?.cancel()
                    }

                    WorkInfo.State.FAILED -> {
                        activeWorkId = null
                        _localState.update { prev ->
                            prev.copy(
                                isRunning = false,
                                networkStats = NetworkClientProvider.telemetry.snapshot(),
                                statusBannerMessage = "Batch worker encountered an error"
                            )
                        }
                        batchResultsObserverJob?.cancel()
                        workerObserverJob?.cancel()
                    }

                    WorkInfo.State.BLOCKED -> Unit
                }
            }
        }
    }

    fun generateBatchIntoConsole(pattern: String, quantity: Int) {
        val expanded = CardValidatorEngine.expandPatternBatch(
            templateLine = pattern,
            count = quantity.coerceIn(1, 100)
        )
        _localState.update { prev ->
            val existing = prev.rawInputText.trim()
            val combined = if (existing.isEmpty() || existing == pattern.trim()) {
                expanded.joinToString("\n")
            } else {
                "$existing\n${expanded.joinToString("\n")}"
            }
            prev.copy(
                rawInputText = combined,
                isSettingsOpen = false,
                statusBannerMessage = "Generated ${expanded.size} Luhn-valid records into console"
            )
        }
    }

    fun expandCurrentConsoleWildcards() {
        val currentText = _localState.value.rawInputText.trim()
        if (currentText.isEmpty()) {
            val defaultTemplate = uiState.value.settings.patternTemplate
            val batch = CardValidatorEngine.expandPatternBatch(defaultTemplate, uiState.value.settings.generateQuantity)
            _localState.update {
                it.copy(
                    rawInputText = batch.joinToString("\n"),
                    statusBannerMessage = "Generated ${batch.size} Luhn-valid lines from template"
                )
            }
            return
        }

        val lines = currentText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val expandedLines = if (lines.size == 1 && lines[0].any { it.equals('x', ignoreCase = true) }) {
            CardValidatorEngine.expandPatternBatch(lines[0], uiState.value.settings.generateQuantity)
        } else {
            lines.flatMap { line ->
                if (line.any { it.equals('x', ignoreCase = true) }) {
                    CardValidatorEngine.expandPatternBatch(line, 1)
                } else {
                    listOf(line)
                }
            }
        }

        _localState.update {
            it.copy(
                rawInputText = expandedLines.joinToString("\n"),
                statusBannerMessage = "Expanded ${expandedLines.size} Luhn-valid record(s)"
            )
        }
    }

    fun saveSettings(
        validationMode: ValidationMode,
        httpConfig: CustomHttpConfig,
        telegramConfig: TelegramWebhookConfig,
        autoResolveWildcards: Boolean,
        autoBinLookupForLive: Boolean,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean,
        deduplicateBatchInputs: Boolean = true,
        wifiOnlyBatch: Boolean = false,
        batterySaverMode: Boolean = false
    ) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = validationMode,
                webhookUrl = httpConfig.url,
                httpMethod = httpConfig.method,
                httpHeaders = httpConfig.headersText,
                httpBodyTemplate = httpConfig.bodyTemplate,
                httpTimeoutSeconds = httpConfig.timeoutSeconds,
                httpMaxRetries = httpConfig.maxRetries,
                httpLiveKeywords = httpConfig.liveKeywords,
                httpDieKeywords = httpConfig.dieKeywords,
                autoResolveWildcards = autoResolveWildcards,
                autoBinLookupForLive = autoBinLookupForLive,
                batchDelayMs = batchDelayMs,
                concurrency = concurrency,
                patternTemplate = patternTemplate,
                generateQuantity = generateQuantity,
                themeMode = themeMode,
                useDynamicColor = useDynamicColor,
                telegramWebhookEnabled = telegramConfig.enabled,
                telegramChatId = telegramConfig.chatId,
                telegramNotifyLiveOnly = telegramConfig.notifyLiveOnly,
                deduplicateBatchInputs = deduplicateBatchInputs,
                gzipCustomRequests = httpConfig.compressBodyGzip,
                wifiOnlyBatch = wifiOnlyBatch,
                batterySaverMode = batterySaverMode
            )
            val effectiveDelay = validationMode.resolveDelayMs(batchDelayMs)
            val savedMsg = if (validationMode.hasFixedDelay) {
                "Configuration saved (${validationMode.badgeLabel} • ${effectiveDelay / 1000L}s delay)"
            } else {
                "Configuration saved (${concurrency}x concurrency • ${effectiveDelay}ms delay)"
            }
            _localState.update {
                it.copy(
                    isSettingsOpen = false,
                    httpProbeResult = null,
                    telegramProbeResult = null,
                    statusBannerMessage = savedMsg
                )
            }
        }
    }

    fun setValidationModeQuick(mode: ValidationMode) {
        val s = uiState.value.settings
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = mode,
                webhookUrl = s.webhookUrl,
                httpMethod = s.httpMethod,
                httpHeaders = s.httpHeaders,
                httpBodyTemplate = s.httpBodyTemplate,
                httpTimeoutSeconds = s.httpTimeoutSeconds,
                httpMaxRetries = s.httpMaxRetries,
                httpLiveKeywords = s.httpLiveKeywords,
                httpDieKeywords = s.httpDieKeywords,
                autoResolveWildcards = s.autoResolveWildcards,
                autoBinLookupForLive = s.autoBinLookupForLive,
                batchDelayMs = s.batchDelayMs,
                concurrency = s.concurrency,
                patternTemplate = s.patternTemplate,
                generateQuantity = s.generateQuantity,
                themeMode = s.themeMode,
                useDynamicColor = s.useDynamicColor,
                telegramWebhookEnabled = s.telegramWebhookEnabled,
                telegramChatId = s.telegramChatId,
                telegramNotifyLiveOnly = s.telegramNotifyLiveOnly,
                deduplicateBatchInputs = s.deduplicateBatchInputs,
                gzipCustomRequests = s.gzipCustomRequests,
                wifiOnlyBatch = s.wifiOnlyBatch,
                batterySaverMode = s.batterySaverMode
            )
            val switchMsg = mode.fixedDelayMs?.let { fixedMs ->
                "Engine switched to ${mode.badgeLabel} (${fixedMs / 1000L}s delay)"
            } ?: "Engine switched to ${mode.badgeLabel} (${s.batchDelayMs}ms delay)"
            _localState.update {
                it.copy(statusBannerMessage = switchMsg)
            }
        }
    }

    fun setThemeModeQuick(themeMode: AppThemeMode) {
        val s = uiState.value.settings
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = s.validationMode,
                webhookUrl = s.webhookUrl,
                httpMethod = s.httpMethod,
                httpHeaders = s.httpHeaders,
                httpBodyTemplate = s.httpBodyTemplate,
                httpTimeoutSeconds = s.httpTimeoutSeconds,
                httpMaxRetries = s.httpMaxRetries,
                httpLiveKeywords = s.httpLiveKeywords,
                httpDieKeywords = s.httpDieKeywords,
                autoResolveWildcards = s.autoResolveWildcards,
                autoBinLookupForLive = s.autoBinLookupForLive,
                batchDelayMs = s.batchDelayMs,
                concurrency = s.concurrency,
                patternTemplate = s.patternTemplate,
                generateQuantity = s.generateQuantity,
                themeMode = themeMode,
                useDynamicColor = s.useDynamicColor,
                telegramWebhookEnabled = s.telegramWebhookEnabled,
                telegramChatId = s.telegramChatId,
                telegramNotifyLiveOnly = s.telegramNotifyLiveOnly,
                deduplicateBatchInputs = s.deduplicateBatchInputs,
                gzipCustomRequests = s.gzipCustomRequests,
                wifiOnlyBatch = s.wifiOnlyBatch,
                batterySaverMode = s.batterySaverMode
            )
        }
    }

    fun setDynamicColorQuick(enabled: Boolean) {
        val s = uiState.value.settings
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = s.validationMode,
                webhookUrl = s.webhookUrl,
                httpMethod = s.httpMethod,
                httpHeaders = s.httpHeaders,
                httpBodyTemplate = s.httpBodyTemplate,
                httpTimeoutSeconds = s.httpTimeoutSeconds,
                httpMaxRetries = s.httpMaxRetries,
                httpLiveKeywords = s.httpLiveKeywords,
                httpDieKeywords = s.httpDieKeywords,
                autoResolveWildcards = s.autoResolveWildcards,
                autoBinLookupForLive = s.autoBinLookupForLive,
                batchDelayMs = s.batchDelayMs,
                concurrency = s.concurrency,
                patternTemplate = s.patternTemplate,
                generateQuantity = s.generateQuantity,
                themeMode = s.themeMode,
                useDynamicColor = enabled,
                telegramWebhookEnabled = s.telegramWebhookEnabled,
                telegramChatId = s.telegramChatId,
                telegramNotifyLiveOnly = s.telegramNotifyLiveOnly,
                deduplicateBatchInputs = s.deduplicateBatchInputs,
                gzipCustomRequests = s.gzipCustomRequests,
                wifiOnlyBatch = s.wifiOnlyBatch,
                batterySaverMode = s.batterySaverMode
            )
        }
    }

    fun clearActiveTabRecords() {
        _localState.update { prev ->
            when (prev.selectedTab) {
                ResultBucket.LIVE -> prev.copy(liveRecords = emptyList())
                ResultBucket.DIE -> prev.copy(dieRecords = emptyList())
                ResultBucket.UNKNOWN -> prev.copy(unknownRecords = emptyList())
            }
        }
    }

    fun clearAllResults() {
        _localState.update {
            it.copy(
                liveRecords = emptyList(),
                dieRecords = emptyList(),
                unknownRecords = emptyList(),
                statusBannerMessage = "Cleared current session buckets"
            )
        }
    }

    fun removeRecord(recordId: String, bucket: ResultBucket) {
        _localState.update { prev ->
            when (bucket) {
                ResultBucket.LIVE -> prev.copy(liveRecords = prev.liveRecords.filterNot { it.id == recordId })
                ResultBucket.DIE -> prev.copy(dieRecords = prev.dieRecords.filterNot { it.id == recordId })
                ResultBucket.UNKNOWN -> prev.copy(unknownRecords = prev.unknownRecords.filterNot { it.id == recordId })
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application)
                NetworkClientProvider.init(app.applicationContext)
                val prefsRepo = ValidatorPreferencesRepository(app.applicationContext)
                val db = AppDatabase.getInstance(app.applicationContext)
                BinLookupService.attachDao(db.binCacheDao())
                val historyRepo = ValidationHistoryRepository(db.validationHistoryDao())
                val workManager = WorkManager.getInstance(app.applicationContext)
                ValidatorViewModel(app, prefsRepo, historyRepo, workManager)
            }
        }
    }
}
