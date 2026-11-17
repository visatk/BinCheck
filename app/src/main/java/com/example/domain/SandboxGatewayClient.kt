package com.example.domain

import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@Immutable
data class SandboxProviderKeyStatus(
    val provider: SandboxProvider,
    val envKeyNames: String,
    val endpointHost: String,
    val isConfigured: Boolean,
    val isRejectedLiveKey: Boolean = false,
    val statusBadge: String
)

@Immutable
data class SandboxEnvConfigSnapshot(
    val providerStatuses: List<SandboxProviderKeyStatus> = emptyList(),
    val configuredCount: Int = 0,
    val totalProviders: Int = 4
) {
    val hasAnyConfiguredLiveSandbox: Boolean
        get() = configuredCount > 0

    val summaryLabel: String
        get() = if (configuredCount > 0) {
            "$configuredCount/$totalProviders .env Gateway Keys Active (Live $0.00 Auth Failover)"
        } else {
            "0/$totalProviders .env Gateway Keys Configured"
        }
}

/**
 * Production-grade Multi-Gateway $0.00 Zero-Dollar Authorization Client & Batch Failover Engine.
 *
 * Reads gateway credentials from `BuildConfig` (injected via `.env` / `.env.example` from AI Studio Secrets)
 * and executes high-throughput $0.00 Auth verification across Stripe, Braintree, Authorize.Net, and Checkout.com with:
 * - Single-flight request coalescing (`RequestDeduplicator`)
 * - Bounded TTL LRU response caching (`TtlLruCache`)
 * - Per-host circuit breakers (`HostCircuitBreaker`)
 * - Exponential backoff with `Retry-After` awareness (`RetryAndBackoffPolicy`)
 * - Round-robin load distribution + automatic multi-gateway failover
 */
object SandboxGatewayClient {

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    private val CACHE_TTL_MS = TimeUnit.MINUTES.toMillis(10)
    private val responseCache = TtlLruCache<String, Pair<ResultBucket, String>>(maxEntries = 1_500)
    private val deduplicator = RequestDeduplicator<String, Pair<ResultBucket, String>>()
    private val roundRobinCursor = AtomicInteger(0)

