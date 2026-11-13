package com.example

import com.example.domain.CardScheme
import com.example.domain.CardValidatorEngine
import com.example.domain.CustomHttpClient
import com.example.domain.CustomHttpConfig
import com.example.domain.FileTextExtractor
import com.example.domain.HostCircuitBreaker
import com.example.domain.HttpCallRawResponse
import com.example.domain.HttpRequestMethod
import com.example.domain.RequestDeduplicator
import com.example.domain.ResultBucket
import com.example.domain.RetryAndBackoffPolicy
import com.example.domain.SupportedFileType
import com.example.domain.TtlLruCache
import com.example.domain.ValidationMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.random.Random

class ExampleUnitTest {

    @Test
    fun wildcardPanSolver_alwaysProducesValidLuhnChecksum() {
        val pattern = "453225005536xxx3"
        repeat(25) {
            val resolved = CardValidatorEngine.resolveWildcardPan(pattern)
            assertEquals(16, resolved.length)
            assertTrue(resolved.startsWith("453225005536"))
            assertTrue(resolved.endsWith("3"))
            assertTrue("Expected valid Luhn for $resolved", CardValidatorEngine.verifyLuhn(resolved))
            assertEquals(CardScheme.VISA, CardValidatorEngine.detectScheme(resolved))
        }
    }

    @Test
    fun expandPatternBatch_generatesRequestedUniqueValidLines() {
        val batch = CardValidatorEngine.expandPatternBatch("453225005536xxx3|08|2028|113", 10)
        assertEquals(10, batch.size)
        batch.forEach { line ->
            val parts = line.split('|')
            assertEquals(4, parts.size)
            assertTrue(CardValidatorEngine.verifyLuhn(parts[0]))
            assertEquals("08", parts[1])
            assertEquals("2028", parts[2])
            assertEquals("113", parts[3])
        }
    }

    @Test
    fun validateLine_classifiesLiveDieAndUnknownCorrectly() = runTest {
        val liveRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        assertEquals(ResultBucket.LIVE, liveRecord.bucket)
        assertEquals(CardScheme.VISA, liveRecord.scheme)

        val validPan = liveRecord.pan
        val brokenLastDigit = if (validPan.last() == '3') '4' else '3'
        val invalidPan = validPan.dropLast(1) + brokenLastDigit
        assertFalse(CardValidatorEngine.verifyLuhn(invalidPan))

        val dieRecord = CardValidatorEngine.validateLine(
            rawLine = "$invalidPan|08|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.DIE, dieRecord.bucket)

        val unknownRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.UNKNOWN, unknownRecord.bucket)
    }

    @Test
    fun fileTextExtractor_detectsSupportedTypesAndStripsHtml() {
        assertEquals(SupportedFileType.TXT, FileTextExtractor.detectFileType("batch.txt", "text/plain"))
        assertEquals(SupportedFileType.HTML, FileTextExtractor.detectFileType("export.html", "text/html"))
        assertEquals(SupportedFileType.HTML, FileTextExtractor.detectFileType("report.HTM", null))
        assertNull(FileTextExtractor.detectFileType("archive.zip", "application/zip"))

        val htmlSample = """
            <html>
              <head><title>Export 4532250055360000|01|2029|111</title><!-- 4532250055369999|02|2029|999 --><style>.hidden{display:none}</style><script>console.log('skip');</script></head>
              <body>
                <h1>Exported Batch</h1>
                <!-- ignored comment 5412750012340000|03|2029|000 -->
                <div>453225005536xxx3&#124;08&#124;2028&#124;113</div>
                <p>Entry: 5412750012345678&#x7c;11&#x7c;2029&#x7c;321 &amp; verified</p>
              </body>
            </html>
        """.trimIndent()

        val extractedLines = FileTextExtractor.extractLines(htmlSample, SupportedFileType.HTML)
        assertTrue(extractedLines.any { it.contains("453225005536xxx3|08|2028|113") })
        assertFalse(extractedLines.any { it.contains("console.log") })
        assertFalse(extractedLines.any { it.contains("4532250055360000") })
        assertFalse(extractedLines.any { it.contains("5412750012340000") })

        val delimited = FileTextExtractor.extractDelimitedLines(extractedLines)
        assertEquals(2, delimited.size)
        assertEquals("453225005536xxx3|08|2028|113", delimited[0])
        assertEquals("5412750012345678|11|2029|321", delimited[1])
    }

