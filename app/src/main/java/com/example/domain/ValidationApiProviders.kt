package com.example.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class ParsedApiVerdict(
    val bucket: ResultBucket,
    val detailMessage: String,
    val embeddedBinInfo: BinInfo? = null
)

object ValidationApiProviders {

    const val PAYATE_URL = "https://mock.payate.com/api.php"
    const val CHKR_URL = "https://api.chkr.cc/"

    private val VERDICT_CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(5)
    private val responseCache = TtlLruCache<String, ValidationRecord>(maxEntries = 1_000)
    private val deduplicator = RequestDeduplicator<String, ValidationRecord>()

    fun clearCache() {
        responseCache.clear()
    }

    suspend fun executePayate(
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        timeoutSeconds: Int = 15,
        startNanos: Long
    ): ValidationRecord {
        val cacheKey = "payate|$normalizedLine"
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
            val request = buildPayateRequest(normalizedLine)
            val fresh = executeSingleProvider(
                providerName = "Payate",
                host = "mock.payate.com",
                request = request,
                rawLine = rawLine,
                normalizedLine = normalizedLine,
                pan = pan,
                mm = mm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                timeoutSeconds = timeoutSeconds,
                startNanos = startNanos,
                parser = { statusCode, body -> parsePayateResponse(statusCode, body) }
            )
            if (fresh.bucket != ResultBucket.UNKNOWN) {
                responseCache.put(cacheKey, fresh, VERDICT_CACHE_TTL_MS)
            }
            fresh
        }
        if (wasCoalesced) {
            NetworkClientProvider.telemetry.recordDeduplicatedSaved()
        }
        return record
    }

    suspend fun executeChkr(
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        timeoutSeconds: Int = 15,
        startNanos: Long
    ): ValidationRecord {
        val cacheKey = "chkr|$normalizedLine"
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
            val request = buildChkrRequest(normalizedLine)
            val fresh = executeSingleProvider(
                providerName = "Chkr.cc",
                host = "api.chkr.cc",
                request = request,
                rawLine = rawLine,
                normalizedLine = normalizedLine,
                pan = pan,
                mm = mm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                timeoutSeconds = timeoutSeconds,
                startNanos = startNanos,
                parser = { statusCode, body ->
                    val parsed = parseChkrResponse(pan, statusCode, body)
                    parsed.embeddedBinInfo?.let { BinLookupService.cacheVerifiedBin(it) }
                    parsed
                }
            )
            if (fresh.bucket != ResultBucket.UNKNOWN) {
                responseCache.put(cacheKey, fresh, VERDICT_CACHE_TTL_MS)
            }
            fresh
        }
        if (wasCoalesced) {
            NetworkClientProvider.telemetry.recordDeduplicatedSaved()
        }
        return record
    }

    suspend fun executeMultiApiFailover(
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        timeoutSeconds: Int = 15,
        startNanos: Long
    ): ValidationRecord {
        val primary = executeChkr(
            rawLine = rawLine,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            timeoutSeconds = timeoutSeconds,
            startNanos = startNanos
        )
        if (primary.bucket != ResultBucket.UNKNOWN) {
            return primary
        }

        currentCoroutineContext().ensureActive()
        val secondary = executePayate(
            rawLine = rawLine,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            timeoutSeconds = timeoutSeconds,
            startNanos = startNanos
        )
        return if (secondary.bucket != ResultBucket.UNKNOWN) {
            secondary.copy(message = "${secondary.message} (Failover)")
        } else {
            secondary
        }
    }

    internal fun buildPayateRequest(normalizedLine: String): Request {
        val formBody = FormBody.Builder()
            .add("data", normalizedLine)
            .build()

        return Request.Builder()
            .url(PAYATE_URL)
            .header("Accept", "*/*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Origin", "https://mock.payate.com")
            .header("Referer", "https://mock.payate.com/")
            .post(formBody)
            .build()
    }

    internal fun buildChkrRequest(normalizedLine: String): Request {
        val escapedData = jsonEscape(normalizedLine)
        val payload = """{"data":"$escapedData","charge":false}"""
        return Request.Builder()
            .url(CHKR_URL)
            .header("Accept", "application/json, text/javascript, */*; q=0.01")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Origin", "https://chkr.cc")
            .header("Referer", "https://chkr.cc/")
            .post(payload.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            .build()
    }

    internal fun parsePayateResponse(statusCode: Int, bodyStr: String): ParsedApiVerdict {
        val trimmed = bodyStr.trim()
        if (trimmed.startsWith("{")) {
            val parsedJson = runCatching {
                val json = JSONObject(trimmed)
                val errorCode = json.optInt("error", -1)
                val rawMsg = json.optString("msg", "").replace("\\/", "/")
                val cleanMsg = stripHtmlTags(rawMsg)

                val bucket = when (errorCode) {
                    1 -> ResultBucket.LIVE
                    2 -> ResultBucket.DIE
                    3 -> ResultBucket.UNKNOWN
                    else -> classifyPlainTextVerdict(cleanMsg)
                }

                val summaryTail = extractReadablePayateDetail(cleanMsg)
                    .ifBlank {
                        when (bucket) {
                            ResultBucket.LIVE -> "Payate Live"
                            ResultBucket.DIE -> "Payate Die"
                            ResultBucket.UNKNOWN -> "Payate Unknown"
                        }
                    }

                ParsedApiVerdict(bucket = bucket, detailMessage = summaryTail)
            }.getOrNull()

            if (parsedJson != null) return parsedJson
        }

        val plain = stripHtmlTags(trimmed)
        val bucket = classifyPlainTextVerdict(plain)
        return ParsedApiVerdict(
            bucket = bucket,
            detailMessage = plain.take(72).ifBlank { "HTTP $statusCode" }
        )
    }

    internal fun parseChkrResponse(
        pan: String,
        statusCode: Int,
        bodyStr: String
    ): ParsedApiVerdict {
        val trimmed = bodyStr.trim()
        if (trimmed.startsWith("{")) {
            val parsedJson = runCatching {
                val json = JSONObject(trimmed)
                val statusStr = json.optString("status", "").trim()
                val codeVal = if (json.has("code")) json.optInt("code", -1) else -1
                val msg = json.optString("message", "").trim()

                val bucket = when {
                    statusStr.equals("Live", ignoreCase = true) ||
                        statusStr.equals("Approved", ignoreCase = true) -> ResultBucket.LIVE
                    statusStr.equals("Die", ignoreCase = true) ||
                        statusStr.equals("Dead", ignoreCase = true) ||
                        statusStr.equals("Declined", ignoreCase = true) -> ResultBucket.DIE
                    codeVal == 1 -> ResultBucket.LIVE
                    codeVal == 0 -> ResultBucket.DIE
                    else -> classifyPlainTextVerdict("$statusStr $msg")
                }

                val bin6 = BinLookupService.extractBin6(pan)
                val cardObj = json.optJSONObject("card")
                val binInfo = if (cardObj != null && bin6.length == 6) {
                    val countryObj = cardObj.optJSONObject("country")
                    val rawName = countryObj?.optString("name", "")?.trim().orEmpty()
                    val rawCode = countryObj?.optString("code", "")?.trim()?.uppercase().orEmpty()
                    val resolvedCode = if ((rawCode.isEmpty() || rawCode == "XX") && rawName.length == 2) {
                        rawName.uppercase()
                    } else {
                        rawCode
                    }
                    val emoji = countryObj?.optString("emoji", "")?.trim().orEmpty()
                        .ifEmpty { BinLookupService.countryCodeToEmoji(resolvedCode) }
                    val currency = countryObj?.optString("currency", "")?.trim()?.uppercase().orEmpty()

                    val schemeName = cardObj.optString("brand", "").trim()
                        .ifEmpty { cardObj.optString("type", "").trim() }
                        .uppercase()
                        .ifEmpty { CardValidatorEngine.detectScheme(bin6).displayName }
                    val category = cardObj.optString("category", "").trim().uppercase()
                    val bank = cardObj.optString("bank", "").trim()

                    BinInfo(
                        bin = bin6,
                        scheme = schemeName,
                        type = category,
                        brand = cardObj.optString("brand", "").trim().uppercase(),
                        bankName = bank,
                        countryName = rawName,
                        countryCode = resolvedCode,
                        countryEmoji = emoji,
                        currency = currency,
                        isPrepaid = category.equals("PREPAID", ignoreCase = true),
                        source = "chkr.cc",
                        isNetworkVerified = true
                    )
                } else {
                    null
                }

                val detail = msg.ifBlank {
                    when (bucket) {
                        ResultBucket.LIVE -> "Chkr Live"
                        ResultBucket.DIE -> "Chkr Die"
                        ResultBucket.UNKNOWN -> "Chkr Unknown"
                    }
                }
                ParsedApiVerdict(
                    bucket = bucket,
                    detailMessage = detail,
                    embeddedBinInfo = binInfo
                )
            }.getOrNull()

            if (parsedJson != null) return parsedJson
        }

        val bucket = when {
            statusCode == 429 || statusCode >= 500 -> ResultBucket.UNKNOWN
            else -> classifyPlainTextVerdict(trimmed)
        }
        return ParsedApiVerdict(
            bucket = bucket,
            detailMessage = trimmed.take(72).ifBlank { "HTTP $statusCode" }
        )
    }

    private suspend fun executeSingleProvider(
        providerName: String,
        host: String,
        request: Request,
        rawLine: String,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        timeoutSeconds: Int,
        startNanos: Long,
        parser: (Int, String) -> ParsedApiVerdict
    ): ValidationRecord {
        val scopedClient = NetworkClientProvider.clientWithTimeout(timeoutSeconds)

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = host,
            maxRetries = 2,
            baseDelayMs = 450L,
            maxDelayMs = 6_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(scopedClient, request) },
            onResult = { code, body, attempts ->
                val parsed = parser(code, body)
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
                val retryTag = if (attempts > 1) " • ${attempts}x" else ""
                ValidationRecord(
                    id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                    rawLine = rawLine,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    bucket = parsed.bucket,
                    message = "$providerName • ${parsed.detailMessage}$retryTag",
                    latencyMs = elapsed
                )
            },
            onCircuitOpen = { remainingMs ->
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
                ValidationRecord(
                    id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                    rawLine = rawLine,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    bucket = ResultBucket.UNKNOWN,
                    message = "$providerName paused (${(remainingMs / 1000L).coerceAtLeast(1L)}s overload cooldown)",
                    latencyMs = elapsed
                )
            },
            onFailure = { reason, attempts ->
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
                ValidationRecord(
                    id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                    rawLine = rawLine,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    bucket = ResultBucket.UNKNOWN,
                    message = "$providerName error (${attempts}x): $reason",
                    latencyMs = elapsed
                )
            }
        )
    }

    private fun stripHtmlTags(html: String): String {
        return html
            .replace(Regex("<[^>]+>"), " ")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun extractReadablePayateDetail(cleanMsg: String): String {
        val parts = cleanMsg.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        return if (parts.size >= 4) {
            parts.drop(3).joinToString(" | ")
        } else {
            cleanMsg
        }
    }

    private fun classifyPlainTextVerdict(text: String): ResultBucket {
        val lower = text.lowercase()
        return when {
            lower.contains("not live") || Regex("\\b(die|dead|declined|invalid)\\b").containsMatchIn(lower) -> ResultBucket.DIE
            Regex("\\b(live|approved|charged)\\b").containsMatchIn(lower) -> ResultBucket.LIVE
            else -> ResultBucket.UNKNOWN
        }
    }

    private fun jsonEscape(value: String): String {
        val sb = StringBuilder(value.length + 8)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
