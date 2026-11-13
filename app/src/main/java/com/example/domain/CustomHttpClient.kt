package com.example.domain

import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

enum class HttpRequestMethod(val label: String) {
    AUTO("Auto"),
    GET("GET"),
    POST_JSON("JSON"),
    POST_FORM("Form")
}

data class CustomHttpConfig(
    val url: String = "",
    val method: HttpRequestMethod = HttpRequestMethod.AUTO,
    val headersText: String = "",
    val bodyTemplate: String = "",
    val timeoutSeconds: Int = 12,
    val maxRetries: Int = 1,
    val liveKeywords: String = DEFAULT_LIVE_KEYWORDS,
    val dieKeywords: String = DEFAULT_DIE_KEYWORDS,
    val compressBodyGzip: Boolean = false
) {
    companion object {
        const val DEFAULT_LIVE_KEYWORDS = "live,approved,charged,succeeded,valid"
        const val DEFAULT_DIE_KEYWORDS = "die,dead,declined,invalid,insufficient,expired"
        const val DEFAULT_JSON_PAYLOAD =
            """{"lista":"{lista}","card":"{card}","month":"{mm}","year":"{yyyy}","cvv":"{cvv}","scheme":"{scheme}","bin":"{bin}"}"""
        const val DEFAULT_FORM_PAYLOAD =
            "lista={lista}&card={card}&mm={mm}&yyyy={yyyy}&cvv={cvv}&scheme={scheme}&bin={bin}"
    }
}

data class HttpProbeResult(
    val isReachable: Boolean,
    val statusCode: Int?,
    val detectedBucket: ResultBucket,
    val latencyMs: Long,
    val summary: String,
    val responsePreview: String
)

object CustomHttpClient {

