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
    val compressBodyGzip: Boolean = false,
    val profileName: String = DEFAULT_PROFILE_NAME,
    val userAgent: String = DEFAULT_USER_AGENT,
    val followRedirects: Boolean = true,
    val parseRulesSpec: String = "",
    val checkpointRulesSpec: String = "",
    val proxyEnabled: Boolean = false,
    val proxyProtocol: ProxyProtocol = ProxyProtocol.HTTP,
    val proxyRotation: ProxyRotationMode = ProxyRotationMode.ROUND_ROBIN,
    val proxyListText: String = ""
) {
    fun parsedParseRules(): List<CustomParseRule> =
        CustomConfigStudioEngine.parseParseRules(parseRulesSpec)

    fun parsedCheckpointRules(): List<CustomCheckpointRule> =
        CustomConfigStudioEngine.parseCheckpointRules(checkpointRulesSpec)

    fun parsedProxyPool(): List<ParsedProxyEndpoint> =
        CustomConfigStudioEngine.parseProxyPool(proxyListText, proxyProtocol)

    companion object {
        const val DEFAULT_PROFILE_NAME = "Custom Webhook"
        const val DEFAULT_USER_AGENT = "BinCheck-Studio/2.5 (Android)"
        const val DEFAULT_LIVE_KEYWORDS = "live,approved,charged,succeeded,valid"
        const val DEFAULT_DIE_KEYWORDS = "die,dead,declined,invalid,insufficient,expired"
        const val DEFAULT_JSON_PAYLOAD =
            """{"lista":"{lista}","card":"{card}","month":"{mm}","year":"{yyyy}","cvv":"{cvv}","scheme":"{scheme}","bin":"{bin}"}"""
        const val ZERO_DOLLAR_AUTH_JSON_PAYLOAD =
            """{"amount":0,"currency":"USD","verify_card":true,"auth_type":"zero_dollar","card":{"number":"{card}","exp_month":"{mm}","exp_year":"{yyyy}","cvc":"{cvv}","scheme":"{scheme}","bin":"{bin}"}}"""
        const val DEFAULT_FORM_PAYLOAD =
            "lista={lista}&card={card}&mm={mm}&yyyy={yyyy}&cvv={cvv}&scheme={scheme}&bin={bin}"
        const val EMAIL_PASS_JSON_PAYLOAD =
            """{"email":"{email}","username":"{user}","password":"{pass}","trace_id":"{uuid}"}"""
    }
}

data class HttpProbeResult(
    val isReachable: Boolean,
    val statusCode: Int?,
    val detectedBucket: ResultBucket,
    val latencyMs: Long,
    val summary: String,
    val responsePreview: String,
    val parsedCaptures: Map<String, String> = emptyMap(),
    val matchedCheckpoint: String = "",
    val proxyUsed: String = "Direct",
    val requestSummary: String = "",
    val rawResponseBody: String = "",
    val responseHeaders: Map<String, String> = emptyMap(),
    val requestHeadersSummary: String = "",
    val requestBodyPreview: String = ""
)

object CustomHttpClient {

