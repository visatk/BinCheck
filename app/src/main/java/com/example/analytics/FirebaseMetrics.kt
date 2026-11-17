package com.example.analytics

import android.content.Context
import android.os.Bundle
import com.example.data.ValidatorSettings
import com.example.domain.NetworkClientProvider
import com.example.domain.NetworkHttpMetricListener
import com.example.domain.ValidationMode
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.perf.FirebasePerformance
import com.google.firebase.perf.metrics.Trace
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Lightweight trace handle abstracting Firebase Performance Monitoring [Trace]
 * so worker/domain execution stays testable without Android runtime dependencies.
 */
interface PerformanceTraceHandle {
    fun putAttribute(name: String, value: String)
    fun incrementMetric(name: String, by: Long = 1L)
    fun stop()
}

object NoOpTraceHandle : PerformanceTraceHandle {
    override fun putAttribute(name: String, value: String) = Unit
    override fun incrementMetric(name: String, by: Long) = Unit
    override fun stop() = Unit
}

/**
 * Pluggable telemetry sink allowing production Firebase dispatch and deterministic
 * in-memory capture during unit and Robolectric tests.
 */
interface MetricsSink {
    fun setCollectionEnabled(enabled: Boolean)
    fun logScreenView(screenName: String, screenClass: String)
    fun logEvent(name: String, params: Map<String, Any?>)
    fun setUserProperty(name: String, value: String)
    fun setCustomKey(key: String, value: String)
    fun logBreadcrumb(message: String)
    fun recordNonFatal(tag: String, throwable: Throwable, context: Map<String, String>)
    fun startTrace(name: String): PerformanceTraceHandle
    fun recordHttpMetric(
        sanitizedUrl: String,
        method: String,
        statusCode: Int,
        latencyMs: Long,
        responseBytes: Long
    )
}

/**
 * Strictly scrubs cardholder-like digit sequences, query parameters, auth headers,
 * and Telegram bot tokens before any telemetry leaves the device.
 */
object TelemetrySanitizer {

    private val PAN_SEQUENCE_REGEX = Regex("""\b[\dxX]{12,19}\b""")
    private val CARD_TUPLE_REGEX = Regex("""[\dxX]{12,19}[|/:]\d{1,2}[|/:]\d{2,4}[|/:]\d{3,4}""")
    private val TELEGRAM_TOKEN_REGEX = Regex("""bot\d+:[A-Za-z0-9_-]+""")
    private val BEARER_TOKEN_REGEX = Regex("""(?i)\bBearer\s+[A-Za-z0-9._\-+/=]+""")
    private val NUMERIC_PATH_ID_REGEX = Regex("""/\d{6,}(?=/|$)""")

    fun sanitizeName(raw: String, maxLen: Int = 40): String {
        val normalized = buildString {
            for (ch in raw.lowercase(Locale.US)) {
                append(if (ch in 'a'..'z' || ch in '0'..'9' || ch == '_') ch else '_')
            }
        }.trim('_').replace(Regex("_+"), "_")
        val prefixed = when {
            normalized.isEmpty() -> "metric_event"
            normalized.first() !in 'a'..'z' -> "m_$normalized"
            else -> normalized
        }
        return prefixed.take(maxLen)
    }

    fun sanitizeValue(raw: String, maxLen: Int = 100): String {
        return raw
            .replace(CARD_TUPLE_REGEX, "[REDACTED_RECORD]")
            .replace(PAN_SEQUENCE_REGEX, "[REDACTED_PAN]")
            .replace(TELEGRAM_TOKEN_REGEX, "bot[REDACTED]")
            .replace(BEARER_TOKEN_REGEX, "Bearer [REDACTED]")
            .trim()
            .take(maxLen)
    }

    fun sanitizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return "https://unknown.invalid/"
        val withoutQuery = trimmed.substringBefore('?').substringBefore('#')
        val tokenScrubbed = withoutQuery
            .replace(TELEGRAM_TOKEN_REGEX, "bot[REDACTED]")
            .replace(NUMERIC_PATH_ID_REGEX, "/{id}")
        return sanitizeValue(tokenScrubbed, maxLen = 180)
    }
}

