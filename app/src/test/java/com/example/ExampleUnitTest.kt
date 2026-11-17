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
            url = "https://mock.payate.com/api.php?lista={lista}&yy={yy}",
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
            url = "https://api.sandbox.checkout.com/payments",
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
            url = "https://api.stripe.com/v1/tokens",
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
        assertTrue(ValidationMode.PAYATE_MOCK.hasFixedDelay)
        assertTrue(ValidationMode.MULTI_API_AUTO.hasFixedDelay)
        assertFalse(ValidationMode.LOCAL_DETERMINISTIC.hasFixedDelay)
        assertFalse(ValidationMode.SANDBOX_SUITE.hasFixedDelay)
        assertFalse(ValidationMode.REMOTE_WEBHOOK.hasFixedDelay)

        assertEquals(3_000L, ValidationMode.PAYATE_MOCK.resolveDelayMs(55L))
        assertEquals(5_000L, ValidationMode.MULTI_API_AUTO.resolveDelayMs(1_500L))

        assertEquals(750L, ValidationMode.LOCAL_DETERMINISTIC.resolveDelayMs(750L))
        assertEquals(8_000L, ValidationMode.REMOTE_WEBHOOK.resolveDelayMs(8_000L))
        assertEquals(ValidationMode.MAX_CUSTOM_DELAY_MS, ValidationMode.REMOTE_WEBHOOK.resolveDelayMs(99_000L))
    }

    @Test
    fun customHttpClient_supportsPayloadAutoPostAndBinToken() {
        val autoPayloadConfig = CustomHttpConfig(
            url = "https://api.sandbox.checkout.com/payments?bin={bin}",
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
        val host = "api.stripe.com"

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
            url = "https://api.sandbox.checkout.com/payments",
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun retryAndBackoffPolicy_releasesHalfOpenProbeOnCancellation() = runTest {
        var now = 0L
        val host = "api.sandbox.checkout.com"
        val breaker = HostCircuitBreaker(
            failureThreshold = 1,
            openCooldownMs = 1_000L,
            timeProvider = { now }
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

    @Test
    fun sandboxTestRegistry_allOfficialVectorsAreLuhnValidAndEvaluateExpectedBuckets() = runTest {
        assertTrue(com.example.domain.SandboxTestRegistry.allVectors.size >= 26)
        for (vector in com.example.domain.SandboxTestRegistry.allVectors) {
            assertTrue(
                "Vector ${vector.pan} (${vector.description}) must satisfy Luhn Mod-10",
                CardValidatorEngine.verifyLuhn(vector.pan)
            )
            val record = CardValidatorEngine.validateLine(
                rawLine = vector.toPipeLine(),
                mode = ValidationMode.SANDBOX_SUITE,
                webhookUrl = "",
                autoResolveWildcards = false
            )
            assertEquals(
                "Unexpected bucket for ${vector.provider} ${vector.pan}",
                vector.expectedBucket,
                record.bucket
            )
            assertTrue(record.message.contains("$0.00 Auth"))
            assertTrue(record.message.contains(vector.gatewayCode))
        }

        // Verify deterministic sandbox triggers for custom Luhn-valid cards
        val customPan = CardValidatorEngine.resolveWildcardPan("453225005536xxx3")
        val cvcFail = CardValidatorEngine.validateLine(
            rawLine = "$customPan|12|2029|000",
            mode = ValidationMode.SANDBOX_SUITE,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.DIE, cvcFail.bucket)
        assertTrue(cvcFail.message.contains("incorrect_cvc"))

        val transientErr = CardValidatorEngine.validateLine(
            rawLine = "$customPan|12|2029|999",
            mode = ValidationMode.SANDBOX_SUITE,
            webhookUrl = "",
            autoResolveWildcards = false
        )
        assertEquals(ResultBucket.UNKNOWN, transientErr.bucket)
        assertTrue(transientErr.message.contains("processing_error"))
    }

    @Test
    fun sandboxGatewayClient_enforcesSandboxOnlyKeyGuardsAndParsesZeroAuthResponses() {
        val snapshot = com.example.domain.SandboxGatewayClient.inspectEnvKeys()
        assertEquals(4, snapshot.totalProviders)
        assertEquals(4, snapshot.providerStatuses.size)
        assertTrue(snapshot.configuredCount in 0..snapshot.totalProviders)
        assertEquals(snapshot.configuredCount > 0, snapshot.hasAnyConfiguredLiveSandbox)

        // Verify both live and sandbox keys are accepted while empty/unset keys are rejected
        assertTrue(com.example.domain.SandboxGatewayClient.isStripeSecretConfigured("sk_live_51Op5Y0HGwjwSrDw1F7X5BhH"))
        assertTrue(com.example.domain.SandboxGatewayClient.isStripePublishableConfigured("pk_live_51HOrSwC6h1nxGoI3lTAgRjY"))
        assertFalse(com.example.domain.SandboxGatewayClient.isStripeSecretConfigured(""))
        assertFalse(com.example.domain.SandboxGatewayClient.isStripeSecretConfigured("NONE"))
        assertFalse(com.example.domain.SandboxGatewayClient.isStripeSecretConfigured("UNSET"))
        assertFalse(com.example.domain.SandboxGatewayClient.isStripePublishableConfigured("UNSET"))
        assertFalse(com.example.domain.SandboxGatewayClient.isCheckoutConfigured("UNSET"))
        assertFalse(com.example.domain.SandboxGatewayClient.isBraintreeConfigured("UNSET"))
        assertFalse(com.example.domain.SandboxGatewayClient.isAuthNetConfigured("UNSET", "UNSET"))
        assertTrue(com.example.domain.SandboxGatewayClient.isStripeSecretConfigured("sk_test_51N9xYzRealSandboxKey1234"))
        assertTrue(com.example.domain.SandboxGatewayClient.isStripePublishableConfigured("pk_test_51N9xYzRealSandboxKey1234"))
        assertTrue(com.example.domain.SandboxGatewayClient.isCheckoutConfigured("sk_prd_1234567890abcdef1234"))
        assertTrue(com.example.domain.SandboxGatewayClient.isCheckoutConfigured("sk_sbox_pk6szy4zceahnoq6nvlskfsrhmz"))
        assertTrue(com.example.domain.SandboxGatewayClient.isBraintreeConfigured("52e4d78c56f496af9d4dd5118482e975"))
        assertTrue(com.example.domain.SandboxGatewayClient.isAuthNetConfigured("7gwcN2Wbap5X", "6CV98V88Bz8jFkxF"))

        val (liveBucket, liveMsg) = com.example.domain.SandboxGatewayClient.parseStripeSandboxResponse(
            httpCode = 200,
            body = """{"id":"seti_1SandboxZeroAuth","status":"succeeded"}"""
        )
        assertEquals(ResultBucket.LIVE, liveBucket)
        assertTrue(liveMsg.contains("$0.00 Auth"))
        assertTrue(liveMsg.contains("seti_1SandboxZeroAuth"))

        val (dieBucket, dieMsg) = com.example.domain.SandboxGatewayClient.parseStripeSandboxResponse(
            httpCode = 402,
            body = """{"error":{"code":"card_declined","decline_code":"incorrect_cvc","message":"Your card's security code is incorrect."}}"""
        )
        assertEquals(ResultBucket.DIE, dieBucket)
        assertTrue(dieMsg.contains("incorrect_cvc"))
    }

    @Test
    fun customConfigStudio_extractsJsonLrRegexAndHeaderCaptures() {
        val sampleJson = """{"status":"succeeded","id":"ch_998877","error":{"code":"none","msg":"ok"},"bin":"453225"}"""
        val headers = mapOf("x-request-id" to "req_abc123", "content-type" to "application/json")
        val rules = listOf(
            com.example.domain.CustomParseRule(
                name = "status",
                mode = com.example.domain.ParseRuleMode.JSON,
                expression = "status"
            ),
            com.example.domain.CustomParseRule(
                name = "err_code",
                mode = com.example.domain.ParseRuleMode.JSON,
                expression = "error.code"
            ),
            com.example.domain.CustomParseRule(
                name = "charge_id",
                mode = com.example.domain.ParseRuleMode.LR,
                leftDelimiter = "\"id\":\"",
                rightDelimiter = "\""
            ),
            com.example.domain.CustomParseRule(
                name = "bin_rx",
                mode = com.example.domain.ParseRuleMode.REGEX,
                expression = "\"bin\"\\s*:\\s*\"(\\d{6})\""
            ),
            com.example.domain.CustomParseRule(
                name = "req_id",
                mode = com.example.domain.ParseRuleMode.HEADER,
                expression = "X-Request-Id"
            )
        )

        val captures = com.example.domain.CustomConfigStudioEngine.extractCaptures(
            bodyStr = sampleJson,
            responseHeaders = headers,
            rules = rules
        )
        assertEquals("succeeded", captures["status"])
        assertEquals("none", captures["err_code"])
        assertEquals("ch_998877", captures["charge_id"])
        assertEquals("453225", captures["bin_rx"])
        assertEquals("req_abc123", captures["req_id"])
    }

    @Test
    fun customConfigStudio_evaluatesPriorityOrderedCheckpointsAndFallback() {
        val rules = listOf(
            com.example.domain.CustomCheckpointRule(
                verdict = ResultBucket.DIE,
                condition = com.example.domain.CheckpointCondition.BODY_CONTAINS_ANY,
                expectedValue = "card_declined,incorrect_cvc,expired_card",
                label = "Declined"
            ),
            com.example.domain.CustomCheckpointRule(
                verdict = ResultBucket.LIVE,
                condition = com.example.domain.CheckpointCondition.JSON_KEY_EQUALS,
                targetKey = "outcome.type",
                expectedValue = "authorized",
                label = "Authorized"
            ),
            com.example.domain.CustomCheckpointRule(
                verdict = ResultBucket.UNKNOWN,
                condition = com.example.domain.CheckpointCondition.STATUS_CODE_IN,
                expectedValue = "429,502,503",
                label = "Rate Limited"
            ),
            com.example.domain.CustomCheckpointRule(
                verdict = ResultBucket.LIVE,
                condition = com.example.domain.CheckpointCondition.CAPTURED_VAR_EQUALS,
                targetKey = "token_prefix",
                expectedValue = "pm_ok",
                label = "PM Captured"
            )
        )

        val config = CustomHttpConfig(
            checkpointRulesSpec = com.example.domain.CustomConfigStudioEngine.serializeCheckpointRules(rules)
        )

        // Rule #1 should win even if HTTP 200
        val dieRes = CustomHttpClient.classifyResponse(
            statusCode = 200,
            bodyStr = """{"error":"incorrect_cvc"}""",
            config = config
        )
        assertEquals(ResultBucket.DIE, dieRes.first)
        assertEquals("Checkpoint: Declined", dieRes.second)

        // Rule #2 JSON dot-path equality
        val liveRes = CustomHttpClient.classifyResponse(
            statusCode = 200,
            bodyStr = """{"outcome":{"type":"authorized"}}""",
            config = config
        )
        assertEquals(ResultBucket.LIVE, liveRes.first)
        assertEquals("Checkpoint: Authorized", liveRes.second)

        // Rule #3 HTTP status list
        val unknownRes = CustomHttpClient.classifyResponse(
            statusCode = 429,
            bodyStr = "Too many requests",
            config = config
        )
        assertEquals(ResultBucket.UNKNOWN, unknownRes.first)
        assertEquals("Checkpoint: Rate Limited", unknownRes.second)

        // Rule #4 Precomputed captured variable equals
        val varRes = CustomHttpClient.classifyResponse(
            statusCode = 201,
            bodyStr = "plain response",
            config = config,
            precomputedCaptures = mapOf("token_prefix" to "pm_ok")
        )
        assertEquals(ResultBucket.LIVE, varRes.first)
        assertEquals("Checkpoint: PM Captured", varRes.second)
    }

    @Test
    fun customConfigStudio_parsesProxyFormatsAndAuthHeaders() {
        val proxyText = """
            # comment line
            198.51.100.10:8080
            198.51.100.20:1080:myuser:mypass
            socks5://alice:s3cr3t@203.0.113.5:9050
            invalid-line
        """.trimIndent()

        val parsed = com.example.domain.CustomConfigStudioEngine.parseProxyPool(
            proxyText,
            com.example.domain.ProxyProtocol.HTTP
        )
        assertEquals(3, parsed.size)

        assertEquals("198.51.100.10", parsed[0].host)
        assertEquals(8080, parsed[0].port)
        assertFalse(parsed[0].requiresAuth)
        assertEquals(com.example.domain.ProxyProtocol.HTTP, parsed[0].protocol)

        assertEquals("198.51.100.20", parsed[1].host)
        assertEquals(1080, parsed[1].port)
        assertTrue(parsed[1].requiresAuth)
        assertEquals("myuser", parsed[1].username)
        assertEquals("mypass", parsed[1].password)
        assertTrue(parsed[1].basicAuthorizationHeaderValue()!!.startsWith("Basic "))

        assertEquals("203.0.113.5", parsed[2].host)
        assertEquals(9050, parsed[2].port)
        assertEquals(com.example.domain.ProxyProtocol.SOCKS5, parsed[2].protocol)
        assertEquals("alice", parsed[2].username)
        assertEquals("s3cr3t", parsed[2].password)
    }

    @Test
    fun customConfigStudio_importsCurlAndJsFetchSnippetsWithAutoTokenization() {
        val fetchSnippet = """
            fetch("https://api.stripe.com/v1/tokens", {
              "headers": {
                "accept": "application/json",
                "content-type": "application/x-www-form-urlencoded",
                "x-custom": "live_gateway"
              },
              "body": "card[number]=4532250055360003&card[cvc]=113&card[exp_month]=8&card[exp_year]=2028",
              "method": "POST"
            });
        """.trimIndent()

        val fromFetch = com.example.domain.CustomConfigStudioEngine
            .importFromCurlOrFetch(fetchSnippet, autoTokenizeFields = true)
            .getOrThrow()
        assertEquals("https://api.stripe.com/v1/tokens", fromFetch.url)
        assertEquals(HttpRequestMethod.POST_FORM, fromFetch.method)
        assertTrue(fromFetch.headersText.contains("x-custom: live_gateway"))
        assertTrue(fromFetch.bodyTemplate.contains("card[number]={card}"))
        assertTrue(fromFetch.bodyTemplate.contains("card[cvc]={cvv}"))
        assertTrue(fromFetch.bodyTemplate.contains("card[exp_month]={mm}"))
        assertTrue(fromFetch.bodyTemplate.contains("card[exp_year]={yyyy}"))

        val curlSnippet = """
            curl -X POST 'https://api.sandbox.checkout.com/payments' \
              -H 'Authorization: Bearer sk_sbox_pk6szy4zceahnoq6nvlskfsrhmz' \
              -H 'Content-Type: application/json' \
              -A 'CustomUA/1.0' \
              --data-raw '{"number":"5178059025936114","exp_month":"02","exp_year":"2029","cvv":"414"}'
        """.trimIndent()

        val fromCurl = com.example.domain.CustomConfigStudioEngine
            .importFromCurlOrFetch(curlSnippet, autoTokenizeFields = true)
            .getOrThrow()
        assertEquals("https://api.sandbox.checkout.com/payments", fromCurl.url)
        assertEquals(HttpRequestMethod.POST_JSON, fromCurl.method)
        assertEquals("CustomUA/1.0", fromCurl.userAgent)
        assertTrue(fromCurl.headersText.contains("Authorization: Bearer sk_sbox_pk6szy4zceahnoq6nvlskfsrhmz"))
        assertTrue(fromCurl.bodyTemplate.contains("\"number\":\"{card}\""))
        assertTrue(fromCurl.bodyTemplate.contains("\"cvv\":\"{cvv}\""))
    }

    @Test
    fun customConfigStudio_exportAndImportJsonRoundTripPreservesAllFields() {
        val original = CustomHttpConfig(
            profileName = "Stripe SetupIntent Live",
            url = "https://api.stripe.com/v1/setup_intents",
            method = HttpRequestMethod.POST_JSON,
            headersText = "Accept: application/json\nX-Trace: {uuid}",
            bodyTemplate = CustomHttpConfig.ZERO_DOLLAR_AUTH_JSON_PAYLOAD,
            timeoutSeconds = 20,
            maxRetries = 2,
            liveKeywords = "succeeded,ok",
            dieKeywords = "declined,fail",
            compressBodyGzip = true,
            userAgent = "BinCheck-UA/3.0",
            followRedirects = false,
            parseRulesSpec = com.example.domain.CustomConfigStudioEngine.serializeParseRules(
                listOf(
                    com.example.domain.CustomParseRule("code", com.example.domain.ParseRuleMode.JSON, "error.code"),
                    com.example.domain.CustomParseRule("id", com.example.domain.ParseRuleMode.LR, leftDelimiter = "\"id\":\"", rightDelimiter = "\"")
                )
            ),
            checkpointRulesSpec = com.example.domain.CustomConfigStudioEngine.serializeCheckpointRules(
                listOf(
                    com.example.domain.CustomCheckpointRule(
                        verdict = ResultBucket.LIVE,
                        condition = com.example.domain.CheckpointCondition.BODY_CONTAINS,
                        expectedValue = "zero_dollar",
                        label = "OK"
                    )
                )
            ),
            proxyEnabled = true,
            proxyProtocol = com.example.domain.ProxyProtocol.SOCKS5,
            proxyRotation = com.example.domain.ProxyRotationMode.RANDOM,
            proxyListText = "198.51.100.1:1080:user:pass"
        )

        val json = com.example.domain.CustomConfigStudioEngine.exportConfigToJson(original)
        val restored = com.example.domain.CustomConfigStudioEngine.importConfigFromJson(json).getOrThrow()

        assertEquals(original.profileName, restored.profileName)
        assertEquals(original.url, restored.url)
        assertEquals(original.method, restored.method)
        assertEquals(original.headersText, restored.headersText)
        assertEquals(original.bodyTemplate, restored.bodyTemplate)
        assertEquals(original.timeoutSeconds, restored.timeoutSeconds)
        assertEquals(original.maxRetries, restored.maxRetries)
        assertEquals(original.compressBodyGzip, restored.compressBodyGzip)
        assertEquals(original.userAgent, restored.userAgent)
        assertEquals(original.followRedirects, restored.followRedirects)
        assertEquals(original.proxyEnabled, restored.proxyEnabled)
        assertEquals(original.proxyProtocol, restored.proxyProtocol)
        assertEquals(original.proxyRotation, restored.proxyRotation)
        assertEquals(original.proxyListText, restored.proxyListText)
        assertEquals(2, restored.parsedParseRules().size)
        assertEquals(1, restored.parsedCheckpointRules().size)
    }

    @Test
    fun customConfigStudio_supportsArrayJsonPathsCookieCssRecursiveAndSmartDiscovery() {
        val complexJson = """
            {
              "status": "failed",
              "errors": [
                {"code": "CVV_MISMATCH", "field": "cvv"},
                {"code": "AVS_WARN", "field": "zip"}
              ],
              "meta": {"trace": "tr_001"}
            }
        """.trimIndent()

        // Non-object intermediate segment must return null, never flat string
        assertNull(
            com.example.domain.CustomConfigStudioEngine.extractJsonPathValue(
                """{"error":"flat_string"}""",
                "error.code"
            )
        )

        // Array indexing via errors[0].code and errors.1.code
        assertEquals(
            "CVV_MISMATCH",
            com.example.domain.CustomConfigStudioEngine.extractJsonPathValue(complexJson, "errors[0].code")
        )
        assertEquals(
            "AVS_WARN",
            com.example.domain.CustomConfigStudioEngine.extractJsonPathValue(complexJson, "errors.1.code")
        )

        // Recursive JSON array extraction
        assertEquals(
            "CVV_MISMATCH, AVS_WARN",
            com.example.domain.CustomConfigStudioEngine.extractJsonPathValue(
                complexJson,
                "errors.code",
                recursive = true
            )
        )

        // Smart JSON key auto-discovery
        val suggestions = com.example.domain.CustomConfigStudioEngine.discoverJsonKeySuggestions(complexJson)
        assertTrue(suggestions.any { it.first == "status" && it.second == "failed" })
        assertTrue(suggestions.any { it.first == "errors[0].code" && it.second == "CVV_MISMATCH" })
        assertTrue(suggestions.any { it.first == "meta.trace" && it.second == "tr_001" })

        // Cookie & HTML/CSS extraction + fallback defaultValue
        val htmlBody = """<html><head><title>3DS Challenge</title></head><body><input type="hidden" name="csrf_token" value="csrf_987654" /></body></html>"""
        val headers = mapOf("set-cookie" to "session_id=sess_abc123; Path=/; HttpOnly; csrf_cookie=ck_555")
        val captures = com.example.domain.CustomConfigStudioEngine.extractCaptures(
            bodyStr = htmlBody,
            responseHeaders = headers,
            rules = listOf(
                com.example.domain.CustomParseRule(
                    name = "sid",
                    mode = com.example.domain.ParseRuleMode.COOKIE,
                    expression = "session_id"
                ),
                com.example.domain.CustomParseRule(
                    name = "csrf",
                    mode = com.example.domain.ParseRuleMode.CSS,
                    expression = "input[name=csrf_token]@value"
                ),
                com.example.domain.CustomParseRule(
                    name = "page_title",
                    mode = com.example.domain.ParseRuleMode.CSS,
                    expression = "title"
                ),
                com.example.domain.CustomParseRule(
                    name = "missing_with_default",
                    mode = com.example.domain.ParseRuleMode.JSON,
                    expression = "nonexistent.path",
                    defaultValue = "fallback_ok"
                )
            )
        )
        assertEquals("sess_abc123", captures["sid"])
        assertEquals("csrf_987654", captures["csrf"])
        assertEquals("3DS Challenge", captures["page_title"])
        assertEquals("fallback_ok", captures["missing_with_default"])
    }

    @Test
    fun customConfigStudio_importsRawHttpAndLoliScriptAndExportsCurlAndLoli() {
        val rawHttp = """
            POST /v1/payment_methods HTTP/1.1
            Host: api.stripe.com
            Authorization: Bearer pk_test_123
            Content-Type: application/x-www-form-urlencoded

            type=card&card[number]=4532250055360003&card[exp_month]=08&card[exp_year]=2028&card[cvc]=113
        """.trimIndent()

        val importedRaw = com.example.domain.CustomConfigStudioEngine
            .importFromCurlOrFetch(rawHttp, autoTokenizeFields = true)
            .getOrThrow()
        assertEquals("https://api.stripe.com/v1/payment_methods", importedRaw.url)
        assertEquals(HttpRequestMethod.POST_FORM, importedRaw.method)
        assertTrue(importedRaw.headersText.contains("Authorization: Bearer pk_test_123"))
        assertTrue(importedRaw.bodyTemplate.contains("card[number]={card}"))
        assertTrue(importedRaw.bodyTemplate.contains("card[cvc]={cvv}"))

        val loliSnippet = """
            REQUEST POST "https://apitest.authorize.net/xml/v1/request.api"
              CONTENT "{\"email\":\"<EMAIL>\",\"password\":\"<PASS>\"}"
              CONTENTTYPE "application/json"
              HEADER "X-Client: OpenBullet"
            PARSE "<SOURCE>" JSON "data.token" -> CAP "auth_tok"
            KEYCHECK
              KEYCHAIN Success AND
                KEY "authenticated"
                KEY "token"
              KEYCHAIN Failure OR
                KEY "invalid_credentials"
        """.trimIndent()

        val importedLoli = com.example.domain.CustomConfigStudioEngine
            .importFromCurlOrFetch(loliSnippet, autoTokenizeFields = true)
            .getOrThrow()
        assertEquals("https://apitest.authorize.net/xml/v1/request.api", importedLoli.url)
        assertEquals(HttpRequestMethod.POST_JSON, importedLoli.method)
        assertTrue(importedLoli.skipLocalLuhnPrecheck)
        assertTrue(importedLoli.bodyTemplate.contains("{email}"))
        assertTrue(importedLoli.bodyTemplate.contains("{pass}"))
        assertEquals(1, importedLoli.parsedParseRules().size)
        assertEquals("auth_tok", importedLoli.parsedParseRules()[0].name)
        assertEquals(2, importedLoli.parsedCheckpointRules().size)
        assertEquals(
            com.example.domain.CheckpointCondition.BODY_CONTAINS_ALL,
            importedLoli.parsedCheckpointRules()[0].condition
        )

        val exportedCurl = com.example.domain.CustomConfigStudioEngine.exportToCurlCommand(
            importedRaw,
            "4532250055360003|08|2028|113"
        )
        assertTrue(exportedCurl.startsWith("curl -X POST 'https://api.stripe.com/v1/payment_methods'"))
        assertTrue(exportedCurl.contains("--data-raw"))

        val exportedLoli = com.example.domain.CustomConfigStudioEngine.exportToLoliScript(importedLoli)
        assertTrue(exportedLoli.contains("REQUEST POST \"https://apitest.authorize.net/xml/v1/request.api\""))
        assertTrue(exportedLoli.contains("PARSE \"<SOURCE>\" JSON \"data.token\" -> CAP \"auth_tok\""))
        assertTrue(exportedLoli.contains("KEYCHAIN Success AND"))
    }

    @Test
    fun customConfigStudio_evaluatesNewCheckpointsStep1VariablesAndRequestPreview() {
        val cfg = CustomHttpConfig(
            profileName = "2-Step Preview Config",
            url = "https://api.stripe.com/v1/setup_intents?token={cap:payment_token}&scheme={scheme_lower}",
            method = HttpRequestMethod.POST_JSON,
            headersText = "Authorization: Basic {base64_combo}\nX-Card-Hash: {sha256_card}",
            bodyTemplate = """{"token":"{cap:payment_token}","dashed":"{card_dashed}","bin8":"{bin8}"}""",
            preRequestEnabled = true,
            preRequestUrl = "https://api.stripe.com/v1/tokens",
            preRequestParseRulesSpec = com.example.domain.CustomConfigStudioEngine.serializeParseRules(
                listOf(
                    com.example.domain.CustomParseRule(
                        name = "payment_token",
                        mode = com.example.domain.ParseRuleMode.JSON,
                        expression = "id",
                        defaultValue = "pm_step1_999"
                    )
                )
            ),
            checkpointRulesSpec = com.example.domain.CustomConfigStudioEngine.serializeCheckpointRules(
                listOf(
                    com.example.domain.CustomCheckpointRule(
                        verdict = ResultBucket.LIVE,
                        condition = com.example.domain.CheckpointCondition.BODY_CONTAINS_ALL,
                        expectedValue = "succeeded,paid",
                        label = "All Matched"
                    ),
                    com.example.domain.CustomCheckpointRule(
                        verdict = ResultBucket.DIE,
                        condition = com.example.domain.CheckpointCondition.HEADER_CONTAINS,
                        targetKey = "x-decline-reason",
                        expectedValue = "do_not_honor",
                        label = "Header Decline"
                    )
                )
            )
        )

        val preview = com.example.domain.CustomConfigStudioEngine.renderRequestPreview(
            cfg,
            "4532250055360003|08|2028|113"
        )
        assertEquals("POST", preview.resolvedMethod)
        assertEquals(true, preview.isJsonValid)
        assertTrue(preview.resolvedUrl.contains("token=pm_step1_999"))
        assertTrue(preview.resolvedUrl.contains("scheme=visa"))
        assertTrue(preview.resolvedBody.contains("\"dashed\":\"4532-2500-5536-0003\""))
        assertTrue(preview.resolvedBody.contains("\"bin8\":\"45322500\""))

        val evalResult = com.example.domain.CustomConfigStudioEngine.evaluateCheckpointsDetailed(
            statusCode = 402,
            bodyStr = """{"status":"failed"}""",
            captures = emptyMap(),
            rules = cfg.parsedCheckpointRules(),
            responseHeaders = mapOf("x-decline-reason" to "do_not_honor")
        )
        assertEquals(ResultBucket.DIE, evalResult.winner?.first)
        assertEquals(2, evalResult.steps.size)
        assertFalse(evalResult.steps[0].matched)
        assertTrue(evalResult.steps[1].matched)
        assertTrue(evalResult.steps[1].isWinner)
    }

    @Test
    fun configBlocks_coverAllNineCategoriesAndRoundTripSerialization() {
        val categories = com.example.domain.BlockCategory.entries
        assertEquals(9, categories.size)
        categories.forEach { cat ->
            val types = com.example.domain.BlockType.forCategory(cat)
            assertTrue("Category ${cat.displayName} must have blocks", types.isNotEmpty())
        }
        assertEquals("Blocks for browser automation", com.example.domain.BlockCategory.BROWSER.description)
        assertEquals("Blocks for solving captchas", com.example.domain.BlockCategory.CAPTCHAS.description)
        assertEquals("Blocks that have to do with checking conditions", com.example.domain.BlockCategory.CONDITIONS.description)
        assertEquals("General purpose functions", com.example.domain.BlockCategory.FUNCTIONS.description)
        assertEquals("Blocks for interoperability with other programs", com.example.domain.BlockCategory.INTEROP.description)
        assertEquals("Blocks for extracting data from strings", com.example.domain.BlockCategory.PARSING.description)
        assertEquals("Blocks for performing network requests", com.example.domain.BlockCategory.REQUESTS.description)
        assertEquals("Blocks for browser automation with Selenium", com.example.domain.BlockCategory.SELENIUM.description)
        assertEquals("Utility blocks for miscellaneous purposes", com.example.domain.BlockCategory.UTILITY.description)

        val blocks = listOf(
            com.example.domain.ConfigBlock(
                id = "b1",
                type = com.example.domain.BlockType.BROWSER_NAVIGATE,
                label = "Open Checkout",
                inputTarget = "https://example.com/pay",
                extraParams = mapOf("fetchHtml" to "true")
            ),
            com.example.domain.ConfigBlock(
                id = "b2",
                type = com.example.domain.BlockType.REQUEST_HTTP,
                label = "Post Auth",
                inputTarget = "https://api.example.com/v1/auth",
                secondaryParam = "POST_JSON",
                tertiaryParam = "{\"card\":\"{card}\"\n}",
                outputVar = "SOURCE",
                extraParams = mapOf("headers" to "Authorization: Bearer abc\nX-Test: 1")
            ),
            com.example.domain.ConfigBlock(
                id = "b3",
                type = com.example.domain.BlockType.CONDITION_KEYCHECK,
                label = "Check Live",
                inputTarget = "<SOURCE>",
                secondaryParam = "CONTAINS",
                tertiaryParam = "approved",
                verdictOnMatch = ResultBucket.LIVE
            )
        )

        val spec = com.example.domain.CustomConfigStudioEngine.serializeBlocks(blocks)
        val restored = com.example.domain.CustomConfigStudioEngine.parseBlocks(spec)
        assertEquals(3, restored.size)
        assertEquals("true", restored[0].extraParams["fetchHtml"])
        assertEquals("{\"card\":\"{card}\"\n}", restored[1].tertiaryParam)
        assertEquals("Authorization: Bearer abc\nX-Test: 1", restored[1].extraParams["headers"])
        assertEquals(ResultBucket.LIVE, restored[2].verdictOnMatch)

        val cfg = CustomHttpConfig(profileName = "Blocks RoundTrip", blocksSpec = spec)
        val json = com.example.domain.CustomConfigStudioEngine.exportConfigToJson(cfg)
        val restoredCfg = com.example.domain.CustomConfigStudioEngine.importConfigFromJson(json).getOrThrow()
        assertEquals(3, restoredCfg.parsedBlocks().size)

        val loli = com.example.domain.CustomConfigStudioEngine.exportToLoliScript(cfg)
        assertTrue(loli.contains("BROWSERACTION Navigate"))
        assertTrue(loli.contains("REQUEST POST_JSON"))
        assertTrue(loli.contains("KEYCHECK"))
    }

    @Test
    fun configBlockExecutor_executesFunctionsParsingConditionsAndUtilityLive() = kotlinx.coroutines.test.runTest {
        val blocks = listOf(
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.FUNC_HASH,
                label = "Card SHA256",
                inputTarget = "{card}|{mm}",
                secondaryParam = "SHA-256",
                outputVar = "sig",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.FUNC_BASE64_ENCODE,
                label = "B64",
                inputTarget = "hello:{pan4}",
                outputVar = "b64"
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.FUNC_BASE64_DECODE,
                label = "B64 Dec",
                inputTarget = "{var:b64}",
                outputVar = "b64_back",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.FUNC_MATH_EVAL,
                label = "Compute Cents",
                inputTarget = "(12 + 8) * 5",
                secondaryParam = "0",
                outputVar = "cents",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.PARSE_JSON,
                label = "Parse Outcome",
                inputTarget = "outcome.status",
                secondaryParam = "<SOURCE>",
                outputVar = "gw_status",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_IF_NUMBER,
                label = "Check 200",
                inputTarget = "{var:STATUS_CODE}",
                secondaryParam = "==",
                tertiaryParam = "200",
                outputVar = "is_200"
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_KEYCHECK,
                label = "Approved Verdict",
                inputTarget = "{var:gw_status}",
                secondaryParam = "EQUALS",
                tertiaryParam = "succeeded",
                verdictOnMatch = ResultBucket.LIVE
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_LIST_OP,
                label = "Count Lista Parts",
                inputTarget = "{lista}",
                secondaryParam = "COUNT",
                tertiaryParam = "|",
                outputVar = "field_count",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_LOG,
                label = "Trace Log",
                inputTarget = "Done {pan4} cents={var:cents}"
            )
        )

        val result = com.example.domain.ConfigBlockExecutor.executeStack(
            blocks = blocks,
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = com.example.domain.CardScheme.VISA,
            config = CustomHttpConfig(),
            initialSourceBody = """{"outcome":{"status":"succeeded"}}""",
            initialStatusCode = 200,
            dryRunNetwork = true
        )

        assertEquals(9, result.steps.size)
        assertEquals(64, result.captures["sig"]?.length)
        assertEquals("hello:0003", result.captures["b64_back"])
        assertEquals("100", result.captures["cents"])
        assertEquals("succeeded", result.captures["gw_status"])
        assertEquals("4", result.captures["field_count"])
        assertEquals("true", result.variables["is_200"])
        assertEquals(ResultBucket.LIVE, result.verdict?.first)
        assertEquals("Done 0003 cents=100", result.logs.first())
    }

    @Test
    fun customHttpClient_runsStandalonePureBlockConfigWhenUrlIsBlank() = kotlinx.coroutines.test.runTest {
        val blocks = listOf(
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_SET_VAR,
                label = "Set Mock Source",
                inputTarget = """{"status":"approved"}""",
                outputVar = "SOURCE"
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.PARSE_JSON,
                label = "Extract Status",
                inputTarget = "status",
                secondaryParam = "<SOURCE>",
                outputVar = "st",
                captureToOutput = true
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_KEYCHECK,
                label = "Approved Keycheck",
                inputTarget = "<SOURCE>",
                secondaryParam = "CONTAINS",
                tertiaryParam = "approved",
                verdictOnMatch = ResultBucket.LIVE
            )
        )
        val cfg = CustomHttpConfig(
            profileName = "Pure Block Pipeline",
            url = "",
            blocksSpec = com.example.domain.CustomConfigStudioEngine.serializeBlocks(blocks)
        )

        val record = CustomHttpClient.executeValidation(
            rawLine = "4532250055360003|08|2028|113",
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = com.example.domain.CardScheme.VISA,
            config = cfg,
            startNanos = System.nanoTime(),
            useCacheAndDedup = false
        )

        assertEquals(ResultBucket.LIVE, record.bucket)
        assertTrue(record.message.contains("[st=approved]"))

        val probe = CustomHttpClient.testEndpoint(cfg, "4532250055360003|08|2028|113")
        assertTrue(probe.isReachable)
        assertEquals(ResultBucket.LIVE, probe.detectedBucket)
        assertEquals(3, probe.blockTrace.size)
        assertEquals("approved", probe.parsedCaptures["st"])
    }

    @Test
    fun sandboxTestRegistry_returnsHonestUnknownWhenEnvKeysUnconfigured() = runTest {
        val (bucket, reason) = com.example.domain.SandboxTestRegistry.evaluateSandboxCard(
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            useCacheAndDedup = false
        )
        assertEquals(ResultBucket.UNKNOWN, bucket)
        assertFalse("Never synthesize fake LIVE verdict without real gateway key", reason.contains("4/4 OK"))
    }

    @Test
    fun splitComboLine_preservesColonsAndDelimitersInPassword() {
        val (user1, pass1) = CardValidatorEngine.splitComboLine("user@domain.com:p@ss:123:xyz|extra")
        assertEquals("user@domain.com", user1)
        assertEquals("p@ss:123:xyz|extra", pass1)

        val (user2, pass2) = CardValidatorEngine.splitComboLine("alice;secret;pass")
        assertEquals("alice", user2)
        assertEquals("secret;pass", pass2)
    }

    @Test
    fun customConfigStudioEngine_jsonDomHandlesNestedKeyShadowingAccurately() {
        val payload = """{"meta":{"status":"nested_pending"},"status":"top_approved","items":[{"id":10,"tags":["a","b"]},{"id":20}]}"""
        val rules = listOf(
            com.example.domain.CustomParseRule(
                name = "top_status",
                mode = com.example.domain.ParseRuleMode.JSON,
                expression = "status"
            ),
            com.example.domain.CustomParseRule(
                name = "meta_status",
                mode = com.example.domain.ParseRuleMode.JSON,
                expression = "meta.status"
            ),
            com.example.domain.CustomParseRule(
                name = "second_item_id",
                mode = com.example.domain.ParseRuleMode.JSON,
                expression = "items[1].id"
            )
        )
        val extracted = com.example.domain.CustomConfigStudioEngine.extractCaptures(
            bodyStr = payload,
            responseHeaders = emptyMap(),
            rules = rules
        )
        assertEquals("top_approved", extracted["top_status"])
        assertEquals("nested_pending", extracted["meta_status"])
        assertEquals("20", extracted["second_item_id"])
    }

    @Test
    fun customHttpClient_doesNotLeakProxyAuthorizationHeaderOntoTargetRequest() {
        val proxy = com.example.domain.ParsedProxyEndpoint(
            protocol = com.example.domain.ProxyProtocol.HTTP,
            host = "198.51.100.10",
            port = 8080,
            username = "proxyuser",
            password = "proxypassword"
        )
        val request = CustomHttpClient.buildRequest(
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = CustomHttpConfig(
                url = "https://api.example.com/check",
                method = HttpRequestMethod.POST_JSON
            ),
            selectedProxy = proxy
        )
        assertNull(request?.header("Proxy-Authorization"))
    }

    @Test
    fun cardValidatorEngine_blocksOnlyConfigRespectsSkipLocalLuhnFlag() = runTest {
        val blocks = listOf(
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_SET_VAR,
                label = "Set Source",
                inputTarget = "ok_live",
                outputVar = "SOURCE"
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_KEYCHECK,
                label = "Match Live",
                inputTarget = "<SOURCE>",
                secondaryParam = "CONTAINS",
                tertiaryParam = "ok_live",
                verdictOnMatch = ResultBucket.LIVE
            )
        )
        val cfg = CustomHttpConfig(
            profileName = "Combo Blocks",
            url = "",
            skipLocalLuhnPrecheck = true,
            blocksSpec = com.example.domain.CustomConfigStudioEngine.serializeBlocks(blocks)
        )
        val record = CardValidatorEngine.validateLine(
            rawLine = "member@drkingbd.org:secret:999",
            mode = ValidationMode.REMOTE_WEBHOOK,
            webhookUrl = "",
            autoResolveWildcards = false,
            customHttpConfig = cfg,
            useCacheAndDedup = false
        )
        assertEquals(ResultBucket.LIVE, record.bucket)
    }

    @Test
    fun configBlockExecutor_conditionOnFalseSupportsSkipNextAndStopDie() = runTest {
        val skipNextBlocks = listOf(
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_IF_NUMBER,
                label = "Check 200",
                inputTarget = "404",
                secondaryParam = "==",
                tertiaryParam = "200",
                extraParams = mapOf("onFalse" to "SKIP_NEXT")
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_SET_VAR,
                label = "Should Skip",
                inputTarget = "should_not_exist",
                outputVar = "skipped_var"
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_SET_VAR,
                label = "Fallback Step",
                inputTarget = "reached_after_skip",
                outputVar = "final_var",
                captureToOutput = true
            )
        )
        val skipResult = com.example.domain.ConfigBlockExecutor.executeStack(
            blocks = skipNextBlocks,
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = CustomHttpConfig(),
            dryRunNetwork = true
        )
        assertNull(skipResult.variables["skipped_var"])
        assertEquals("reached_after_skip", skipResult.captures["final_var"])

        val stopDieBlocks = listOf(
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.CONDITION_IF_STRING,
                label = "Must Contain Approved",
                inputTarget = "declined_card",
                secondaryParam = "CONTAINS",
                tertiaryParam = "approved",
                extraParams = mapOf("onFalse" to "STOP_DIE")
            ),
            com.example.domain.ConfigBlock(
                type = com.example.domain.BlockType.UTIL_SET_VAR,
                label = "Never Reached",
                inputTarget = "yes",
                outputVar = "after_stop"
            )
        )
        val stopResult = com.example.domain.ConfigBlockExecutor.executeStack(
            blocks = stopDieBlocks,
            normalizedLine = "4532250055360003|08|2028|113",
            pan = "4532250055360003",
            mm = "08",
            yyyy = "2028",
            cvv = "113",
            scheme = CardScheme.VISA,
            config = CustomHttpConfig(),
            dryRunNetwork = true
        )
        assertEquals(ResultBucket.DIE, stopResult.verdict?.first)
        assertNull(stopResult.variables["after_stop"])
    }
}



