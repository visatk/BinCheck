package com.example.domain

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.FormBody
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class ParsedApiVerdict(
    val bucket: ResultBucket,
    val detailMessage: String,
    val embeddedBinInfo: BinInfo? = null
)

object ValidationApiProviders {

    const val PAYATE_URL = "https://mock.payate.com/api.php"

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
        startNanos: Long,
        useCacheAndDedup: Boolean = true
    ): ValidationRecord {
        val cacheKey = "payate|$normalizedLine"
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
        }

        suspend fun runNetworkCall(): ValidationRecord {
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
            if (useCacheAndDedup && fresh.bucket != ResultBucket.UNKNOWN) {
                responseCache.put(cacheKey, fresh, VERDICT_CACHE_TTL_MS)
            }
            return fresh
        }

        if (!useCacheAndDedup) {
            return runNetworkCall()
        }

        val (record, wasCoalesced) = deduplicator.getOrExecute(cacheKey) {
            runNetworkCall()
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
        startNanos: Long,
        useCacheAndDedup: Boolean = true
    ): ValidationRecord {
        val primary = executePayate(
            rawLine = rawLine,
            normalizedLine = normalizedLine,
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            timeoutSeconds = timeoutSeconds,
            startNanos = startNanos,
            useCacheAndDedup = useCacheAndDedup
        )
        if (primary.bucket != ResultBucket.UNKNOWN) {
            return primary
        }

        currentCoroutineContext().ensureActive()
        val (sandboxBucket, sandboxMessage) = SandboxTestRegistry.evaluateSandboxCard(
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            timeoutSeconds = timeoutSeconds,
            useCacheAndDedup = useCacheAndDedup
        )
        val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
        return ValidationRecord(
            id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
            rawLine = rawLine,
            normalizedLine = normalizedLine,
            pan = pan,
            month = mm,
            year = yyyy,
            cvv = cvv,
            scheme = scheme,
            bucket = sandboxBucket,
            message = "$sandboxMessage (Failover)",
            latencyMs = elapsed
        )
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
}