    private val PLACEHOLDER_TOKENS = listOf(
        "{lista}", "{card}", "{pan}", "{mm}", "{yy}", "{yyyy}", "{cvv}", "{scheme}", "{bin}"
    )

    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)
    private val responseCache = TtlLruCache<String, ValidationRecord>(maxEntries = 1_000)
    private val deduplicator = RequestDeduplicator<String, ValidationRecord>()

    fun clearCache() {
        responseCache.clear()
    }

    suspend fun executeValidation(
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        config: CustomHttpConfig,
        startNanos: Long,
        useCacheAndDedup: Boolean = true
    ): ValidationRecord {
        val cacheKey = buildString(128) {
            append(config.url.trim())
            append('|').append(config.method.name)
            append('|').append(config.headersText.hashCode())
            append('|').append(config.bodyTemplate.hashCode())
            append('|').append(config.liveKeywords.hashCode())
            append('|').append(config.dieKeywords.hashCode())
            append('|').append(config.compressBodyGzip)
            append('|').append(normalizedLine)
        }
        if (useCacheAndDedup) {
            responseCache.get(cacheKey)?.let { cached ->
                NetworkClientProvider.telemetry.recordCacheHit()
                return cached.copy(
                    id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                    rawLine = rawLine,
                    latencyMs = 1L,
                    message = "${cached.message} (Cached)"
                )
            }

            val (record, wasCoalesced) = deduplicator.getOrExecute(cacheKey) {
                executeFreshValidation(
                    rawLine = rawLine,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    mm = mm,
                    yyyy = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    config = config,
                    startNanos = startNanos
                ).also { fresh ->
                    if (fresh.bucket != ResultBucket.UNKNOWN) {
                        responseCache.put(cacheKey, fresh, CACHE_TTL_MS)
                    }
                }
            }
            if (wasCoalesced) {
                NetworkClientProvider.telemetry.recordDeduplicatedSaved()
            }
            return record
        }

        return executeFreshValidation(
            rawLine = rawLine,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config,
            startNanos = startNanos
        )
    }

    private suspend fun executeFreshValidation(
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        config: CustomHttpConfig,
        startNanos: Long
    ): ValidationRecord {
        val outcome = executeWithRetries(
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config
        )

        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
        return when (outcome) {
            is RequestOutcome.InvalidUrl -> ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = rawLine,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.UNKNOWN,
                message = outcome.reason,
                latencyMs = elapsed
            )

            is RequestOutcome.CircuitOpen -> ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = rawLine,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.UNKNOWN,
                message = "Endpoint paused (${(outcome.remainingMs / 1000L).coerceAtLeast(1L)}s server overload cooldown)",
                latencyMs = elapsed
            )

            is RequestOutcome.Failure -> ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = rawLine,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.UNKNOWN,
                message = "HTTP error (${outcome.attempts}x): ${outcome.reason}",
                latencyMs = elapsed
            )

            is RequestOutcome.Success -> {
                val (bucket, reasonTag) = classifyResponse(
                    statusCode = outcome.statusCode,
                    bodyStr = outcome.body,
                    config = config
                )
                val retrySuffix = if (outcome.attempts > 1) " • ${outcome.attempts}x" else ""
                val cacheSuffix = if (outcome.fromCache) " • DiskCache" else ""
                val summary = when (bucket) {
                    ResultBucket.LIVE -> "HTTP ${outcome.statusCode} • Live ($reasonTag)$retrySuffix$cacheSuffix"
                    ResultBucket.DIE -> "HTTP ${outcome.statusCode} • Die ($reasonTag)$retrySuffix$cacheSuffix"
                    ResultBucket.UNKNOWN -> "HTTP ${outcome.statusCode} • Unknown ($reasonTag)$retrySuffix$cacheSuffix"
                }
                ValidationRecord(
                    id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                    rawLine = rawLine,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    bucket = bucket,
                    message = summary,
                    latencyMs = elapsed
                )
            }
        }
    }

    suspend fun testEndpoint(config: CustomHttpConfig): HttpProbeResult {
        val sampleLine = CardValidatorEngine.expandPatternBatch("453225005536xxx3|08|2028|113", 1)
            .firstOrNull() ?: "4532250055360003|08|2028|113"
        val parts = sampleLine.split('|')
        val pan = parts.getOrElse(0) { "4532250055360003" }
        val mm = parts.getOrElse(1) { "08" }
        val yyyy = parts.getOrElse(2) { "2028" }
        val cvv = parts.getOrElse(3) { "113" }
        val scheme = CardValidatorEngine.detectScheme(pan)

        val startNanos = System.nanoTime()
        val outcome = executeWithRetries(
            normalizedLine = sampleLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config.copy(maxRetries = config.maxRetries.coerceIn(0, 1))
        )
        val latencyMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)

        return when (outcome) {
            is RequestOutcome.InvalidUrl -> HttpProbeResult(
                isReachable = false,
                statusCode = null,
                detectedBucket = ResultBucket.UNKNOWN,
                latencyMs = latencyMs,
                summary = outcome.reason,
                responsePreview = ""
            )

            is RequestOutcome.CircuitOpen -> HttpProbeResult(
                isReachable = false,
                statusCode = null,
                detectedBucket = ResultBucket.UNKNOWN,
                latencyMs = latencyMs,
                summary = "Circuit breaker active (${(outcome.remainingMs / 1000L).coerceAtLeast(1L)}s cooldown)",
                responsePreview = ""
            )

            is RequestOutcome.Failure -> HttpProbeResult(
                isReachable = false,
                statusCode = null,
                detectedBucket = ResultBucket.UNKNOWN,
                latencyMs = latencyMs,
                summary = "Connection failed: ${outcome.reason}",
                responsePreview = ""
            )

            is RequestOutcome.Success -> {
                val (bucket, reasonTag) = classifyResponse(outcome.statusCode, outcome.body, config)
                val preview = outcome.body
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(220)
                HttpProbeResult(
                    isReachable = true,
                    statusCode = outcome.statusCode,
                    detectedBucket = bucket,
                    latencyMs = latencyMs,
                    summary = "HTTP ${outcome.statusCode} -> ${bucket.name} ($reasonTag)",
                    responsePreview = preview.ifEmpty { "(Empty response body)" }
                )
            }
        }
    }

    private sealed class RequestOutcome {
        data class InvalidUrl(val reason: String) : RequestOutcome()
        data class CircuitOpen(val remainingMs: Long) : RequestOutcome()
        data class Success(val statusCode: Int, val body: String, val attempts: Int, val fromCache: Boolean = false) : RequestOutcome()
        data class Failure(val reason: String, val attempts: Int) : RequestOutcome()
    }

    private suspend fun executeWithRetries(
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        config: CustomHttpConfig
    ): RequestOutcome {
        val request = buildRequest(
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config
        ) ?: return RequestOutcome.InvalidUrl("Invalid HTTP URL (must start with http:// or https://)")

        val client = NetworkClientProvider.clientWithTimeout(config.timeoutSeconds)
        val host = request.url.host

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = host,
            maxRetries = config.maxRetries,
            baseDelayMs = 450L,
            maxDelayMs = 8_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(client, request) },
            onResult = { code, body, attempts ->
                RequestOutcome.Success(statusCode = code, body = body, attempts = attempts)
            },
            onCircuitOpen = { remainingMs ->
                RequestOutcome.CircuitOpen(remainingMs)
            },
            onFailure = { reason, attempts ->
                RequestOutcome.Failure(reason = reason, attempts = attempts)
            }
        )
    }

    internal fun buildRequest(
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        config: CustomHttpConfig
    ): Request? {
        val rawUrl = config.url.trim()
        if (rawUrl.isEmpty()) return null

        val hasUrlPlaceholders = PLACEHOLDER_TOKENS.any { rawUrl.contains(it, ignoreCase = true) }
        val resolvedMethod = when (config.method) {
            HttpRequestMethod.AUTO -> when {
                config.bodyTemplate.isNotBlank() -> HttpRequestMethod.POST_JSON
                hasUrlPlaceholders -> HttpRequestMethod.GET
                else -> HttpRequestMethod.POST_JSON
            }
            else -> config.method
        }

        val resolvedUrlStr = substitutePlaceholders(
            template = rawUrl,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            urlEncodeValues = true
        )
        val httpUrl = resolvedUrlStr.toHttpUrlOrNull() ?: return null

        val headers = parseHeaders(
            headersText = config.headersText,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme
        )

        val builder = Request.Builder()
            .url(httpUrl)
            .headers(headers)

        when (resolvedMethod) {
            HttpRequestMethod.GET -> builder.get()

            HttpRequestMethod.POST_JSON -> {
                val jsonPayload = if (config.bodyTemplate.isNotBlank()) {
                    substitutePlaceholders(
                        template = config.bodyTemplate,
                        normalizedLine = normalizedLine,
                        pan = pan,
                        mm = mm,
                        yyyy = yyyy,
                        cvv = cvv,
                        scheme = scheme,
                        urlEncodeValues = false,
                        jsonEscapeValues = true
                    )
                } else {
                    buildString {
                        append('{')
                        append("\"raw\":\"${jsonEscapeInner(normalizedLine)}\",")
                        append("\"lista\":\"${jsonEscapeInner(normalizedLine)}\",")
                        append("\"number\":\"${jsonEscapeInner(pan)}\",")
                        append("\"month\":\"${jsonEscapeInner(mm)}\",")
                        append("\"year\":\"${jsonEscapeInner(yyyy)}\",")
                        append("\"cvv\":\"${jsonEscapeInner(cvv)}\",")
                        append("\"scheme\":\"${jsonEscapeInner(scheme.displayName)}\",")
                        append("\"bin\":\"${jsonEscapeInner(pan.filter { it.isDigit() }.take(6))}\"")
                        append('}')
                    }
                }
                val mediaType = "application/json; charset=utf-8".toMediaType()
                if (config.compressBodyGzip) {
                    builder.header("Content-Encoding", "gzip")
                    builder.post(NetworkClientProvider.createGzipRequestBody(jsonPayload, mediaType))
                } else {
                    builder.post(jsonPayload.toRequestBody(mediaType))
                }
            }

            HttpRequestMethod.POST_FORM -> {
                if (config.bodyTemplate.isNotBlank()) {
                    val formBodyStr = substitutePlaceholders(
                        template = config.bodyTemplate,
                        normalizedLine = normalizedLine,
                        pan = pan,
                        mm = mm,
                        yyyy = yyyy,
                        cvv = cvv,
                        scheme = scheme,
                        urlEncodeValues = true
                    )
                    val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
                    if (config.compressBodyGzip) {
                        builder.header("Content-Encoding", "gzip")
                        builder.post(NetworkClientProvider.createGzipRequestBody(formBodyStr, mediaType))
                    } else {
                        builder.post(formBodyStr.toRequestBody(mediaType))
                    }
                } else {
                    val form = FormBody.Builder()
                        .add("raw", normalizedLine)
                        .add("lista", normalizedLine)
                        .add("number", pan)
                        .add("month", mm)
                        .add("year", yyyy)
                        .add("cvv", cvv)
                        .add("scheme", scheme.displayName)
                        .build()
                    if (config.compressBodyGzip) {
                        val encoded = okio.Buffer().apply { form.writeTo(this) }.readUtf8()
                        val mediaType = "application/x-www-form-urlencoded; charset=utf-8".toMediaType()
                        builder.header("Content-Encoding", "gzip")
                        builder.post(NetworkClientProvider.createGzipRequestBody(encoded, mediaType))
                    } else {
                        builder.post(form)
                    }
                }
            }

            HttpRequestMethod.AUTO -> Unit
        }

        return builder.build()
    }

    internal fun parseHeaders(
        headersText: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme
    ): Headers {
        val builder = Headers.Builder()
        if (headersText.isBlank()) return builder.build()

        headersText.lines().forEach { rawHeader ->
            val trimmed = rawHeader.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("//")) return@forEach
            val sepIndex = trimmed.indexOf(':')
            if (sepIndex <= 0) return@forEach

            val key = trimmed.substring(0, sepIndex).trim()
            val rawValue = trimmed.substring(sepIndex + 1).trim()
            val value = substitutePlaceholders(
                template = rawValue,
                normalizedLine = normalizedLine,
                pan = pan,
                mm = mm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                urlEncodeValues = false
            )
            runCatching { builder.add(key, value) }
        }
        return builder.build()
    }

    private fun substitutePlaceholders(
        template: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        urlEncodeValues: Boolean,
        jsonEscapeValues: Boolean = false
    ): String {
        val yy = if (yyyy.length >= 2) yyyy.takeLast(2) else yyyy
        val bin6 = pan.filter { it.isDigit() }.take(6)
        fun formatVal(value: String): String = when {
            urlEncodeValues -> URLEncoder.encode(value, "UTF-8")
            jsonEscapeValues -> jsonEscapeInner(value)
            else -> value
        }

        val replacements = listOf(
            "{lista}" to formatVal(normalizedLine),
            "{card}" to formatVal(pan),
            "{pan}" to formatVal(pan),
            "{mm}" to formatVal(mm),
            "{yyyy}" to formatVal(yyyy),
            "{yy}" to formatVal(yy),
            "{cvv}" to formatVal(cvv),
            "{scheme}" to formatVal(scheme.displayName),
            "{bin}" to formatVal(bin6)
        )

        var output = template
        for ((token, replacement) in replacements) {
            output = output.replace(token, replacement, ignoreCase = true)
        }
        return output
    }

    private fun jsonEscapeInner(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') {
                    sb.append(String.format("\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    internal fun classifyResponse(
        statusCode: Int,
        bodyStr: String,
        config: CustomHttpConfig
    ): Pair<ResultBucket, String> {
        val trimmed = bodyStr.trim()
        val liveTerms = parseKeywordList(config.liveKeywords, CustomHttpConfig.DEFAULT_LIVE_KEYWORDS)
        val dieTerms = parseKeywordList(config.dieKeywords, CustomHttpConfig.DEFAULT_DIE_KEYWORDS)

        if (trimmed.startsWith("{")) {
            val jsonClassification = runCatching {
                val json = JSONObject(trimmed)
                val statusKeys = listOf("status", "result", "state", "code", "verdict", "message")
                for (key in statusKeys) {
                    val value = json.optString(key, "").trim()
                    if (value.isNotEmpty()) {
                        val lowerVal = value.lowercase()
                        val matchedDie = dieTerms.firstOrNull { containsKeywordOrNegated(lowerVal, it, liveTerms) }
                        if (matchedDie != null) {
                            return@runCatching ResultBucket.DIE to value.take(24)
                        }
                        val matchedLive = liveTerms.firstOrNull { matchesWordOrExact(lowerVal, it) }
                        if (matchedLive != null) {
                            return@runCatching ResultBucket.LIVE to value.take(24)
                        }
                    }
                }
                if (json.has("valid")) {
                    val ok = json.optBoolean("valid", false)
                    return@runCatching (if (ok) ResultBucket.LIVE else ResultBucket.DIE) to if (ok) "valid=true" else "valid=false"
                }
                if (json.has("approved")) {
                    val ok = json.optBoolean("approved", false)
                    return@runCatching (if (ok) ResultBucket.LIVE else ResultBucket.DIE) to if (ok) "approved=true" else "approved=false"
                }
                null
            }.getOrNull()

            if (jsonClassification != null) return jsonClassification
        }

        val lower = trimmed.lowercase()

        for (term in liveTerms) {
            if (lower.contains("not $term") || lower.contains("not_$term")) {
                return ResultBucket.DIE to "not $term"
            }
        }
        val matchedDie = dieTerms.firstOrNull { matchesWordOrPhrase(lower, it) }
        if (matchedDie != null) {
            return ResultBucket.DIE to matchedDie
        }

        val matchedLive = liveTerms.firstOrNull { matchesWordOrPhrase(lower, it) }
        if (matchedLive != null) {
            return ResultBucket.LIVE to matchedLive
        }

        return when {
            statusCode in 200..299 -> ResultBucket.LIVE to "HTTP $statusCode"
            statusCode in 400..499 -> ResultBucket.DIE to "HTTP $statusCode"
            else -> ResultBucket.UNKNOWN to "HTTP $statusCode"
        }
    }

    private fun parseKeywordList(raw: String, fallback: String): List<String> {
        val parsed = raw.split(',', ';', '\n')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
        return parsed.ifEmpty {
            fallback.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
    }

    private fun containsKeywordOrNegated(lowerVal: String, dieTerm: String, liveTerms: List<String>): Boolean {
        if (liveTerms.any { lowerVal.contains("not $it") || lowerVal.contains("not_$it") }) return true
        return matchesWordOrExact(lowerVal, dieTerm)
    }

    private fun matchesWordOrExact(lowerText: String, term: String): Boolean {
        if (lowerText == term) return true
        return matchesWordOrPhrase(lowerText, term)
    }

    private fun matchesWordOrPhrase(lowerText: String, term: String): Boolean {
        val escaped = Regex.escape(term)
        return Regex("\\b$escaped\\b").containsMatchIn(lowerText)
    }
}
