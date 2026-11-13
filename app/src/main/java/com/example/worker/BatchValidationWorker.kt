package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.ValidationHistoryRepository
import com.example.domain.CardValidatorEngine
import com.example.domain.ResultBucket
import com.example.domain.ValidationMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

class BatchValidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val batchId = inputData.getString(KEY_BATCH_ID) ?: return@withContext Result.failure()
        val modeName = inputData.getString(KEY_VALIDATION_MODE) ?: ValidationMode.LOCAL_DETERMINISTIC.name
        val mode = runCatching { ValidationMode.valueOf(modeName) }
            .getOrDefault(ValidationMode.LOCAL_DETERMINISTIC)
        val webhookUrl = inputData.getString(KEY_WEBHOOK_URL).orEmpty()
        val autoResolveWildcards = inputData.getBoolean(KEY_AUTO_RESOLVE_WILDCARDS, true)
        val concurrency = inputData.getInt(KEY_CONCURRENCY, 4).coerceIn(1, 8)
        val batchDelayMs = inputData.getLong(KEY_BATCH_DELAY_MS, 0L).coerceIn(0L, 2000L)
        val sourceName = inputData.getString(KEY_SOURCE_NAME).orEmpty().ifBlank { "Console" }

        val database = AppDatabase.getInstance(applicationContext)
        val repository = ValidationHistoryRepository(database.validationHistoryDao())

        val queueItems = repository.getQueuedBatch(batchId)
        if (queueItems.isEmpty()) {
            return@withContext Result.success(
                workDataOf(
                    KEY_BATCH_ID to batchId,
                    KEY_PROCESSED to 0,
                    KEY_TOTAL to 0
                )
            )
        }

        val total = queueItems.size
        val processedCounter = AtomicInteger(0)
        val liveCounter = AtomicInteger(0)
        val dieCounter = AtomicInteger(0)
        val unknownCounter = AtomicInteger(0)
        val semaphore = Semaphore(permits = concurrency)
        val endpointLabel = if (mode == ValidationMode.REMOTE_WEBHOOK && webhookUrl.isNotBlank()) {
            webhookUrl
        } else {
            "Local Luhn Mod-10 Engine"
        }

        setProgress(
            workDataOf(
                KEY_BATCH_ID to batchId,
                KEY_PROCESSED to 0,
                KEY_TOTAL to total,
                KEY_LIVE to 0,
                KEY_DIE to 0,
                KEY_UNKNOWN to 0
            )
        )

        try {
            coroutineScope {
                queueItems.map { item ->
                    async {
                        semaphore.withPermit {
                            val record = CardValidatorEngine.validateLine(
                                rawLine = item.rawLine,
                                mode = mode,
                                webhookUrl = webhookUrl,
                                autoResolveWildcards = autoResolveWildcards
                            )

                            repository.recordResult(
                                batchId = batchId,
                                sourceName = item.sourceName.ifBlank { sourceName },
                                record = record,
                                endpointUsed = endpointLabel
                            )

                            when (record.bucket) {
                                ResultBucket.LIVE -> liveCounter.incrementAndGet()
                                ResultBucket.DIE -> dieCounter.incrementAndGet()
                                ResultBucket.UNKNOWN -> unknownCounter.incrementAndGet()
                            }

                            val done = processedCounter.incrementAndGet()
                            setProgress(
                                workDataOf(
                                    KEY_BATCH_ID to batchId,
                                    KEY_PROCESSED to done,
                                    KEY_TOTAL to total,
                                    KEY_LIVE to liveCounter.get(),
                                    KEY_DIE to dieCounter.get(),
                                    KEY_UNKNOWN to unknownCounter.get()
                                )
                            )

                            if (batchDelayMs > 0L) {
                                delay(batchDelayMs)
                            }
                        }
                    }
                }.awaitAll()
            }

            Result.success(
                workDataOf(
                    KEY_BATCH_ID to batchId,
                    KEY_PROCESSED to processedCounter.get(),
                    KEY_TOTAL to total,
                    KEY_LIVE to liveCounter.get(),
                    KEY_DIE to dieCounter.get(),
                    KEY_UNKNOWN to unknownCounter.get()
                )
            )
        } finally {
            repository.clearQueuedBatch(batchId)
        }
    }

    companion object {
        const val UNIQUE_BATCH_WORK_NAME = "bincheck_concurrent_batch_validation"

        const val KEY_BATCH_ID = "batch_id"
        const val KEY_VALIDATION_MODE = "validation_mode"
        const val KEY_WEBHOOK_URL = "webhook_url"
        const val KEY_AUTO_RESOLVE_WILDCARDS = "auto_resolve_wildcards"
        const val KEY_CONCURRENCY = "concurrency"
        const val KEY_BATCH_DELAY_MS = "batch_delay_ms"
        const val KEY_SOURCE_NAME = "source_name"

        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_LIVE = "live"
        const val KEY_DIE = "die"
        const val KEY_UNKNOWN = "unknown"
    }
}
