package com.example.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.MainActivity
import com.example.R
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
import com.example.domain.TtlLruCache
import com.example.domain.ValidationMode
import com.example.domain.ValidationRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
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

        // Bounded LRU cache prevents unbounded memory growth on huge files while deduplicating concrete lines
        val batchConcreteCache = TtlLruCache<String, ValidationRecord>(maxEntries = BATCH_CACHE_MAX_ENTRIES)
        val batchLineDeduplicator = RequestDeduplicator<String, ValidationRecord>()

        // Buffered DB history writes for high-throughput (< 2s delay or concurrent) runs
        val useBatchedDbWrites = effectiveDelayMs < 2_000L || effectiveConcurrency > 1
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

        /** Returns the validated record alongside whether it came from local batch dedup/cache. */
        suspend fun validateSingleLine(rawLine: String): Pair<ValidationRecord, Boolean> {
            val trimmed = rawLine.trim()
            val hasWildcard = trimmed.any { it.equals('x', ignoreCase = true) }
            if (!deduplicateInputs || hasWildcard) {
                val record = CardValidatorEngine.validateLine(
                    rawLine = trimmed,
                    mode = mode,
                    webhookUrl = webhookUrl,
                    autoResolveWildcards = autoResolveWildcards,
                    customHttpConfig = customHttpConfig,
                    useCacheAndDedup = deduplicateInputs
                )
                return record to false
            }

            batchConcreteCache.get(trimmed)?.let { cached ->
                NetworkClientProvider.telemetry.recordDeduplicatedSaved()
                val dedupRecord = cached.copy(
                    id = "${System.nanoTime()}_${trimmed.hashCode()}",
                    latencyMs = 0L,
                    message = if (cached.message.contains("(Dedup)")) cached.message else "${cached.message} (Dedup)"
                )
                return dedupRecord to true
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
                    batchConcreteCache.put(trimmed, computed, BATCH_CACHE_TTL_MS)
                }
                computed
            }
            if (wasCoalesced) {
                NetworkClientProvider.telemetry.recordDeduplicatedSaved()
            }
            return result to wasCoalesced
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
            val shouldUpdate = force || done == total ||
                (now - prev >= PROGRESS_MIN_INTERVAL_MS && lastProgressTime.compareAndSet(prev, now))
            if (shouldUpdate) {
                if (force || done == total) {
                    lastProgressTime.set(now)
                }
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

        suspend fun processQueueItem(
            item: com.example.data.local.BatchQueueItemEntity,
            forceProgress: Boolean = false
        ): Boolean {
            val (record, wasDedupHit) = validateSingleLine(item.rawLine)

            persistRecord(item.sourceName, record)

            when (record.bucket) {
                ResultBucket.LIVE -> liveCounter.incrementAndGet()
                ResultBucket.DIE -> dieCounter.incrementAndGet()
                ResultBucket.UNKNOWN -> unknownCounter.incrementAndGet()
            }

            handleTelegramDispatch(record)

            val done = processedCounter.incrementAndGet()
            maybePublishProgress(done, force = forceProgress)
            return wasDedupHit
        }

        try {
            val workerCount = effectiveConcurrency.coerceAtMost(total).coerceAtLeast(1)
            if (workerCount == 1) {
                for ((index, item) in queueItems.withIndex()) {
                    if (isStopped) break
                    val wasDedupHit = processQueueItem(item, forceProgress = index == queueItems.lastIndex)
                    if (!wasDedupHit && index < queueItems.lastIndex && effectiveDelayMs > 0L && !isStopped) {
                        delay(effectiveDelayMs)
                    }
                }
            } else {
                // Bounded worker pool avoids allocating up to thousands of suspended async coroutines up front
                val nextIndex = AtomicInteger(0)
                coroutineScope {
                    List(workerCount) {
                        launch {
                            while (!isStopped) {
                                val index = nextIndex.getAndIncrement()
                                if (index >= total) break
                                val wasDedupHit = processQueueItem(queueItems[index])
                                if (!wasDedupHit && effectiveDelayMs > 0L && !isStopped && nextIndex.get() < total) {
                                    delay(effectiveDelayMs)
                                }
                            }
                        }
                    }.joinAll()
                }
                maybePublishProgress(processedCounter.get(), force = true)
            }

            flushRemainingHistory()

            if (shouldNotifyTelegram && !isStopped) {
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

            val processedTotal = processedCounter.get()
            if (!isStopped && processedTotal > 0) {
                runCatching {
                    postCompletionNotification(
                        processed = processedTotal,
                        total = total,
                        live = liveCounter.get(),
                        die = dieCounter.get(),
                        unknown = unknownCounter.get()
                    )
                }
            }

            Result.success(
                workDataOf(
                    KEY_BATCH_ID to batchId,
                    KEY_PROCESSED to processedTotal,
                    KEY_TOTAL to total,
                    KEY_LIVE to liveCounter.get(),
                    KEY_DIE to dieCounter.get(),
                    KEY_UNKNOWN to unknownCounter.get()
                )
            )
        } catch (ce: CancellationException) {
            throw ce
        } finally {
            withContext(NonCancellable) {
                runCatching { flushRemainingHistory() }
                runCatching { repository.clearQueuedBatch(batchId) }
            }
        }
    }

    private fun postCompletionNotification(
        processed: Int,
        total: Int,
        live: Int,
        die: Int,
        unknown: Int
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID_BATCH_RESULTS,
                applicationContext.getString(R.string.batch_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = applicationContext.getString(R.string.batch_notification_channel_desc)
            }
            val sysManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            sysManager?.createNotificationChannel(channel)
        }

        val launchIntent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            1001,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (live > 0) {
            "Check finished • $live live found"
        } else {
            "Check finished • $processed of $total checked"
        }
        val body = "$live Live • $die Die • $unknown Unknown"

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID_BATCH_RESULTS)
            .setSmallIcon(R.drawable.ic_stat_bincheck)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_BATCH_DONE, notification)
    }

    companion object {
        const val UNIQUE_BATCH_WORK_NAME = "bincheck_concurrent_batch_validation"
        private const val CHANNEL_ID_BATCH_RESULTS = "bincheck_batch_results"
        private const val NOTIFICATION_ID_BATCH_DONE = 2048
        private const val PROGRESS_MIN_INTERVAL_MS = 250L
        private const val DB_BATCH_FLUSH_SIZE = 50
        private const val BATCH_CACHE_MAX_ENTRIES = 2_500
        private val BATCH_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(15)
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