    internal fun extractJsonString(rawJson: String, key: String): String {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*\"([^\"]*)\"")
        return pattern.find(rawJson)?.groupValues?.getOrNull(1).orEmpty()
    }

    internal fun extractJsonBoolean(rawJson: String, key: String): Boolean {
        val pattern = Regex("\"" + Regex.escape(key) + "\"\\s*:\\s*(true|false)", RegexOption.IGNORE_CASE)
        return pattern.find(rawJson)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) == true
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    fun clearCache() {
        responseCache.clear()
    }

    private fun isConfiguredValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return false
        val upper = trimmed.uppercase()
        return upper != "NONE" && upper != "NULL" && upper != "UNSET" && upper != "NA"
    }

    fun isStripeSecretConfigured(raw: String = BuildConfig.STRIPE_SANDBOX_SECRET_KEY): Boolean {
        val clean = raw.trim()
        return isConfiguredValue(clean) && (
            clean.startsWith("sk_live_") ||
                clean.startsWith("sk_test_") ||
                clean.startsWith("rk_live_") ||
                clean.startsWith("rk_test_")
            )
    }

    fun isStripePublishableConfigured(raw: String = BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY): Boolean {
        val clean = raw.trim()
        return isConfiguredValue(clean) && (
            clean.startsWith("pk_live_") ||
                clean.startsWith("pk_test_")
            )
    }

    fun isBraintreeConfigured(raw: String = BuildConfig.BRAINTREE_SANDBOX_ACCESS_TOKEN): Boolean {
        val clean = raw.trim()
        return isConfiguredValue(clean) && clean.length >= 16
    }

    fun isAuthNetConfigured(
        loginId: String = BuildConfig.AUTHNET_SANDBOX_API_LOGIN_ID,
        txnKey: String = BuildConfig.AUTHNET_SANDBOX_TRANSACTION_KEY
    ): Boolean {
        return isConfiguredValue(loginId) && isConfiguredValue(txnKey)
    }

    fun isCheckoutConfigured(raw: String = BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY): Boolean {
        val clean = raw.trim()
        return isConfiguredValue(clean) && (
            clean.startsWith("sk_sbox_") ||
                clean.startsWith("sk_test_") ||
                clean.startsWith("sk_live_") ||
                clean.startsWith("sk_prd_") ||
                clean.length >= 16
            )
    }

    fun inspectEnvKeys(): SandboxEnvConfigSnapshot {
        val stripeSec = BuildConfig.STRIPE_SANDBOX_SECRET_KEY
        val stripePub = BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY
        val stripeReady = isStripeSecretConfigured(stripeSec) || isStripePublishableConfigured(stripePub)
        val stripeIsLive = stripeSec.trim().startsWith("sk_live_") || stripePub.trim().startsWith("pk_live_")

        val btToken = BuildConfig.BRAINTREE_SANDBOX_ACCESS_TOKEN
        val btReady = isBraintreeConfigured(btToken)

        val authLogin = BuildConfig.AUTHNET_SANDBOX_API_LOGIN_ID
        val authKey = BuildConfig.AUTHNET_SANDBOX_TRANSACTION_KEY
        val authReady = isAuthNetConfigured(authLogin, authKey)

        val ckoKey = BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY
        val ckoReady = isCheckoutConfigured(ckoKey)
        val ckoIsLive = ckoKey.trim().startsWith("sk_live_") || ckoKey.trim().startsWith("sk_prd_")

        val list = listOf(
            SandboxProviderKeyStatus(
                provider = SandboxProvider.STRIPE,
                envKeyNames = "STRIPE_SANDBOX_SECRET_KEY / PUBLISHABLE_KEY",
                endpointHost = "api.stripe.com",
                isConfigured = stripeReady,
                isRejectedLiveKey = false,
                statusBadge = when {
                    stripeReady && stripeIsLive -> "LIVE KEY ACTIVE"
                    stripeReady -> "KEY ACTIVE"
                    else -> "UNCONFIGURED"
                }
            ),
            SandboxProviderKeyStatus(
                provider = SandboxProvider.BRAINTREE,
                envKeyNames = "BRAINTREE_SANDBOX_ACCESS_TOKEN",
                endpointHost = "payments.sandbox.braintree-api.com",
                isConfigured = btReady,
                isRejectedLiveKey = false,
                statusBadge = if (btReady) "KEY ACTIVE" else "UNCONFIGURED"
            ),
            SandboxProviderKeyStatus(
                provider = SandboxProvider.AUTHNET_CYBERSOURCE,
                envKeyNames = "AUTHNET_SANDBOX_API_LOGIN_ID + TXN_KEY",
                endpointHost = "apitest.authorize.net",
                isConfigured = authReady,
                isRejectedLiveKey = false,
                statusBadge = if (authReady) "KEY ACTIVE" else "UNCONFIGURED"
            ),
            SandboxProviderKeyStatus(
                provider = SandboxProvider.CHECKOUT_WORLDPAY,
                envKeyNames = "CHECKOUT_SANDBOX_SECRET_KEY",
                endpointHost = if (ckoIsLive) "api.checkout.com" else "api.sandbox.checkout.com",
                isConfigured = ckoReady,
                isRejectedLiveKey = false,
                statusBadge = when {
                    ckoReady && ckoIsLive -> "LIVE KEY ACTIVE"
                    ckoReady -> "KEY ACTIVE"
                    else -> "UNCONFIGURED"
                }
            )
        )

        return SandboxEnvConfigSnapshot(
            providerStatuses = list,
            configuredCount = list.count { it.isConfigured },
            totalProviders = list.size
        )
    }

    /**
     * Executes a high-throughput $0.00 Zero-Dollar Authorization check across configured `.env` sandboxes
     * with round-robin load balancing, single-flight coalescing, circuit breaker protection,
     * and automatic failover. Returns `null` if no `.env` sandbox keys are configured or if all
     * configured endpoints are unreachable so caller can fall back to deterministic $0.00 Auth consensus.
     */
    suspend fun executeConfiguredEnvZeroAuthIfAvailable(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        timeoutSeconds: Int = 12,
        useCacheAndDedup: Boolean = true
    ): Pair<ResultBucket, String>? = withContext(Dispatchers.IO) {
        val snapshot = inspectEnvKeys()
        val activeProviders = snapshot.providerStatuses.filter { it.isConfigured }.map { it.provider }
        if (activeProviders.isEmpty()) return@withContext null

        val cacheKey = "sbox0|$pan|$mm|$yyyy|$cvv|${activeProviders.joinToString(",") { it.id }}"
        if (useCacheAndDedup) {
            responseCache.get(cacheKey)?.let { cached ->
                NetworkClientProvider.telemetry.recordCacheHit()
                return@withContext cached.first to "${cached.second} (Cached)"
            }
        }

        val block: suspend () -> Pair<ResultBucket, String>? = {
            val startIndex = (roundRobinCursor.getAndIncrement() and Int.MAX_VALUE) % activeProviders.size
            var resolvedResult: Pair<ResultBucket, String>? = null

            for (offset in activeProviders.indices) {
                val provider = activeProviders[(startIndex + offset) % activeProviders.size]
                val attempt = when (provider) {
                    SandboxProvider.STRIPE -> executeStripeZeroAuth(pan, mm, yyyy, cvv, timeoutSeconds)
                    SandboxProvider.CHECKOUT_WORLDPAY -> executeCheckoutZeroAuth(pan, mm, yyyy, cvv, timeoutSeconds)
                    SandboxProvider.AUTHNET_CYBERSOURCE -> executeAuthNetZeroAuth(pan, mm, yyyy, cvv, timeoutSeconds)
                    SandboxProvider.BRAINTREE -> executeBraintreeZeroAuth(pan, mm, yyyy, cvv, timeoutSeconds)
                    SandboxProvider.ALL_SANDBOXES -> null
                }
                if (attempt != null && attempt.first != ResultBucket.UNKNOWN) {
                    resolvedResult = attempt
                    break
                } else if (attempt != null && resolvedResult == null) {
                    resolvedResult = attempt
                }
            }
            if (resolvedResult != null && useCacheAndDedup && resolvedResult.first != ResultBucket.UNKNOWN) {
                responseCache.put(cacheKey, resolvedResult, CACHE_TTL_MS)
            }
            resolvedResult
        }

        if (!useCacheAndDedup) {
            return@withContext block()
        }

        var resultHolder: Pair<ResultBucket, String>? = null
        val (_, coalesced) = deduplicator.getOrExecute(cacheKey) {
            val res = block()
            resultHolder = res
            res ?: (ResultBucket.UNKNOWN to "")
        }
        if (coalesced) {
            NetworkClientProvider.telemetry.recordDeduplicatedSaved()
        }
        resultHolder
    }

    private suspend fun executeStripeZeroAuth(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        timeoutSeconds: Int
    ): Pair<ResultBucket, String>? {
        val secretKey = BuildConfig.STRIPE_SANDBOX_SECRET_KEY.trim()
        val pubKey = BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY.trim()
        val useSecret = isStripeSecretConfigured(secretKey)
        val usePub = !useSecret && isStripePublishableConfigured(pubKey)
        if (!useSecret && !usePub) return null

        val client = NetworkClientProvider.clientWithTimeout(timeoutSeconds)
        val host = "api.stripe.com"

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = host,
            maxRetries = 1,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = {
                val request = if (useSecret) {
                    val form = FormBody.Builder()
                        .add("confirm", "true")
                        .add("usage", "off_session")
                        .add("payment_method_data[type]", "card")
                        .add("payment_method_data[card][number]", pan)
                        .add("payment_method_data[card][exp_month]", mm)
                        .add("payment_method_data[card][exp_year]", yyyy)
                        .add("payment_method_data[card][cvc]", cvv)
                        .build()
                    Request.Builder()
                        .url("https://api.stripe.com/v1/setup_intents")
                        .header("Authorization", "Bearer $secretKey")
                        .header("Accept", "application/json")
                        .post(form)
                        .build()
                } else {
                    val form = FormBody.Builder()
                        .add("key", pubKey)
                        .add("card[number]", pan)
                        .add("card[exp_month]", mm)
                        .add("card[exp_year]", yyyy)
                        .add("card[cvc]", cvv)
                        .build()
                    Request.Builder()
                        .url("https://api.stripe.com/v1/tokens")
                        .header("Accept", "application/json")
                        .post(form)
                        .build()
                }
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    HttpCallRawResponse(
                        code = resp.code,
                        body = bodyStr,
                        retryAfterMs = RetryAndBackoffPolicy.parseRetryAfterMs(resp.header("Retry-After"), bodyStr)
                    )
                }
            },
            onResult = { code, body, _ ->
                parseStripeSandboxResponse(code, body)
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
    }

    internal fun parseStripeSandboxResponse(httpCode: Int, body: String): Pair<ResultBucket, String> {
        if (httpCode in 200..299) {
            val id = extractJsonString(body, "id").ifBlank { "seti_ok" }
            val status = extractJsonString(body, "status").ifBlank { "succeeded" }
            return ResultBucket.LIVE to "$0.00 Auth • Stripe Sandbox • $id ($status • 200 OK)"
        }
        val declineCode = extractJsonString(body, "decline_code")
        val errCode = extractJsonString(body, "code")
        val errMsg = extractJsonString(body, "message")
        val combinedCode = listOf(errCode, declineCode).filter { it.isNotBlank() }.joinToString(": ")
        return when {
            httpCode == 402 || errCode == "card_declined" || errCode == "incorrect_cvc" || errCode == "expired_card" ->
                ResultBucket.DIE to "$0.00 Auth • Stripe Sandbox • ${combinedCode.ifBlank { "card_declined" }} ${errMsg.take(48)}".trim()
            else ->
                ResultBucket.UNKNOWN to "$0.00 Auth • Stripe Sandbox HTTP $httpCode ${combinedCode.ifBlank { errMsg.take(40) }}".trim()
        }
    }

    private suspend fun executeCheckoutZeroAuth(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        timeoutSeconds: Int
    ): Pair<ResultBucket, String>? {
        val secretKey = BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY.trim()
        if (!isCheckoutConfigured(secretKey)) return null

        val client = NetworkClientProvider.clientWithTimeout(timeoutSeconds)
        val expM = mm.toIntOrNull() ?: 12
        val expY = yyyy.toIntOrNull() ?: 2029
        val ref = "bincheck_0auth_${UUID.randomUUID().toString().take(8)}"
        val payload = """{"amount":0,"currency":"USD","reference":"$ref","source":{"type":"card","number":"${escapeJson(pan)}","expiry_month":$expM,"expiry_year":$expY,"cvv":"${escapeJson(cvv)}"}}"""
        val isLiveCheckout = secretKey.startsWith("sk_live_") || secretKey.startsWith("sk_prd_")
        val checkoutHost = if (isLiveCheckout) "api.checkout.com" else "api.sandbox.checkout.com"

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = checkoutHost,
            maxRetries = 1,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = {
                val request = Request.Builder()
                    .url("https://$checkoutHost/payments")
                    .header("Authorization", "Bearer $secretKey")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    HttpCallRawResponse(
                        code = resp.code,
                        body = bodyStr,
                        retryAfterMs = RetryAndBackoffPolicy.parseRetryAfterMs(resp.header("Retry-After"), bodyStr)
                    )
                }
            },
            onResult = { code, body, _ ->
                val approved = extractJsonBoolean(body, "approved")
                val status = extractJsonString(body, "status")
                val summary = extractJsonString(body, "response_summary")
                when {
                    approved || status.equals("Card Verified", ignoreCase = true) || status.equals("Authorized", ignoreCase = true) ->
                        ResultBucket.LIVE to "$0.00 Auth • Checkout.com Sandbox • ${summary.ifBlank { "10000 Approved" }}"
                    code in 200..299 && !approved ->
                        ResultBucket.DIE to "$0.00 Auth • Checkout.com Sandbox • ${summary.ifBlank { "Declined" }}"
                    else ->
                        ResultBucket.UNKNOWN to "$0.00 Auth • Checkout.com HTTP $code"
                }
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
    }

    private suspend fun executeAuthNetZeroAuth(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        timeoutSeconds: Int
    ): Pair<ResultBucket, String>? {
        val loginId = BuildConfig.AUTHNET_SANDBOX_API_LOGIN_ID.trim()
        val txnKey = BuildConfig.AUTHNET_SANDBOX_TRANSACTION_KEY.trim()
        if (!isAuthNetConfigured(loginId, txnKey)) return null

        val client = NetworkClientProvider.clientWithTimeout(timeoutSeconds)
        val payload = """{"createTransactionRequest":{"merchantAuthentication":{"name":"${escapeJson(loginId)}","transactionKey":"${escapeJson(txnKey)}"},"transactionRequest":{"transactionType":"authOnlyTransaction","amount":"0.00","payment":{"creditCard":{"cardNumber":"${escapeJson(pan)}","expirationDate":"${escapeJson(yyyy)}-${escapeJson(mm)}","cardCode":"${escapeJson(cvv)}"}}}}}"""

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = "apitest.authorize.net",
            maxRetries = 1,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = {
                val request = Request.Builder()
                    .url("https://apitest.authorize.net/xml/v1/request.api")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    HttpCallRawResponse(
                        code = resp.code,
                        body = bodyStr,
                        retryAfterMs = RetryAndBackoffPolicy.parseRetryAfterMs(resp.header("Retry-After"), bodyStr)
                    )
                }
            },
            onResult = { code, body, _ ->
                val cleanBody = body.trimStart('\uFEFF')
                val respCode = extractJsonString(cleanBody, "responseCode")
                when (respCode) {
                    "1" -> ResultBucket.LIVE to "$0.00 Auth • Authorize.Net Sandbox • 1-1 Approved"
                    "2", "3" -> ResultBucket.DIE to "$0.00 Auth • Authorize.Net Sandbox • $respCode Declined"
                    else -> ResultBucket.UNKNOWN to "$0.00 Auth • Authorize.Net HTTP $code"
                }
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
    }

    private suspend fun executeBraintreeZeroAuth(
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        timeoutSeconds: Int
    ): Pair<ResultBucket, String>? {
        val token = BuildConfig.BRAINTREE_SANDBOX_ACCESS_TOKEN.trim()
        if (!isBraintreeConfigured(token)) return null

        val client = NetworkClientProvider.clientWithTimeout(timeoutSeconds)
        val payload = """{"query":"mutation TokenizeCreditCard(${'$'}input: TokenizeCreditCardInput!) { tokenizeCreditCard(input: ${'$'}input) { paymentMethod { id } } }","variables":{"input":{"creditCard":{"number":"${escapeJson(pan)}","expirationMonth":"${escapeJson(mm)}","expirationYear":"${escapeJson(yyyy)}","cvv":"${escapeJson(cvv)}"}}}}"""

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = "payments.sandbox.braintree-api.com",
            maxRetries = 1,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = {
                val request = Request.Builder()
                    .url("https://payments.sandbox.braintree-api.com/graphql")
                    .header("Authorization", "Bearer $token")
                    .header("Braintree-Version", "2023-08-01")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                client.newCall(request).execute().use { resp ->
                    val bodyStr = resp.body?.string().orEmpty()
                    HttpCallRawResponse(
                        code = resp.code,
                        body = bodyStr,
                        retryAfterMs = RetryAndBackoffPolicy.parseRetryAfterMs(resp.header("Retry-After"), bodyStr)
                    )
                }
            },
            onResult = { code, body, _ ->
                when {
                    code in 200..299 && body.contains("paymentMethod") ->
                        ResultBucket.LIVE to "$0.00 Auth • Braintree Sandbox • 1000 Approved"
                    code in 200..299 && body.contains("errors") ->
                        ResultBucket.DIE to "$0.00 Auth • Braintree Sandbox • 2000 Do Not Honor"
                    else ->
                        ResultBucket.UNKNOWN to "$0.00 Auth • Braintree HTTP $code"
                }
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
    }
}
