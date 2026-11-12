package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ValidatorPreferencesRepository
import com.example.data.ValidatorSettings
import com.example.domain.CardValidatorEngine
import com.example.domain.ResultBucket
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

class ValidatorViewModel(
    application: Application,
    private val preferencesRepository: ValidatorPreferencesRepository
) : AndroidViewModel(application) {

    private val _localState = MutableStateFlow(ValidatorUiState())
    private var batchJob: Job? = null

    val uiState: StateFlow<ValidatorUiState> = combine(
        _localState,
        preferencesRepository.settingsFlow
    ) { state, prefs ->
        state.copy(settings = prefs)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ValidatorUiState()
    )

    fun onInputTextChanged(newText: String) {
        _localState.update {
            it.copy(
                rawInputText = newText,
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
        batchJob?.cancel()
        batchJob = null
        _localState.update {
            it.copy(
                isRunning = false,
                statusBannerMessage = "Batch paused (${it.processedCount}/${it.totalBatchCount} processed)"
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
                    statusBannerMessage = "Console is empty. Paste records or tap ⚙ to expand a BIN pattern."
                )
            }
            return
        }

        batchJob?.cancel()
        batchJob = viewModelScope.launch {
            // If a line contains 'x' wildcards and autoResolveWildcards is true, resolve it in-place in the console too
            val settings = uiState.value.settings
            _localState.update {
                it.copy(
                    isRunning = true,
                    processedCount = 0,
                    totalBatchCount = lines.size,
                    statusBannerMessage = null
                )
            }

            val remainingLines = lines.toMutableList()

            for ((index, line) in lines.withIndex()) {
                val record = CardValidatorEngine.validateLine(
                    rawLine = line,
                    mode = settings.validationMode,
                    webhookUrl = settings.webhookUrl,
                    autoResolveWildcards = settings.autoResolveWildcards
                )

                remainingLines.removeAt(0)

                _localState.update { prev ->
                    val updatedLive = if (record.bucket == ResultBucket.LIVE) {
                        listOf(record) + prev.liveRecords
                    } else {
                        prev.liveRecords
                    }
                    val updatedDie = if (record.bucket == ResultBucket.DIE) {
                        listOf(record) + prev.dieRecords
                    } else {
                        prev.dieRecords
                    }
                    val updatedUnknown = if (record.bucket == ResultBucket.UNKNOWN) {
                        listOf(record) + prev.unknownRecords
                    } else {
                        prev.unknownRecords
                    }

                    prev.copy(
                        rawInputText = remainingLines.joinToString("\n"),
                        processedCount = index + 1,
                        liveRecords = updatedLive,
                        dieRecords = updatedDie,
                        unknownRecords = updatedUnknown
                    )
                }

                if (settings.batchDelayMs > 0 && index < lines.lastIndex) {
                    delay(settings.batchDelayMs)
                }
            }

            _localState.update {
                it.copy(
                    isRunning = false,
                    statusBannerMessage = "Completed ${lines.size} item(s)"
                )
            }
        }
    }

    /**
     * Expands wildcards in the current console input or generates a batch from the pattern template.
     */
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
        patternTemplate: String,
        generateQuantity: Int
    ) {
        viewModelScope.launch {
            preferencesRepository.updateSettings(
                validationMode = validationMode,
                webhookUrl = webhookUrl,
                autoResolveWildcards = autoResolveWildcards,
                batchDelayMs = batchDelayMs,
                patternTemplate = patternTemplate,
                generateQuantity = generateQuantity
            )
            _localState.update {
                it.copy(
                    isSettingsOpen = false,
                    statusBannerMessage = "Configuration saved"
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
                statusBannerMessage = "Cleared all result buckets"
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
                val repo = ValidatorPreferencesRepository(app.applicationContext)
                ValidatorViewModel(app, repo)
            }
        }
    }
}