    @Test
    fun expandPatternBatch_defaultsToSchemeCvvLengthAndPadsMonth() = runTest {
        val amexBatch = CardValidatorEngine.expandPatternBatch("378282246310xxx", 5)
        assertEquals(5, amexBatch.size)
        amexBatch.forEach { line ->
            val parts = line.split('|')
            assertEquals(4, parts.size)
            assertTrue(CardValidatorEngine.verifyLuhn(parts[0]))
            assertEquals(CardScheme.AMEX, CardValidatorEngine.detectScheme(parts[0]))
            assertEquals(4, parts[3].length)
        }

        val paddedRecord = CardValidatorEngine.validateLine(
            rawLine = "453225005536xxx3|8|2028|113",
            mode = ValidationMode.LOCAL_DETERMINISTIC,
            webhookUrl = "",
            autoResolveWildcards = true
        )
        assertEquals("08", paddedRecord.month)
        assertTrue(paddedRecord.normalizedLine.contains("|08|2028|113"))
    }

    @Test
    fun customHttpClient_buildsGetJsonAndFormRequestsWithCustomHeaders() {
        val getConfig = CustomHttpConfig(
            url = "https://api.example.com/check?lista={lista}&yy={yy}",
            method = HttpRequestMethod.GET,
            headersText = "# auth header\nAuthorization: Bearer secret-123\nX-Scheme: {scheme}"
        )
        val getRequest = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = getConfig
        )
        assertEquals("GET", getRequest?.method)
        assertTrue(getRequest?.url.toString().contains("yy=28"))
        assertTrue(getRequest?.url.toString().contains("4532250055360003%7C08%7C2028%7C113"))
        assertEquals("Bearer secret-123", getRequest?.header("Authorization"))
        assertEquals("VISA", getRequest?.header("X-Scheme"))