private class AndroidTraceHandle(private val trace: Trace) : PerformanceTraceHandle {
    private val stopped = AtomicBoolean(false)

    override fun putAttribute(name: String, value: String) {
        if (stopped.get()) return
        runCatching {
            trace.putAttribute(
                TelemetrySanitizer.sanitizeName(name, maxLen = 40),
                TelemetrySanitizer.sanitizeValue(value, maxLen = 100)
            )
        }
    }

    override fun incrementMetric(name: String, by: Long) {
        if (stopped.get() || by == 0L) return
        runCatching {
            trace.incrementMetric(TelemetrySanitizer.sanitizeName(name, maxLen = 100), by)
        }
    }

    override fun stop() {
        if (stopped.compareAndSet(false, true)) {
            runCatching { trace.stop() }
        }
    }
}

/**
 * Production Firebase Analytics, Crashlytics, and Performance Monitoring sink.
 * Uses explicit fully-qualified SDK calls and safe runtime guards so missing Play
 * Services or Robolectric environments never crash application flows.
 */
class AndroidFirebaseMetricsSink(context: Context) : MetricsSink {

    private val appContext: Context = context.applicationContext

    private fun ensureFirebaseReady(): Boolean {
        return runCatching {
            if (FirebaseApp.getApps(appContext).isEmpty()) {
                FirebaseApp.initializeApp(appContext) != null
            } else {
                true
            }
        }.getOrDefault(false)
    }

    private fun analyticsOrNull(): FirebaseAnalytics? {
        if (!ensureFirebaseReady()) return null
        return runCatching { FirebaseAnalytics.getInstance(appContext) }.getOrNull()
    }

    private fun crashlyticsOrNull(): FirebaseCrashlytics? {
        if (!ensureFirebaseReady()) return null
        return runCatching { FirebaseCrashlytics.getInstance() }.getOrNull()
    }

    private fun performanceOrNull(): FirebasePerformance? {
        if (!ensureFirebaseReady()) return null
        return runCatching { FirebasePerformance.getInstance() }.getOrNull()
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runCatching { analyticsOrNull()?.setAnalyticsCollectionEnabled(enabled) }
        runCatching { crashlyticsOrNull()?.isCrashlyticsCollectionEnabled = enabled }
        runCatching { performanceOrNull()?.isPerformanceCollectionEnabled = enabled }
    }

    override fun logScreenView(screenName: String, screenClass: String) {
        val safeName = TelemetrySanitizer.sanitizeValue(screenName, maxLen = 100)
        val safeClass = TelemetrySanitizer.sanitizeValue(screenClass, maxLen = 100)

        val analytics = analyticsOrNull()
        if (analytics != null) {
            runCatching {
                // Log a screen view event
                // https://firebase.google.com/docs/analytics/screenviews#kotlin
                val bundle = Bundle().apply {
                    putString(FirebaseAnalytics.Param.SCREEN_NAME, safeName)
                    putString(FirebaseAnalytics.Param.SCREEN_CLASS, safeClass)
                }
                analytics.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, bundle)
            }
        }

