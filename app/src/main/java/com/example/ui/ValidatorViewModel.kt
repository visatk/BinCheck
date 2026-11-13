package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.ExistingWorkPolicy
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
import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.FileTextExtractor
import com.example.domain.ImportedFilePreview
import com.example.domain.ResultBucket
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import com.example.worker.BatchValidationWorker
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class HistoryUiSummary(
    val totalCount: Int = 0,
    val successCount: Int = 0,
    val failureCount: Int = 0
)

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
    val historyFilter: HistoryFilter = HistoryFilter.ALL,
    val historySearchQuery: String = "",
    val historyEntries: List<ValidationHistoryEntity> = emptyList(),
    val historySummary: HistoryUiSummary = HistoryUiSummary(),
    val isImportingFile: Boolean = false,
    val fileImportError: String? = null,
    val importedFilePreview: ImportedFilePreview? = null,
    val loadedFileName: String? = null,
    val settings: ValidatorSettings = ValidatorSettings(),
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

@OptIn(ExperimentalCoroutinesApi::class)
class ValidatorViewModel(
    application: Application,
    private val preferencesRepository: ValidatorPreferencesRepository,
    private val historyRepository: ValidationHistoryRepository,
    private val workManager: WorkManager
) : AndroidViewModel(application) {

    private val _localState = MutableStateFlow(ValidatorUiState())
    private val _historyFilter = MutableStateFlow(HistoryFilter.ALL)
    private val _historyQuery = MutableStateFlow("")

    private var activeWorkId: UUID? = null
    private var workerObserverJob: Job? = null
    private var batchResultsObserverJob: Job? = null

    init {
        reconnectOngoingBatchWork()
    }

    private val filteredHistoryFlow = _historyFilter
        .flatMapLatest { filter -> historyRepository.observeHistory(filter) }
        .combine(_historyQuery) { entries, query ->
            val q = query.trim().lowercase()
            if (q.isEmpty()) {
                entries
            } else {
                entries.filter {
                    it.normalizedLine.lowercase().contains(q) ||
                        it.scheme.lowercase().contains(q) ||
                        it.sourceName.lowercase().contains(q) ||
                        it.message.lowercase().contains(q)
                }
            }
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
        filteredHistoryFlow,
        historySummaryFlow,
        combine(_historyFilter, _historyQuery) { f, q -> f to q }
    ) { state, prefs, historyList, summary, filterPair ->
        state.copy(
            settings = prefs,
            historyEntries = historyList,
            historySummary = summary,
            historyFilter = filterPair.first,
            historySearchQuery = filterPair.second
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ValidatorUiState()
    )

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
        _localState.update { it.copy(isSettingsOpen = true, statusBannerMessage = null) }
    }

    fun closeSettings() {
        _localState.update { it.copy(isSettingsOpen = false) }
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
        _localState.update { it.copy(isHistoryOpen = true, statusBannerMessage = null) }
    }

    fun closeHistory() {
        _localState.update { it.copy(isHistoryOpen = false) }
    }

    fun setHistoryFilter(filter: HistoryFilter) {
        _historyFilter.value = filter
    }

    fun setHistorySearchQuery(query: String) {
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
        val batchId = UUID.randomUUID().toString()
        val sourceName = currentState.loadedFileName ?: "Console"

        viewModelScope.launch {
            historyRepository.enqueueBatch(
                batchId = batchId,
                lines = lines,
                sourceName = sourceName
            )

            val inputData = workDataOf(
                BatchValidationWorker.KEY_BATCH_ID to batchId,
                BatchValidationWorker.KEY_VALIDATION_MODE to settings.validationMode.name,
                BatchValidationWorker.KEY_WEBHOOK_URL to settings.webhookUrl,
                BatchValidationWorker.KEY_AUTO_RESOLVE_WILDCARDS to settings.autoResolveWildcards,
                BatchValidationWorker.KEY_CONCURRENCY to settings.concurrency,
                BatchValidationWorker.KEY_BATCH_DELAY_MS to settings.batchDelayMs,
                BatchValidationWorker.KEY_SOURCE_NAME to sourceName
            )

            val workRequest = OneTimeWorkRequestBuilder<BatchValidationWorker>()
                .setInputData(inputData)
                .addTag("batch:$batchId")
                .build()

            activeWorkId = workRequest.id

            _localState.update {
                it.copy(
                    isRunning = true,
                    processedCount = 0,
                    totalBatchCount = lines.size,
                    statusBannerMessage = "Worker running (${settings.concurrency}x concurrent)..."
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

        // Delta-stream completed records from Room without overwriting user removals
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
                                    processedCount = processedFromProgress.coerceAtMost(resolvedTotal.coerceAtLeast(processedFromProgress))
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
        webhookUrl: String,
        autoResolveWildcards: Boolean,
        batchDelayMs: Long,
        concurrency: Int,
        patternTemplate: String,
        generateQuantity: Int,
        themeMode: AppThemeMode,
        useDynamicColor: Boolean
    ) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = validationMode,
                webhookUrl = webhookUrl,
                autoResolveWildcards = autoResolveWildcards,
                batchDelayMs = batchDelayMs,
                concurrency = concurrency,
                patternTemplate = patternTemplate,
                generateQuantity = generateQuantity,
                themeMode = themeMode,
                useDynamicColor = useDynamicColor
            )
            _localState.update {
                it.copy(
                    isSettingsOpen = false,
                    statusBannerMessage = "Configuration saved (${concurrency}x worker concurrency)"
                )
            }
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
                val prefsRepo = ValidatorPreferencesRepository(app.applicationContext)
                val db = AppDatabase.getInstance(app.applicationContext)
                val historyRepo = ValidationHistoryRepository(db.validationHistoryDao())
                val workManager = WorkManager.getInstance(app.applicationContext)
                ValidatorViewModel(app, prefsRepo, historyRepo, workManager)
            }
        }
    }
}