        val jsonConfig = CustomHttpConfig(
            url = "https://api.example.com/v2/verify",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = """{"pan":"{card}","exp":"{mm}/{yy}","cvv":"{cvv}"}"""
        )
        val jsonRequest = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = jsonConfig
        )
        val jsonBuffer = Buffer()
        jsonRequest?.body?.writeTo(jsonBuffer)
        assertEquals("""{"pan":"4532250055360003","exp":"08/28","cvv":"113"}""", jsonBuffer.readUtf8())

        val formConfig = CustomHttpConfig(
            url = "https://api.example.com/form",
            method = HttpRequestMethod.POST_FORM
        )
        val formRequest = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = formConfig
        )
        val formBuffer = Buffer()
        formRequest?.body?.writeTo(formBuffer)
        assertTrue(formBuffer.readUtf8().contains("number=4532250055360003"))
    }

    @Test
    fun customHttpClient_classifiesCustomKeywordsAndNegatedStatusSafely() {
        val config = CustomHttpConfig(
            liveKeywords = "approved,charged,custom_ok",
            dieKeywords = "declined,insufficient,custom_fail"
        )

        assertEquals(
            ResultBucket.LIVE,
            CustomHttpClient.classifyResponse(200, """{"status":"custom_ok"}""", config).first
        )
        assertEquals(
            ResultBucket.DIE,
            CustomHttpClient.classifyResponse(200, """{"status":"not approved"}""", config).first
        )
        assertEquals(
            ResultBucket.DIE,
            CustomHttpClient.classifyResponse(200, "Card is NOT approved due to insufficient funds", config).first
        )
        assertEquals(
            ResultBucket.UNKNOWN,
            CustomHttpClient.classifyResponse(503, "Service Unavailable", config).first
        )
    }

    @Test
    fun validationMode_enforcesFixedDelaysAndAllowsCustomDelaysForOthers() {
        assertTrue(ValidationMode.CHKR_CC.hasFixedDelay)
        assertTrue(ValidationMode.PAYATE_MOCK.hasFixedDelay)
        assertTrue(ValidationMode.MULTI_API_AUTO.hasFixedDelay)
        assertFalse(ValidationMode.LOCAL_DETERMINISTIC.hasFixedDelay)
        assertFalse(ValidationMode.REMOTE_WEBHOOK.hasFixedDelay)

        assertEquals(10_000L, ValidationMode.CHKR_CC.resolveDelayMs(0L))
        assertEquals(10_000L, ValidationMode.CHKR_CC.resolveDelayMs(250L))
        assertEquals(3_000L, ValidationMode.PAYATE_MOCK.resolveDelayMs(55L))
        assertEquals(5_000L, ValidationMode.MULTI_API_AUTO.resolveDelayMs(1_500L))

        assertEquals(750L, ValidationMode.LOCAL_DETERMINISTIC.resolveDelayMs(750L))
        assertEquals(8_000L, ValidationMode.REMOTE_WEBHOOK.resolveDelayMs(8_000L))
        assertEquals(ValidationMode.MAX_CUSTOM_DELAY_MS, ValidationMode.REMOTE_WEBHOOK.resolveDelayMs(99_000L))
    }

    @Test
    fun customHttpClient_supportsPayloadAutoPostAndBinToken() {
        val autoPayloadConfig = CustomHttpConfig(
            url = "https://api.example.com/verify?bin={bin}",
            method = HttpRequestMethod.AUTO,
            bodyTemplate = CustomHttpConfig.DEFAULT_JSON_PAYLOAD
        )
        val req = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = autoPayloadConfig
        )
        assertEquals("POST", req?.method)
        assertTrue(req?.url.toString().contains("bin=453225"))
        val buf = Buffer()
        req?.body?.writeTo(buf)
        val bodyStr = buf.readUtf8()
        assertTrue(bodyStr.contains("\"bin\":\"453225\""))
        assertTrue(bodyStr.contains("\"lista\":\"4532250055360003|08|2028|113\""))
        assertTrue(bodyStr.contains("\"scheme\":\"VISA\""))
    }

    @Test
    fun ttlLruCache_evictsLeastRecentlyUsedAndExpiresByTtl() {
        var now = 1_000L
        val cache = TtlLruCache<String, String>(maxEntries = 2, timeProvider = { now })

        cache.put("a", "val-a", ttlMillis = 500L)
        cache.put("b", "val-b", ttlMillis = 500L)
        assertEquals("val-a", cache.get("a")) // touch 'a' so 'b' becomes LRU

        cache.put("c", "val-c", ttlMillis = 500L) // evicts 'b'
        assertNull(cache.get("b"))
        assertEquals("val-a", cache.get("a"))
        assertEquals("val-c", cache.get("c"))

        now = 1_600L // past 1_000 + 500 TTL
        assertNull(cache.get("a"))
        assertNull(cache.get("c"))
        assertEquals(0, cache.size())
    }

    @Test
    fun requestDeduplicator_coalescesConcurrentCallsAndCleansUp() = runTest {
        val dedup = RequestDeduplicator<String, String>()
        val executionCount = AtomicInteger(0)

        val results = (1..10).map {
            async {
                dedup.getOrExecute("453225") {
                    executionCount.incrementAndGet()
                    delay(25)
                    "BIN_RESULT"
                }
            }
        }.awaitAll()

        assertEquals(1, executionCount.get())
        assertEquals(1, results.count { !it.second }) // 1 primary execution
        assertEquals(9, results.count { it.second })  // 9 coalesced callers
        assertTrue(results.all { it.first == "BIN_RESULT" })
        assertEquals(0, dedup.activeInFlightCount())
    }

    @Test
    fun hostCircuitBreaker_transitionsClosedOpenHalfOpenAndRecovers() {
        var now = 10_000L
        val breaker = HostCircuitBreaker(
            failureThreshold = 3,
            openCooldownMs = 20_000L,
            timeProvider = { now }
        )
        val host = "api.example.com"

        assertEquals(HostCircuitBreaker.State.CLOSED, breaker.currentState(host))
        assertTrue(breaker.isRequestAllowed(host))

        assertFalse(breaker.recordTransientFailure(host))
        assertFalse(breaker.recordTransientFailure(host))
        assertTrue(breaker.recordTransientFailure(host))

        assertEquals(HostCircuitBreaker.State.OPEN, breaker.currentState(host))
        assertFalse(breaker.isRequestAllowed(host))
        assertTrue(breaker.remainingCooldownMs(host) > 0L)

        now = 31_000L // after 20s cooldown
        assertEquals(HostCircuitBreaker.State.HALF_OPEN, breaker.currentState(host))
        assertTrue(breaker.isRequestAllowed(host)) // single probe allowed
        assertFalse(breaker.isRequestAllowed(host)) // second concurrent probe blocked

        breaker.recordSuccess(host)
        assertEquals(HostCircuitBreaker.State.CLOSED, breaker.currentState(host))
        assertTrue(breaker.isRequestAllowed(host))
    }

    @Test
    fun retryAndBackoffPolicy_computesExponentialBackoffAndParsesRetryAfter() {
        val d1 = RetryAndBackoffPolicy.computeBackoffDelayMs(
            attempt = 1,
            baseDelayMs = 400L,
            maxDelayMs = 5_000L,
            random = Random(1)
        )
        val d2 = RetryAndBackoffPolicy.computeBackoffDelayMs(
            attempt = 2,
            baseDelayMs = 400L,
            maxDelayMs = 5_000L,
            random = Random(1)
        )
        val d3 = RetryAndBackoffPolicy.computeBackoffDelayMs(
            attempt = 3,
            baseDelayMs = 400L,
            maxDelayMs = 5_000L,
            random = Random(1)
        )
        assertTrue(d1 in 400L..533L)
        assertTrue(d2 in 800L..1_066L)
        assertTrue(d3 in 1_600L..2_133L)

        assertEquals(12_000L, RetryAndBackoffPolicy.parseRetryAfterMs("12"))
        assertEquals(
            7_000L,
            RetryAndBackoffPolicy.parseRetryAfterMs(
                retryAfterHeader = null,
                responseBody = """{"ok":false,"error_code":429,"parameters":{"retry_after":7}}"""
            )
        )
    }

    @Test
    fun customHttpClient_compressesJsonPayloadWithGzipWhenEnabled() {
        val gzipConfig = CustomHttpConfig(
            url = "https://api.example.com/v2/batch",
            method = HttpRequestMethod.POST_JSON,
            bodyTemplate = """{"lista":"{lista}","bin":"{bin}"}""",
            compressBodyGzip = true
        )
        val req = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = gzipConfig
        )
        assertEquals("gzip", req?.header("Content-Encoding"))
        val compressedBuf = Buffer()
        req!!.body!!.writeTo(compressedBuf)
        val compressedBytes = compressedBuf.readByteArray()

        val decompressed = GZIPInputStream(ByteArrayInputStream(compressedBytes))
            .bufferedReader(Charsets.UTF_8)
            .readText()
        assertEquals("""{"lista":"4532250055360003|08|2028|113","bin":"453225"}""", decompressed)
    }

    @Test
    fun detectScheme_prioritizesDiscoverRangeOverUnionPayPrefix() {
        assertEquals(CardScheme.DISCOVER, CardValidatorEngine.detectScheme("6221260000000000"))
        assertEquals(CardScheme.DISCOVER, CardValidatorEngine.detectScheme("6229250000000000"))
        assertEquals(CardScheme.UNIONPAY, CardValidatorEngine.detectScheme("6221250000000000"))
        assertEquals(CardScheme.UNIONPAY, CardValidatorEngine.detectScheme("6229260000000000"))
        assertEquals(CardScheme.UNIONPAY, CardValidatorEngine.detectScheme("6210000000000000"))
    }

    @Test
    fun retryAndBackoffPolicy_releasesHalfOpenProbeOnCancellation() = runTest {
        var now = 0L
        val host = "probe-cancel.example.com"
        val breaker = HostCircuitBreaker(
            failureThreshold = 1,
            openDurationMs = 1_000L,
            clock = { now }
        )
        breaker.recordTransientFailure(host)
        assertEquals(HostCircuitBreaker.State.OPEN, breaker.currentState(host))

        now = 2_000L
        assertEquals(HostCircuitBreaker.State.HALF_OPEN, breaker.currentState(host))

        val probeStarted = AtomicInteger(0)
        val job = launch {
            RetryAndBackoffPolicy.executeWithExponentialBackoff(
                host = host,
                maxRetries = 0,
                circuitBreaker = breaker,
                callBlock = {
                    probeStarted.incrementAndGet()
                    delay(10_000L)
                    HttpCallRawResponse(code = 200, body = "ok")
                },
                onResult = { _, _, _ -> "done" },
                onCircuitOpen = { "open" },
                onFailure = { _, _ -> "fail" }
            )
        }
        testScheduler.advanceTimeBy(50L)
        assertEquals(1, probeStarted.get())
        // While suspended, probe slot is claimed
        assertFalse(breaker.isRequestAllowed(host))

        job.cancel(CancellationException("user cancelled"))
        job.join()

        // After cancellation, half-open probe lock must be released for the next caller
        assertTrue(breaker.isRequestAllowed(host))
    }
}