        runCatching {
            crashlyticsOrNull()?.apply {
                setCustomKey("current_screen", safeName)
                log("screen_view:$safeName")
            }
        }
    }

    override fun logEvent(name: String, params: Map<String, Any?>) {
        val analytics = analyticsOrNull() ?: return
        val safeEventName = TelemetrySanitizer.sanitizeName(name, maxLen = 40)
        runCatching {
            val bundle = Bundle()
            var count = 0
            for ((rawKey, rawValue) in params) {
                if (rawValue == null || count >= 25) continue
                val key = TelemetrySanitizer.sanitizeName(rawKey, maxLen = 40)
                when (rawValue) {
                    is Int -> bundle.putLong(key, rawValue.toLong())
                    is Long -> bundle.putLong(key, rawValue)
                    is Float -> bundle.putDouble(key, rawValue.toDouble())
                    is Double -> bundle.putDouble(key, rawValue)
                    is Boolean -> bundle.putString(key, rawValue.toString())
                    else -> bundle.putString(key, TelemetrySanitizer.sanitizeValue(rawValue.toString(), maxLen = 100))
                }
                count++
            }
            analytics.logEvent(safeEventName, bundle)
        }
    }

    override fun setUserProperty(name: String, value: String) {
        val analytics = analyticsOrNull() ?: return
        runCatching {
            analytics.setUserProperty(
                TelemetrySanitizer.sanitizeName(name, maxLen = 24),
                TelemetrySanitizer.sanitizeValue(value, maxLen = 36)
            )
        }
    }

    override fun setCustomKey(key: String, value: String) {
        runCatching {
            crashlyticsOrNull()?.setCustomKey(
                TelemetrySanitizer.sanitizeName(key, maxLen = 40),
                TelemetrySanitizer.sanitizeValue(value, maxLen = 100)
            )
        }
    }

    override fun logBreadcrumb(message: String) {
        runCatching {
            crashlyticsOrNull()?.log(TelemetrySanitizer.sanitizeValue(message, maxLen = 160))
        }
    }

    override fun recordNonFatal(tag: String, throwable: Throwable, context: Map<String, String>) {
        runCatching {
            crashlyticsOrNull()?.apply {
                setCustomKey("error_tag", TelemetrySanitizer.sanitizeValue(tag, maxLen = 64))
                context.forEach { (k, v) ->
                    setCustomKey(
                        TelemetrySanitizer.sanitizeName(k, maxLen = 40),
                        TelemetrySanitizer.sanitizeValue(v, maxLen = 100)
                    )
                }
                log("non_fatal[$tag]: ${TelemetrySanitizer.sanitizeValue(throwable.message ?: throwable::class.java.simpleName, 140)}")
                recordException(throwable)
            }
        }
    }

    override fun startTrace(name: String): PerformanceTraceHandle {
        val perf = performanceOrNull() ?: return NoOpTraceHandle
        val safeTraceName = TelemetrySanitizer.sanitizeName(name, maxLen = 100)
        return runCatching {
            val trace = perf.newTrace(safeTraceName)
            trace.start()
            AndroidTraceHandle(trace)
        }.getOrDefault(NoOpTraceHandle)
    }

    override fun recordHttpMetric(
        sanitizedUrl: String,
        method: String,
        statusCode: Int,
        latencyMs: Long,
        responseBytes: Long
    ) {
        val perf = performanceOrNull() ?: return
        val perfMethod = when (method.uppercase(Locale.US)) {
            "GET" -> FirebasePerformance.HttpMethod.GET
            "POST" -> FirebasePerformance.HttpMethod.POST
            "PUT" -> FirebasePerformance.HttpMethod.PUT
            "DELETE" -> FirebasePerformance.HttpMethod.DELETE
            "HEAD" -> FirebasePerformance.HttpMethod.HEAD
            "PATCH" -> FirebasePerformance.HttpMethod.PATCH
            "OPTIONS" -> FirebasePerformance.HttpMethod.OPTIONS
            else -> return
        }
        runCatching {
            val metric = perf.newHttpMetric(sanitizedUrl, perfMethod)
            metric.start()
            if (statusCode in 100..599) {
                metric.setHttpResponseCode(statusCode)
            }
            if (responseBytes >= 0L) {
                metric.setResponsePayloadSize(responseBytes)
            }
            metric.putAttribute("latency_ms", latencyMs.coerceAtLeast(0L).toString())
            metric.stop()
        }
    }
}

/**
 * Central application metrics facade coordinating Google Analytics for Firebase,
 * Firebase Crashlytics, and Firebase Performance Monitoring with strict PII safety.
 */
object FirebaseMetrics {

    @Volatile
    private var sink: MetricsSink? = null

    private val collectionEnabled = AtomicBoolean(true)