    private val PLACEHOLDER_TOKENS = listOf(
        "{lista}", "{card}", "{pan}", "{card_spaced}", "{pan4}",
        "{mm}", "{mm_no_pad}", "{yy}", "{yyyy}", "{cvv}", "{scheme}", "{bin}",
        "{email}", "{user}", "{pass}", "{uuid}", "{guid}", "{timestamp}", "{timestamp_ms}", "{random6}"
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
        val cacheKey = buildString(160) {
            append(config.url.trim())
            append('|').append(config.method.name)
            append('|').append(config.headersText.hashCode())
            append('|').append(config.bodyTemplate.hashCode())
            append('|').append(config.liveKeywords.hashCode())
            append('|').append(config.dieKeywords.hashCode())
            append('|').append(config.parseRulesSpec.hashCode())
            append('|').append(config.checkpointRulesSpec.hashCode())
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
                val parseRules = config.parsedParseRules()
                val captures = CustomConfigStudioEngine.extractCaptures(
                    bodyStr = outcome.body,
                    responseHeaders = outcome.headers,
                    rules = parseRules
                )
                val (bucket, reasonTag) = classifyResponse(
                    statusCode = outcome.statusCode,
                    bodyStr = outcome.body,
                    config = config,
                    precomputedCaptures = captures
                )
                val captureSuffix = CustomConfigStudioEngine.formatCapturesForSummary(captures, parseRules)
                    .let { if (it.isNotEmpty()) " $it" else "" }
                val proxySuffix = if (outcome.proxyLabel != "Direct") " • Proxy" else ""
                val retrySuffix = if (outcome.attempts > 1) " • ${outcome.attempts}x" else ""
                val cacheSuffix = if (outcome.fromCache) " • DiskCache" else ""
                val summary = when (bucket) {
                    ResultBucket.LIVE -> "HTTP ${outcome.statusCode} • Live ($reasonTag)$captureSuffix$proxySuffix$retrySuffix$cacheSuffix"
                    ResultBucket.DIE -> "HTTP ${outcome.statusCode} • Die ($reasonTag)$captureSuffix$proxySuffix$retrySuffix$cacheSuffix"
                    ResultBucket.UNKNOWN -> "HTTP ${outcome.statusCode} • Unknown ($reasonTag)$captureSuffix$proxySuffix$retrySuffix$cacheSuffix"
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

    suspend fun testEndpoint(
        config: CustomHttpConfig,
        customSampleLine: String? = null
    ): HttpProbeResult {
        val sampleLine = customSampleLine?.takeIf { it.isNotBlank() }
            ?: CardValidatorEngine.expandPatternBatch("453225005536xxx3|08|2028|113", 1)
                .firstOrNull() ?: "4532250055360003|08|2028|113"
        val parts = sampleLine.split('|', ':', '/')
        val pan = parts.getOrElse(0) { "4532250055360003" }.trim()
        val mm = parts.getOrElse(1) { "08" }.trim()
        val yyyy = parts.getOrElse(2) { "2028" }.trim()
        val cvv = parts.getOrElse(3) { "113" }.trim()
        val scheme = CardValidatorEngine.detectScheme(pan)

        val builtReq = buildRequest(
            normalizedLine = sampleLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config
        )
        val reqSummary = builtReq?.let { "${it.method} ${it.url}" }.orEmpty()
        val reqHeadersSummary = builtReq?.headers?.let { hdrs ->
            (0 until hdrs.size).joinToString("\n") { i -> "${hdrs.name(i)}: ${hdrs.value(i)}" }
        }.orEmpty()
        val reqBodyPreview = builtReq?.body?.let { body ->
            runCatching {
                val buf = okio.Buffer()
                body.writeTo(buf)
                if (config.compressBodyGzip) "(GZIP binary ${buf.size} bytes)"
                else buf.readUtf8().take(420)
            }.getOrDefault("")
        }.orEmpty()

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
                responsePreview = "",
                requestSummary = reqSummary,
                requestHeadersSummary = reqHeadersSummary,
                requestBodyPreview = reqBodyPreview
            )

            is RequestOutcome.CircuitOpen -> HttpProbeResult(
                isReachable = false,
                statusCode = null,
                detectedBucket = ResultBucket.UNKNOWN,
                latencyMs = latencyMs,
                summary = "Circuit breaker active (${(outcome.remainingMs / 1000L).coerceAtLeast(1L)}s cooldown)",
                responsePreview = "",
                requestSummary = reqSummary,
                requestHeadersSummary = reqHeadersSummary,
                requestBodyPreview = reqBodyPreview
            )

            is RequestOutcome.Failure -> HttpProbeResult(
                isReachable = false,
                statusCode = null,
                detectedBucket = ResultBucket.UNKNOWN,
                latencyMs = latencyMs,
                summary = "Connection failed: ${outcome.reason}",
                responsePreview = "",
                proxyUsed = outcome.proxyLabel,
                requestSummary = reqSummary,
                requestHeadersSummary = reqHeadersSummary,
                requestBodyPreview = reqBodyPreview
            )

            is RequestOutcome.Success -> {
                val parseRules = config.parsedParseRules()
                val captures = CustomConfigStudioEngine.extractCaptures(
                    bodyStr = outcome.body,
                    responseHeaders = outcome.headers,
                    rules = parseRules
                )
                val (bucket, reasonTag) = classifyResponse(
                    statusCode = outcome.statusCode,
                    bodyStr = outcome.body,
                    config = config,
                    precomputedCaptures = captures
                )
                val preview = outcome.body
                    .replace(Regex("\\s+"), " ")
                    .trim()
                    .take(360)
                HttpProbeResult(
                    isReachable = true,
                    statusCode = outcome.statusCode,
                    detectedBucket = bucket,
                    latencyMs = latencyMs,
                    summary = "HTTP ${outcome.statusCode} -> ${bucket.name} ($reasonTag)",
                    responsePreview = preview.ifEmpty { "(Empty response body)" },
                    parsedCaptures = captures,
                    matchedCheckpoint = reasonTag,
                    proxyUsed = outcome.proxyLabel,
                    requestSummary = reqSummary,
                    rawResponseBody = outcome.body.take(8_192),
                    responseHeaders = outcome.headers,
                    requestHeadersSummary = reqHeadersSummary,
                    requestBodyPreview = reqBodyPreview
                )
            }
        }
    }

    private sealed class RequestOutcome {
        data class InvalidUrl(val reason: String) : RequestOutcome()
        data class CircuitOpen(val remainingMs: Long) : RequestOutcome()
        data class Success(
            val statusCode: Int,
            val body: String,
            val attempts: Int,
            val fromCache: Boolean = false,
            val headers: Map<String, String> = emptyMap(),
            val proxyLabel: String = "Direct"
        ) : RequestOutcome()
        data class Failure(
            val reason: String,
            val attempts: Int,
            val proxyLabel: String = "Direct"
        ) : RequestOutcome()
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
        val selectedProxy = CustomConfigStudioEngine.selectProxyForRequest(config)
        val proxyLabel = selectedProxy?.displayLabel ?: "Direct"

        val request = buildRequest(
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config,
            selectedProxy = selectedProxy
        ) ?: return RequestOutcome.InvalidUrl("Invalid HTTP URL (must start with http:// or https://)")

        val client = NetworkClientProvider.clientWithCustomOptions(
            timeoutSeconds = config.timeoutSeconds,
            followRedirects = config.followRedirects,
            proxyEndpoint = selectedProxy
        )
        val host = request.url.host
        var lastHeaders: Map<String, String> = emptyMap()
        var lastFromCache = false

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = host,
            maxRetries = config.maxRetries,
            baseDelayMs = 450L,
            maxDelayMs = 8_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = {
                NetworkClientProvider.awaitRawCall(client, request).also { raw ->
                    lastHeaders = raw.headers
                    lastFromCache = raw.fromCache
                }
            },
            onResult = { code, body, attempts ->
                RequestOutcome.Success(
                    statusCode = code,
                    body = body,
                    attempts = attempts,
                    fromCache = lastFromCache,
                    headers = lastHeaders,
                    proxyLabel = proxyLabel
                )
            },
            onCircuitOpen = { remainingMs ->
                RequestOutcome.CircuitOpen(remainingMs)
            },
            onFailure = { reason, attempts ->
                RequestOutcome.Failure(
                    reason = reason,
                    attempts = attempts,
                    proxyLabel = proxyLabel
                )
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
        config: CustomHttpConfig,
        selectedProxy: ParsedProxyEndpoint? = null
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

        if (headers["User-Agent"] == null && config.userAgent.isNotBlank()) {
            runCatching { builder.header("User-Agent", config.userAgent.trim()) }
        }
        if (selectedProxy != null && selectedProxy.protocol == ProxyProtocol.HTTP) {
            selectedProxy.basicAuthorizationHeaderValue()?.let { basicAuth ->
                if (headers["Proxy-Authorization"] == null) {
                    runCatching { builder.header("Proxy-Authorization", basicAuth) }
                }
            }
        }

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
        val pan4 = pan.filter { it.isDigit() }.takeLast(4)
        val cardSpaced = CustomConfigStudioEngine.formatSpacedPan(pan)
        val mmNoPad = mm.toIntOrNull()?.toString() ?: mm.trimStart('0').ifEmpty { mm }

        // Support credential combo lines (email:pass or user|pass) seamlessly
        val comboParts = normalizedLine.split(':', '|', ';', limit = 3)
        val emailOrUser = comboParts.getOrElse(0) { "" }.trim()
        val comboUser = if (emailOrUser.contains('@')) emailOrUser.substringBefore('@') else emailOrUser
        val comboPass = comboParts.getOrElse(1) { "" }.trim()

        val uuidStr = if (template.contains("{uuid}", ignoreCase = true) || template.contains("{guid}", ignoreCase = true)) {
            CustomConfigStudioEngine.generateUuidToken()
        } else ""
        val tsStr = if (template.contains("{timestamp}", ignoreCase = true)) {
            CustomConfigStudioEngine.generateTimestampSeconds()
        } else ""
        val tsMsStr = if (template.contains("{timestamp_ms}", ignoreCase = true)) {
            CustomConfigStudioEngine.generateTimestampMillis()
        } else ""
        val random6Str = if (template.contains("{random6}", ignoreCase = true)) {
            CustomConfigStudioEngine.generateRandomDigits(6)
        } else ""

        fun formatVal(value: String): String = when {
            urlEncodeValues -> URLEncoder.encode(value, "UTF-8")
            jsonEscapeValues -> jsonEscapeInner(value)
            else -> value
        }

        val replacements = listOf(
            "{lista}" to formatVal(normalizedLine),
            "{card_spaced}" to formatVal(cardSpaced),
            "{card}" to formatVal(pan),
            "{pan4}" to formatVal(pan4),
            "{pan}" to formatVal(pan),
            "{mm_no_pad}" to formatVal(mmNoPad),
            "{mm}" to formatVal(mm),
            "{yyyy}" to formatVal(yyyy),
            "{yy}" to formatVal(yy),
            "{cvv}" to formatVal(cvv),
            "{scheme}" to formatVal(scheme.displayName),
            "{bin}" to formatVal(bin6),
            "{email}" to formatVal(emailOrUser),
            "{user}" to formatVal(comboUser),
            "{pass}" to formatVal(comboPass),
            "{uuid}" to formatVal(uuidStr),
            "{guid}" to formatVal(uuidStr),
            "{timestamp_ms}" to formatVal(tsMsStr),
            "{timestamp}" to formatVal(tsStr),
            "{random6}" to formatVal(random6Str)
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
        config: CustomHttpConfig,
        precomputedCaptures: Map<String, String>? = null
    ): Pair<ResultBucket, String> {
        val checkpointRules = config.parsedCheckpointRules()
        if (checkpointRules.isNotEmpty()) {
            val captures = precomputedCaptures ?: CustomConfigStudioEngine.extractCaptures(
                bodyStr = bodyStr,
                responseHeaders = emptyMap(),
                rules = config.parsedParseRules()
            )
            val checkpointVerdict = CustomConfigStudioEngine.evaluateCheckpoints(
                statusCode = statusCode,
                bodyStr = bodyStr,
                captures = captures,
                rules = checkpointRules
            )
            if (checkpointVerdict != null) {
                return checkpointVerdict
            }
        }

        val trimmed = bodyStr.trim()
        val liveTerms = parseKeywordList(config.liveKeywords, CustomHttpConfig.DEFAULT_LIVE_KEYWORDS)
        val dieTerms = parseKeywordList(config.dieKeywords, CustomHttpConfig.DEFAULT_DIE_KEYWORDS)

        if (trimmed.startsWith("{")) {
            val statusKeys = listOf("status", "result", "state", "code", "verdict", "message")
            for (key in statusKeys) {
                val value = CustomConfigStudioEngine.extractJsonPathValue(trimmed, key)?.trim().orEmpty()
                if (value.isNotEmpty()) {
                    val lowerVal = value.lowercase()
                    val matchedDie = dieTerms.firstOrNull { containsKeywordOrNegated(lowerVal, it, liveTerms) }
                    if (matchedDie != null) {
                        return ResultBucket.DIE to value.take(24)
                    }
                    val matchedLive = liveTerms.firstOrNull { matchesWordOrExact(lowerVal, it) }
                    if (matchedLive != null) {
                        return ResultBucket.LIVE to value.take(24)
                    }
                }
            }
            val validVal = CustomConfigStudioEngine.extractJsonPathValue(trimmed, "valid")
            if (validVal.equals("true", ignoreCase = true) || validVal.equals("false", ignoreCase = true)) {
                val ok = validVal.equals("true", ignoreCase = true)
                return (if (ok) ResultBucket.LIVE else ResultBucket.DIE) to if (ok) "valid=true" else "valid=false"
            }
            val approvedVal = CustomConfigStudioEngine.extractJsonPathValue(trimmed, "approved")
            if (approvedVal.equals("true", ignoreCase = true) || approvedVal.equals("false", ignoreCase = true)) {
                val ok = approvedVal.equals("true", ignoreCase = true)
                return (if (ok) ResultBucket.LIVE else ResultBucket.DIE) to if (ok) "approved=true" else "approved=false"
            }
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
