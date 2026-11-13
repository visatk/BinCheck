package com.example.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.data.local.AppDatabase
import com.example.data.local.ValidationHistoryRepository
import com.example.domain.BinLookupService
import com.example.domain.CardValidatorEngine
import com.example.domain.CustomHttpConfig
import com.example.domain.HttpRequestMethod
import com.example.domain.NetworkClientProvider
import com.example.domain.RequestDeduplicator
import com.example.domain.ResultBucket
import com.example.domain.TelegramWebhookClient
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class BatchValidationWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        NetworkClientProvider.init(applicationContext)
        val database = AppDatabase.getInstance(applicationContext)
        BinLookupService.attachDao(database.binCacheDao())

        val batchId = inputData.getString(KEY_BATCH_ID) ?: return@withContext Result.failure()
        val modeName = inputData.getString(KEY_VALIDATION_MODE) ?: ValidationMode.LOCAL_DETERMINISTIC.name
        val mode = runCatching { ValidationMode.valueOf(modeName) }
            .getOrDefault(ValidationMode.LOCAL_DETERMINISTIC)
        val webhookUrl = inputData.getString(KEY_WEBHOOK_URL).orEmpty()
        val httpMethodName = inputData.getString(KEY_HTTP_METHOD) ?: HttpRequestMethod.AUTO.name
        val httpMethod = runCatching { HttpRequestMethod.valueOf(httpMethodName) }
            .getOrDefault(HttpRequestMethod.AUTO)
        val httpHeaders = inputData.getString(KEY_HTTP_HEADERS).orEmpty()
        val httpBodyTemplate = inputData.getString(KEY_HTTP_BODY_TEMPLATE).orEmpty()
        val httpTimeoutSeconds = inputData.getInt(KEY_HTTP_TIMEOUT_SECONDS, 12).coerceIn(5, 60)
        val httpMaxRetries = inputData.getInt(KEY_HTTP_MAX_RETRIES, 1).coerceIn(0, 3)
        val httpLiveKeywords = inputData.getString(KEY_HTTP_LIVE_KEYWORDS)
            ?.ifBlank { CustomHttpConfig.DEFAULT_LIVE_KEYWORDS }
            ?: CustomHttpConfig.DEFAULT_LIVE_KEYWORDS
        val httpDieKeywords = inputData.getString(KEY_HTTP_DIE_KEYWORDS)
            ?.ifBlank { CustomHttpConfig.DEFAULT_DIE_KEYWORDS }
            ?: CustomHttpConfig.DEFAULT_DIE_KEYWORDS
        val gzipRequests = inputData.getBoolean(KEY_GZIP_REQUESTS, false)
        val customHttpConfig = CustomHttpConfig(
            url = webhookUrl,
            method = httpMethod,
            headersText = httpHeaders,
            bodyTemplate = httpBodyTemplate,
            timeoutSeconds = httpTimeoutSeconds,
            maxRetries = httpMaxRetries,
            liveKeywords = httpLiveKeywords,
            dieKeywords = httpDieKeywords,
            compressBodyGzip = gzipRequests
        )

        val autoResolveWildcards = inputData.getBoolean(KEY_AUTO_RESOLVE_WILDCARDS, true)
        val deduplicateInputs = inputData.getBoolean(KEY_DEDUPLICATE_INPUTS, true)
        val concurrency = inputData.getInt(KEY_CONCURRENCY, 4).coerceIn(1, 8)
        val userDelayMs = inputData.getLong(KEY_BATCH_DELAY_MS, 0L)
            .coerceIn(0L, ValidationMode.MAX_CUSTOM_DELAY_MS)
        val effectiveDelayMs = mode.resolveDelayMs(userDelayMs)
        val effectiveConcurrency = if (mode.hasFixedDelay) 1 else concurrency
        val sourceName = inputData.getString(KEY_SOURCE_NAME).orEmpty().ifBlank { "Console" }
        val telegramEnabled = inputData.getBoolean(KEY_TELEGRAM_ENABLED, false)
        val telegramChatId = inputData.getString(KEY_TELEGRAM_CHAT_ID).orEmpty().trim()
        val telegramLiveOnly = inputData.getBoolean(KEY_TELEGRAM_LIVE_ONLY, true)
        val shouldNotifyTelegram =
            telegramEnabled && telegramChatId.isNotEmpty() && TelegramWebhookClient.hasConfiguredToken()
        val useTelegramDigestBatching = !mode.hasFixedDelay && (effectiveConcurrency > 1 || effectiveDelayMs < 1_000L)

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
        val lastProgressTime = AtomicLong(0L)
        val semaphore = Semaphore(permits = effectiveConcurrency)

        // Batch-level concrete line deduplicator (saves duplicate server requests in high-traffic files)
        val batchConcreteCache = ConcurrentHashMap<String, ValidationRecord>()
        val batchLineDeduplicator = RequestDeduplicator<String, ValidationRecord>()

        // Buffered DB history writes for high-throughput (< 1s delay) runs
        val useBatchedDbWrites = effectiveDelayMs < 1_000L
        val historyWriteBuffer = mutableListOf<com.example.data.local.ValidationHistoryEntity>()
        val historyBufferMutex = Mutex()

        // Buffered Telegram alert digest queue for high-throughput batches
        val telegramBuffer = mutableListOf<ValidationRecord>()
        val telegramBufferMutex = Mutex()

        val endpointLabel = when (mode) {
            ValidationMode.LOCAL_DETERMINISTIC -> "Local Luhn Mod-10 Engine"
            ValidationMode.CHKR_CC -> "Chkr.cc API (api.chkr.cc)"
            ValidationMode.PAYATE_MOCK -> "Payate API (mock.payate.com)"
            ValidationMode.MULTI_API_AUTO -> "Multi-API Failover (Chkr.cc + Payate)"
            ValidationMode.REMOTE_WEBHOOK -> if (webhookUrl.isNotBlank()) webhookUrl else "Custom HTTP"
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

        suspend fun validateSingleLine(rawLine: String): ValidationRecord {
            val trimmed = rawLine.trim()
            val hasWildcard = trimmed.any { it.equals('x', ignoreCase = true) }
            if (!deduplicateInputs || hasWildcard) {
                return CardValidatorEngine.validateLine(
                    rawLine = trimmed,
                    mode = mode,
                    webhookUrl = webhookUrl,
                    autoResolveWildcards = autoResolveWildcards,
                    customHttpConfig = customHttpConfig,
                    useCacheAndDedup = deduplicateInputs
                )
            }

            batchConcreteCache[trimmed]?.let { cached ->
                NetworkClientProvider.telemetry.recordDeduplicatedSaved()
                return cached.copy(
                    id = "${System.nanoTime()}_${trimmed.hashCode()}",
                    latencyMs = 0L,
                    message = if (cached.message.contains("(Dedup)")) cached.message else "${cached.message} (Dedup)"
                )
            }

            val (result, wasCoalesced) = batchLineDeduplicator.getOrExecute(trimmed) {
                val computed = CardValidatorEngine.validateLine(
                    rawLine = trimmed,
                    mode = mode,
                    webhookUrl = webhookUrl,
                    autoResolveWildcards = autoResolveWildcards,
                    customHttpConfig = customHttpConfig,
                    useCacheAndDedup = true
                )
                if (computed.bucket != ResultBucket.UNKNOWN) {
                    batchConcreteCache[trimmed] = computed
                }
                computed
            }
            if (wasCoalesced) {
                NetworkClientProvider.telemetry.recordDeduplicatedSaved()
            }
            return result
        }

        suspend fun persistRecord(itemSourceName: String, record: ValidationRecord) {
            val effectiveSource = itemSourceName.ifBlank { sourceName }
            if (!useBatchedDbWrites) {
                repository.recordResult(
                    batchId = batchId,
                    sourceName = effectiveSource,
                    record = record,
                    endpointUsed = endpointLabel
                )
                return
            }
            val entity = repository.toEntity(
                batchId = batchId,
                sourceName = effectiveSource,
                record = record,
                endpointUsed = endpointLabel
            )
            val flushChunk = historyBufferMutex.withLock {
                historyWriteBuffer.add(entity)
                if (historyWriteBuffer.size >= DB_BATCH_FLUSH_SIZE) {
                    historyWriteBuffer.toList().also { historyWriteBuffer.clear() }
                } else {
                    null
                }
            }
            if (!flushChunk.isNullOrEmpty()) {
                repository.recordResultBatch(flushChunk)
            }
        }

        suspend fun flushRemainingHistory() {
            if (!useBatchedDbWrites) return
            val remaining = historyBufferMutex.withLock {
                historyWriteBuffer.toList().also { historyWriteBuffer.clear() }
            }
            if (remaining.isNotEmpty()) {
                repository.recordResultBatch(remaining)
            }
        }

        suspend fun handleTelegramDispatch(record: ValidationRecord) {
            if (!shouldNotifyTelegram) return
            if (telegramLiveOnly && record.bucket != ResultBucket.LIVE) return

            if (!useTelegramDigestBatching) {
                val binInfo = BinLookupService.getCached(record.pan)
                TelegramWebhookClient.sendValidationNotification(
                    record = record,
                    engineLabel = mode.badgeLabel,
                    chatId = telegramChatId,
                    binInfo = binInfo
                )
            } else {
                val flushList: List<ValidationRecord>? = telegramBufferMutex.withLock {
                    telegramBuffer.add(record)
                    if (telegramBuffer.size >= TELEGRAM_DIGEST_CHUNK_SIZE) {
                        telegramBuffer.toList().also { telegramBuffer.clear() }
                    } else {
                        null
                    }
                }
                if (!flushList.isNullOrEmpty()) {
                    TelegramWebhookClient.sendBatchDigest(
                        records = flushList,
                        engineLabel = mode.badgeLabel,
                        chatId = telegramChatId
                    )
                }
            }
        }

        suspend fun maybePublishProgress(done: Int, force: Boolean = false) {
            val now = System.currentTimeMillis()
            val prev = lastProgressTime.get()
            val isIntervalHit = (done % PROGRESS_ITEM_STRIDE == 0) || (now - prev >= PROGRESS_MIN_INTERVAL_MS)
            if (force || done == total || isIntervalHit) {
                lastProgressTime.set(now)
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
            }
        }

        try {
            if (effectiveConcurrency == 1) {
                for ((index, item) in queueItems.withIndex()) {
                    if (isStopped) break
                    val record = validateSingleLine(item.rawLine)

                    persistRecord(item.sourceName, record)

                    when (record.bucket) {
                        ResultBucket.LIVE -> liveCounter.incrementAndGet()
                        ResultBucket.DIE -> dieCounter.incrementAndGet()
                        ResultBucket.UNKNOWN -> unknownCounter.incrementAndGet()
                    }

                    handleTelegramDispatch(record)

                    val done = processedCounter.incrementAndGet()
                    maybePublishProgress(done, force = index == queueItems.lastIndex)

                    if (index < queueItems.lastIndex && effectiveDelayMs > 0L && !isStopped) {
                        delay(effectiveDelayMs)
                    }
                }
            } else {
                coroutineScope {
                    queueItems.mapIndexed { index, item ->
                        async {
                            semaphore.withPermit {
                                if (isStopped) return@withPermit
                                val record = validateSingleLine(item.rawLine)

                                persistRecord(item.sourceName, record)

                                when (record.bucket) {
                                    ResultBucket.LIVE -> liveCounter.incrementAndGet()
                                    ResultBucket.DIE -> dieCounter.incrementAndGet()
                                    ResultBucket.UNKNOWN -> unknownCounter.incrementAndGet()
                                }

                                handleTelegramDispatch(record)

                                val done = processedCounter.incrementAndGet()
                                maybePublishProgress(done)

                                if (index < queueItems.lastIndex && effectiveDelayMs > 0L && !isStopped) {
                                    delay(effectiveDelayMs)
                                }
                            }
                        }
                    }.awaitAll()
                }
                maybePublishProgress(processedCounter.get(), force = true)
            }

            flushRemainingHistory()

            if (shouldNotifyTelegram) {
                val remaining = telegramBufferMutex.withLock {
                    telegramBuffer.toList().also { telegramBuffer.clear() }
                }
                if (remaining.isNotEmpty()) {
                    TelegramWebhookClient.sendBatchDigest(
                        records = remaining,
                        engineLabel = mode.badgeLabel,
                        chatId = telegramChatId
                    )
                }
            }

            runCatching { repository.trimExcessHistory() }

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
        private const val PROGRESS_ITEM_STRIDE = 4
        private const val PROGRESS_MIN_INTERVAL_MS = 250L
        private const val DB_BATCH_FLUSH_SIZE = 15
        private const val TELEGRAM_DIGEST_CHUNK_SIZE = 8

        const val KEY_BATCH_ID = "batch_id"
        const val KEY_VALIDATION_MODE = "validation_mode"
        const val KEY_WEBHOOK_URL = "webhook_url"
        const val KEY_HTTP_METHOD = "http_method"
        const val KEY_HTTP_HEADERS = "http_headers"
        const val KEY_HTTP_BODY_TEMPLATE = "http_body_template"
        const val KEY_HTTP_TIMEOUT_SECONDS = "http_timeout_seconds"
        const val KEY_HTTP_MAX_RETRIES = "http_max_retries"
        const val KEY_HTTP_LIVE_KEYWORDS = "http_live_keywords"
        const val KEY_HTTP_DIE_KEYWORDS = "http_die_keywords"
        const val KEY_GZIP_REQUESTS = "gzip_requests"
        const val KEY_DEDUPLICATE_INPUTS = "deduplicate_inputs"
        const val KEY_AUTO_RESOLVE_WILDCARDS = "auto_resolve_wildcards"
        const val KEY_CONCURRENCY = "concurrency"
        const val KEY_BATCH_DELAY_MS = "batch_delay_ms"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_TELEGRAM_ENABLED = "telegram_enabled"
        const val KEY_TELEGRAM_CHAT_ID = "telegram_chat_id"
        const val KEY_TELEGRAM_LIVE_ONLY = "telegram_live_only"

        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        const val KEY_LIVE = "live"
        const val KEY_DIE = "die"
        const val KEY_UNKNOWN = "unknown"
    }
}