    fun init(context: Context, enabled: Boolean = true) {
        collectionEnabled.set(enabled)
        if (sink == null) {
            synchronized(this) {
                if (sink == null) {
                    sink = AndroidFirebaseMetricsSink(context.applicationContext)
                }
            }
        }
        sink?.setCollectionEnabled(enabled)
        attachNetworkHttpMetricHook()
    }

    /** Replaces the active sink for deterministic unit testing. */
    fun installSinkForTesting(testSink: MetricsSink?, enabled: Boolean = true) {
        sink = testSink
        collectionEnabled.set(enabled)
        testSink?.setCollectionEnabled(enabled)
    }

    fun isRuntimeConfigured(context: Context): Boolean {
        val appCtx = context.applicationContext
        return runCatching {
            FirebaseApp.getApps(appCtx).isNotEmpty() || FirebaseApp.initializeApp(appCtx) != null
        }.getOrDefault(false)
    }

    fun isCollectionEnabled(): Boolean = collectionEnabled.get()

    fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled.set(enabled)
        sink?.setCollectionEnabled(enabled)
    }

    fun attachNetworkHttpMetricHook() {
        NetworkClientProvider.httpMetricListener = NetworkHttpMetricListener { rawUrl, method, statusCode, latencyMs, responseBytes, fromCache ->
            if (!collectionEnabled.get() || fromCache) return@NetworkHttpMetricListener
            val safeUrl = TelemetrySanitizer.sanitizeUrl(rawUrl)
            sink?.recordHttpMetric(
                sanitizedUrl = safeUrl,
                method = method,
                statusCode = statusCode,
                latencyMs = latencyMs,
                responseBytes = responseBytes
            )
        }
    }

    fun logScreenView(screenName: String, screenClass: String = "MainActivity") {
        if (!collectionEnabled.get()) return
        sink?.logScreenView(screenName, screenClass)
    }

    fun syncUserProperties(settings: ValidatorSettings) {
        if (!collectionEnabled.get()) return
        val currentSink = sink ?: return
        currentSink.setUserProperty("validation_mode", settings.validationMode.name.lowercase(Locale.US))
        currentSink.setUserProperty("theme_mode", settings.themeMode.name.lowercase(Locale.US))
        currentSink.setUserProperty("dynamic_color", settings.useDynamicColor.toString())
        currentSink.setUserProperty("telegram_enabled", settings.telegramWebhookEnabled.toString())
        currentSink.setUserProperty("battery_saver", settings.batterySaverMode.toString())
        currentSink.setUserProperty("wifi_only", settings.wifiOnlyBatch.toString())
        currentSink.setUserProperty("worker_concurrency", settings.concurrency.toString())

        currentSink.setCustomKey("validation_mode", settings.validationMode.name)
        currentSink.setCustomKey("worker_concurrency", settings.concurrency.toString())
        currentSink.setCustomKey("battery_saver", settings.batterySaverMode.toString())
        currentSink.setCustomKey("wifi_only", settings.wifiOnlyBatch.toString())
    }

    fun startTrace(name: String): PerformanceTraceHandle {
        if (!collectionEnabled.get()) return NoOpTraceHandle
        return sink?.startTrace(name) ?: NoOpTraceHandle
    }

    fun recordNonFatal(tag: String, throwable: Throwable, context: Map<String, String> = emptyMap()) {
        if (!collectionEnabled.get()) return
        sink?.recordNonFatal(tag, throwable, context)
    }

    fun logBreadcrumb(message: String) {
        if (!collectionEnabled.get()) return
        sink?.logBreadcrumb(message)
    }

    fun logBatchStarted(
        mode: ValidationMode,
        sourceType: String,
        lineCount: Int,
        concurrency: Int,
        delayMs: Long,
        wifiOnly: Boolean,
        batterySaver: Boolean,
        deduplicate: Boolean
    ) {
        if (!collectionEnabled.get()) return
        sink?.setCustomKey("last_batch_mode", mode.name)
        sink?.setCustomKey("last_batch_size", lineCount.toString())
        sink?.logBreadcrumb("batch_started:${mode.name}:$lineCount")
        sink?.logEvent(
            "batch_started",
            mapOf(
                "mode" to mode.name.lowercase(Locale.US),
                "source_type" to sourceType,
                "line_count" to lineCount,
                "concurrency" to concurrency,
                "delay_ms" to delayMs,
                "wifi_only" to wifiOnly,
                "battery_saver" to batterySaver,
                "deduplicate" to deduplicate
            )
        )
    }

    fun logBatchCompleted(
        mode: ValidationMode,
        processed: Int,
        total: Int,
        live: Int,
        die: Int,
        unknown: Int,
        durationMs: Long,
        dedupSaved: Int,
        outcome: String
    ) {
        if (!collectionEnabled.get()) return
        val liveRatePct = if (processed > 0) ((live * 100.0) / processed) else 0.0
        sink?.logBreadcrumb("batch_completed:${mode.name}:$outcome:$processed/$total")
        sink?.logEvent(
            "batch_completed",
            mapOf(
                "mode" to mode.name.lowercase(Locale.US),
                "processed_count" to processed,
                "total_count" to total,
                "live_count" to live,
                "die_count" to die,
                "unknown_count" to unknown,
                "live_rate_pct" to liveRatePct,
                "duration_ms" to durationMs.coerceAtLeast(0L),
                "dedup_saved" to dedupSaved,
                "outcome" to outcome
            )
        )
    }

    fun logBatchStopped(mode: ValidationMode, processed: Int, total: Int) {
        if (!collectionEnabled.get()) return
        sink?.logBreadcrumb("batch_stopped:${mode.name}:$processed/$total")
        sink?.logEvent(
            "batch_stopped",
            mapOf(
                "mode" to mode.name.lowercase(Locale.US),
                "processed_count" to processed,
                "total_count" to total
            )
        )
    }

    fun logBatchFailed(mode: ValidationMode, processed: Int, total: Int, reason: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "batch_failed",
            mapOf(
                "mode" to mode.name.lowercase(Locale.US),
                "processed_count" to processed,
                "total_count" to total,
                "reason" to reason
            )
        )
    }

    fun logPatternGenerated(quantity: Int, hasWildcards: Boolean) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "pattern_generated",
            mapOf(
                "quantity" to quantity,
                "has_wildcards" to hasWildcards
            )
        )
    }

    fun logWildcardsExpanded(inputLines: Int, outputLines: Int) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "wildcards_expanded",
            mapOf(
                "input_lines" to inputLines,
                "output_lines" to outputLines
            )
        )
    }

    fun logSandboxSuiteLoaded(providerId: String, vectorCount: Int) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "sandbox_suite_loaded",
            mapOf(
                "provider" to providerId,
                "vector_count" to vectorCount
            )
        )
    }

    fun logBinLookup(
        source: String,
        cacheHit: Boolean,
        scheme: String,
        countryCode: String,
        isPrepaid: Boolean?,
        isVerified: Boolean,
        success: Boolean,
        latencyMs: Long
    ) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "bin_lookup",
            mapOf(
                "source" to source,
                "cache_hit" to cacheHit,
                "scheme" to scheme.ifBlank { "unknown" },
                "country_code" to countryCode.ifBlank { "unknown" },
                "prepaid" to (isPrepaid?.toString() ?: "unknown"),
                "verified" to isVerified,
                "success" to success,
                "latency_ms" to latencyMs.coerceAtLeast(0L)
            )
        )
    }

    fun logFileImported(
        fileType: String,
        totalLines: Int,
        delimitedLines: Int,
        append: Boolean,
        onlyDelimited: Boolean,
        startImmediately: Boolean
    ) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "file_imported",
            mapOf(
                "file_type" to fileType,
                "total_lines" to totalLines,
                "delimited_lines" to delimitedLines,
                "append" to append,
                "only_delimited" to onlyDelimited,
                "start_immediately" to startImmediately
            )
        )
    }

    fun logFileImportError(errorType: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "file_import_error",
            mapOf("error_type" to errorType)
        )
    }

    fun logToolkitApplied(
        inputCount: Int,
        outputCount: Int,
        removedCount: Int
    ) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "toolkit_applied",
            mapOf(
                "input_count" to inputCount,
                "output_count" to outputCount,
                "removed_count" to removedCount
            )
        )
    }

    fun logCustomHttpTested(
        method: String,
        statusCode: Int?,
        detectedBucket: String,
        step1Enabled: Boolean,
        proxyEnabled: Boolean,
        latencyMs: Long,
        reachable: Boolean
    ) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "custom_http_tested",
            mapOf(
                "method" to method,
                "status_code" to (statusCode ?: -1),
                "detected_bucket" to detectedBucket,
                "step1_enabled" to step1Enabled,
                "proxy_enabled" to proxyEnabled,
                "latency_ms" to latencyMs.coerceAtLeast(0L),
                "reachable" to reachable
            )
        )
    }

    fun logCustomConfigApplied(
        method: String,
        parseRuleCount: Int,
        checkpointRuleCount: Int,
        preRequestEnabled: Boolean,
        proxyEnabled: Boolean,
        gzipEnabled: Boolean
    ) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "custom_config_applied",
            mapOf(
                "method" to method,
                "parse_rules" to parseRuleCount,
                "checkpoint_rules" to checkpointRuleCount,
                "pre_request" to preRequestEnabled,
                "proxy_enabled" to proxyEnabled,
                "gzip_enabled" to gzipEnabled
            )
        )
    }

    fun logCustomConfigImported(format: String, success: Boolean) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "custom_config_imported",
            mapOf(
                "format" to format,
                "success" to success
            )
        )
    }

    fun logCustomConfigExported(format: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "custom_config_exported",
            mapOf("format" to format)
        )
    }

    fun logTelegramProbe(action: String, success: Boolean) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "telegram_probe",
            mapOf(
                "action" to action,
                "success" to success
            )
        )
    }

    fun logHistoryFiltered(filter: String, hasSearchQuery: Boolean) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "history_filtered",
            mapOf(
                "filter" to filter,
                "has_search" to hasSearchQuery
            )
        )
    }

    fun logHistoryCleared() {
        if (!collectionEnabled.get()) return
        sink?.logEvent("history_cleared", emptyMap())
    }

    fun logCachesCleared() {
        if (!collectionEnabled.get()) return
        sink?.logEvent("caches_cleared", emptyMap())
    }

    fun logResultTabSelected(bucket: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent("result_tab_selected", mapOf("bucket" to bucket))
    }

    fun logResultsCopied(scope: String, count: Int) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "results_copied",
            mapOf(
                "scope" to scope,
                "count" to count
            )
        )
    }

    fun logSettingsSaved(settings: ValidatorSettings) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "settings_saved",
            mapOf(
                "mode" to settings.validationMode.name.lowercase(Locale.US),
                "concurrency" to settings.concurrency,
                "theme_mode" to settings.themeMode.name.lowercase(Locale.US),
                "dynamic_color" to settings.useDynamicColor,
                "telegram_enabled" to settings.telegramWebhookEnabled,
                "battery_saver" to settings.batterySaverMode,
                "wifi_only" to settings.wifiOnlyBatch,
                "metrics_enabled" to settings.firebaseMetricsEnabled
            )
        )
        syncUserProperties(settings)
    }

    fun logValidationModeSwitched(mode: ValidationMode, via: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "validation_mode_switched",
            mapOf(
                "mode" to mode.name.lowercase(Locale.US),
                "via" to via
            )
        )
        sink?.setUserProperty("validation_mode", mode.name.lowercase(Locale.US))
    }

    fun logPermissionPromptResult(granted: Boolean, source: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "permission_prompt_result",
            mapOf(
                "granted" to granted,
                "source" to source
            )
        )
    }

    fun logDeveloperChannelOpened(source: String) {
        if (!collectionEnabled.get()) return
        sink?.logEvent(
            "developer_channel_opened",
            mapOf("source" to source)
        )
    }
}
