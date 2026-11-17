package com.example.domain

import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Extraction mode for Custom Config Studio response parsing (Capture Rules).
 */
enum class ParseRuleMode(val label: String, val hint: String) {
    JSON("JSON Key", "e.g. error.code or errors[0].code"),
    LR("Left / Right", "Extract text between Left & Right delimiters"),
    REGEX("Regex Group", "e.g. \"code\":\"([^\"]+)\""),
    HEADER("HTTP Header", "e.g. x-request-id or cf-ray"),
    COOKIE("Set-Cookie", "e.g. session_id, csrf_token, or *"),
    CSS("HTML / Attr", "e.g. input[name=csrf]@value or title")
}

/**
 * A single capture / parse rule that extracts a named variable from the HTTP response.
 */
data class CustomParseRule(
    val name: String,
    val mode: ParseRuleMode = ParseRuleMode.JSON,
    val expression: String = "",
    val leftDelimiter: String = "",
    val rightDelimiter: String = "",
    val showInOutput: Boolean = true,
    val recursive: Boolean = false,
    val defaultValue: String = ""
)

/**
 * Condition types for Custom Config Studio Checkpoints (Keycheck Rules).
 */
enum class CheckpointCondition(val label: String, val requiresKey: Boolean) {
    BODY_CONTAINS("Body Contains", false),
    BODY_CONTAINS_ANY("Body Contains Any (,)", false),
    BODY_CONTAINS_ALL("Body Contains All (&)", false),
    BODY_NOT_CONTAINS("Body Not Contains", false),
    STATUS_CODE_EQUALS("HTTP Status ==", false),
    STATUS_CODE_IN("HTTP Status In (,)", false),
    JSON_KEY_EQUALS("JSON Key ==", true),
    JSON_KEY_CONTAINS("JSON Key Contains", true),
    CAPTURED_VAR_CONTAINS("Var Contains", true),
    CAPTURED_VAR_EQUALS("Var Equals", true),
    CAPTURED_VAR_NOT_CONTAINS("Var Not Contains", true),
    HEADER_CONTAINS("Header Contains", true),
    REGEX_MATCH("Regex Matches Body", false),
    LATENCY_GT_MS("Latency > (ms)", false)
}

/**
 * A single checkpoint / keycheck assertion mapping a response condition to a ResultBucket verdict.
 */
data class CustomCheckpointRule(
    val verdict: ResultBucket = ResultBucket.LIVE,
    val condition: CheckpointCondition = CheckpointCondition.BODY_CONTAINS,
    val targetKey: String = "",
    val expectedValue: String = "",
    val label: String = ""
)

/**
 * Step-by-step evaluation trace for a single [CustomCheckpointRule] in the Keycheck debugger.
 */
data class CheckpointEvalStep(
    val index: Int,
    val rule: CustomCheckpointRule,
    val matched: Boolean,
    val isWinner: Boolean,
    val detail: String
)

/**
 * Complete evaluation result for ordered checkpoints, including the winning verdict and full trace.
 */
data class CheckpointEvaluationResult(
    val winner: Pair<ResultBucket, String>?,
    val steps: List<CheckpointEvalStep>
)

/**
 * Live rendered preview of an HTTP request before network dispatch.
 */
data class RenderedRequestPreview(
    val resolvedMethod: String,
    val resolvedUrl: String,
    val resolvedHeaders: List<Pair<String, String>>,
    val resolvedBody: String,
    val isJsonValid: Boolean?,
    val jsonDiagnostic: String = "",
    val detectedInputType: String = "CARD",
    val unresolvedTokens: List<String> = emptyList()
)

/**
 * Proxy transport protocol for Custom Config Studio routing.
 */
enum class ProxyProtocol(val label: String) {
    HTTP("HTTP/S"),
    SOCKS5("SOCKS5")
}

/**
 * Proxy selection strategy across a multi-line proxy pool.
 */
enum class ProxyRotationMode(val label: String) {
    ROUND_ROBIN("Round-Robin"),
    RANDOM("Random"),
    STICKY("Sticky (1st)")
}

/**
 * Parsed single proxy endpoint supporting `host:port`, `host:port:user:pass`, or `user:pass@host:port`.
 */
data class ParsedProxyEndpoint(
    val host: String,
    val port: Int,
    val username: String = "",
    val password: String = "",
    val protocol: ProxyProtocol = ProxyProtocol.HTTP
) {
    val requiresAuth: Boolean
        get() = username.isNotBlank()

    val displayLabel: String
        get() = buildString {
            append(protocol.name)
            append("://")
            if (requiresAuth) {
                append(username.take(3)).append("***@")
            }
            append(host).append(':').append(port)
        }

    fun toJavaNetProxy(): Proxy {
        val type = when (protocol) {
            ProxyProtocol.HTTP -> Proxy.Type.HTTP
            ProxyProtocol.SOCKS5 -> Proxy.Type.SOCKS
        }
        return Proxy(type, InetSocketAddress.createUnresolved(host, port))
    }

    fun basicAuthorizationHeaderValue(): String? {
        if (!requiresAuth) return null
        val raw = "$username:$password".toByteArray(Charsets.UTF_8)
        val encoded = runCatching {
            Base64.getEncoder().encodeToString(raw)
        }.getOrElse {
            encodeBase64Fallback(raw)
        }
        return "Basic $encoded"
    }
}

/**
 * Named preset or user-saved profile in Custom Config Studio.
 */
data class CustomConfigProfile(
    val id: String,
    val name: String,
    val description: String,
    val config: CustomHttpConfig,
    val isBuiltIn: Boolean = false
)

/**
 * Core engine for Custom Config Studio:
 * - Parse Rule serialization & extraction (JSON dot-path + array indexing, Left/Right, Regex, Header, Set-Cookie, HTML/CSS)
 * - Smart JSON Key Discovery from live/sample responses for 1-tap rule generation
 * - Checkpoint / Keycheck ordered evaluation (14 condition modes + step-by-step debugger trace)
 * - 2-Step Pre-Request (Token / Session / CSRF Handshake -> Main Request) variable & cookie chaining
 * - Proxy list parsing, validation diagnostics & rotation (Round-Robin, Random, Sticky)
 * - Universal Importer: cURL (bash/cmd), JS fetch(), Raw HTTP (Burp/HTTP Toolkit), and LoliScript (.loli/.anom)
 * - Code Exporters: Export to JSON, Export to cURL command, and Export to OpenBullet LoliScript
 */
object CustomConfigStudioEngine {

    private const val RULE_LINE_SEP = "\n"
    private const val FIELD_SEP = "\u001F" // ASCII Unit Separator for collision-free field packing
    private const val LEGACY_PIPE_SEP = "::"

    private val roundRobinCounter = AtomicInteger(0)

    private val ROTATING_USER_AGENTS = listOf(
        "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Safari/605.1.15"
    )

    internal fun secretOrPlaceholder(raw: String, placeholder: String): String {
        val clean = raw.trim()
        val upper = clean.uppercase(Locale.ROOT)
        return if (clean.isEmpty() || upper == "UNSET" || upper == "NONE" || upper == "NULL" || upper == "NA") {
            placeholder
        } else {
            clean
        }
    }

    val BUILT_IN_PROFILES: List<CustomConfigProfile> by lazy {
        val stripeSecret = secretOrPlaceholder(com.example.BuildConfig.STRIPE_SANDBOX_SECRET_KEY, "<STRIPE_SECRET_KEY>")
        val stripePub = secretOrPlaceholder(com.example.BuildConfig.STRIPE_SANDBOX_PUBLISHABLE_KEY, "<STRIPE_PUBLISHABLE_KEY>")
        val checkoutSecret = secretOrPlaceholder(com.example.BuildConfig.CHECKOUT_SANDBOX_SECRET_KEY, "<CHECKOUT_SECRET_KEY>")
        val authNetLogin = secretOrPlaceholder(com.example.BuildConfig.AUTHNET_SANDBOX_API_LOGIN_ID, "<AUTHNET_API_LOGIN_ID>")
        val authNetKey = secretOrPlaceholder(com.example.BuildConfig.AUTHNET_SANDBOX_TRANSACTION_KEY, "<AUTHNET_TRANSACTION_KEY>")
        listOf(
            CustomConfigProfile(
                id = "builtin_zero_dollar_sandbox",
                name = "Stripe $0.00 SetupIntent Auth",
                description = "Real Stripe v1/setup_intents $0.00 off-session card verification with status & decline_code captures",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Stripe $0.00 SetupIntent Auth",
                    url = "https://api.stripe.com/v1/setup_intents",
                    method = HttpRequestMethod.POST_FORM,
                    headersText = "Authorization: Bearer $stripeSecret\nAccept: application/json\nIdempotency-Key: {uuid}",
                    bodyTemplate = "confirm=true&usage=off_session&payment_method_data[type]=card&payment_method_data[card][number]={card}&payment_method_data[card][exp_month]={mm}&payment_method_data[card][exp_year]={yyyy}&payment_method_data[card][cvc]={cvv}",
                    timeoutSeconds = 12,
                    maxRetries = 1,
                    liveKeywords = "succeeded,requires_action,seti_",
                    dieKeywords = "card_declined,incorrect_cvc,expired_card,invalid_number",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "status",
                                mode = ParseRuleMode.JSON,
                                expression = "status",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "decline_code",
                                mode = ParseRuleMode.JSON,
                                expression = "error.decline_code",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "status",
                                expectedValue = "succeeded",
                                label = "Stripe $0 Succeeded"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "card_declined,incorrect_cvc,expired_card",
                                label = "Stripe Declined"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_two_step_token_pipeline",
                name = "2-Step Stripe Token -> SetupIntent",
                description = "Step 1 tokenizes card via /v1/tokens & captures payment_token -> Step 2 confirms {cap:payment_token}",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "2-Step Stripe Token -> SetupIntent",
                    url = "https://api.stripe.com/v1/setup_intents",
                    method = HttpRequestMethod.POST_FORM,
                    headersText = "Authorization: Bearer $stripeSecret\nAccept: application/json\nIdempotency-Key: {uuid}",
                    bodyTemplate = "confirm=true&usage=off_session&payment_method_data[type]=card&payment_method_data[card][token]={cap:payment_token}",
                    timeoutSeconds = 15,
                    maxRetries = 1,
                    liveKeywords = "succeeded,requires_action,seti_",
                    dieKeywords = "card_declined,incorrect_cvc,expired_card",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    preRequestEnabled = true,
                    preRequestUrl = "https://api.stripe.com/v1/tokens",
                    preRequestMethod = HttpRequestMethod.POST_FORM,
                    preRequestHeaders = "Authorization: Bearer $stripePub\nAccept: application/json",
                    preRequestBody = "card[number]={card}&card[exp_month]={mm}&card[exp_year]={yyyy}&card[cvc]={cvv}",
                    preRequestParseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "payment_token",
                                mode = ParseRuleMode.JSON,
                                expression = "id",
                                showInOutput = true,
                                defaultValue = "tok_visa"
                            )
                        )
                    ),
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "setup_id",
                                mode = ParseRuleMode.JSON,
                                expression = "id",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "status",
                                expectedValue = "succeeded",
                                label = "2-Step Token Verified"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "card_declined,incorrect_cvc,expired_card",
                                label = "2-Step Declined"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_form_gateway_capture",
                name = "Payate Form Gateway + Captures",
                description = "URL-encoded form POST to Payate API (data={lista}) with JSON msg & error code keycheck",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Payate Form Gateway + Captures",
                    url = "https://mock.payate.com/api.php",
                    method = HttpRequestMethod.POST_FORM,
                    headersText = "Accept: application/json, text/plain, */*\nX-Requested-With: XMLHttpRequest\nOrigin: https://mock.payate.com\nReferer: https://mock.payate.com/",
                    bodyTemplate = "data={lista}",
                    timeoutSeconds = 15,
                    maxRetries = 1,
                    liveKeywords = "Live,Approved,Checked",
                    dieKeywords = "Die,Dead,Declined",
                    userAgent = "Mozilla/5.0 (Linux; Android 15) BinCheck/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "err",
                                mode = ParseRuleMode.JSON,
                                expression = "error",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "msg",
                                mode = ParseRuleMode.JSON,
                                expression = "msg",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "error",
                                expectedValue = "1",
                                label = "Payate Live"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "error",
                                expectedValue = "2",
                                label = "Payate Die"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_get_query_regex",
                name = "Checkout.com $0.00 Card Verify JSON",
                description = "POST JSON $0.00 Zero-Dollar Auth to Checkout.com /payments with response_summary capture",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Checkout.com $0.00 Card Verify JSON",
                    url = "https://api.sandbox.checkout.com/payments",
                    method = HttpRequestMethod.POST_JSON,
                    headersText = "Authorization: Bearer $checkoutSecret\nAccept: application/json",
                    bodyTemplate = """{"amount":0,"currency":"USD","reference":"{uuid}","source":{"type":"card","number":"{card}","expiry_month":{mm_no_pad},"expiry_year":{yyyy},"cvv":"{cvv}"}}""",
                    timeoutSeconds = 12,
                    maxRetries = 1,
                    liveKeywords = "Authorized,Card Verified,10000",
                    dieKeywords = "Declined,20005,20014",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "status",
                                mode = ParseRuleMode.JSON,
                                expression = "status",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "summary",
                                mode = ParseRuleMode.JSON,
                                expression = "response_summary",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "Card Verified,Authorized,10000",
                                label = "Checkout Approved"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "Declined,20005,20014",
                                label = "Checkout Declined"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_combo_auth_json",
                name = "Authorize.Net $0.00 authOnly JSON",
                description = "POST JSON createTransactionRequest ($0.00 authOnlyTransaction) to Authorize.Net API",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Authorize.Net $0.00 authOnly JSON",
                    url = "https://apitest.authorize.net/xml/v1/request.api",
                    method = HttpRequestMethod.POST_JSON,
                    headersText = "Accept: application/json",
                    bodyTemplate = """{"createTransactionRequest":{"merchantAuthentication":{"name":"$authNetLogin","transactionKey":"$authNetKey"},"transactionRequest":{"transactionType":"authOnlyTransaction","amount":"0.00","payment":{"creditCard":{"cardNumber":"{card}","expirationDate":"{yyyy}-{mm}","cardCode":"{cvv}"}}}}}""",
                    timeoutSeconds = 12,
                    maxRetries = 1,
                    liveKeywords = "Approved,1",
                    dieKeywords = "Declined,Error,2,3",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "responseCode",
                                mode = ParseRuleMode.JSON,
                                expression = "transactionResponse.responseCode",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "transactionResponse.responseCode",
                                expectedValue = "1",
                                label = "Auth.Net 1-1 Approved"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "\"responseCode\":\"2\",\"responseCode\":\"3\"",
                                label = "Auth.Net Declined"
                            )
                        )
                    )
                )
            )
        )
    }

    // =========================================================================
    // 0. Config Maker Block Stack Serialization Delegates
    // =========================================================================

    fun serializeBlocks(blocks: List<ConfigBlock>): String =
        ConfigBlockSerialization.serializeBlocks(blocks)

    fun parseBlocks(spec: String): List<ConfigBlock> =
        ConfigBlockSerialization.parseBlocks(spec)

    // =========================================================================
    // 1. Parse Rules Serialization & Extraction
    // =========================================================================

    fun serializeParseRules(rules: List<CustomParseRule>): String {
        if (rules.isEmpty()) return ""
        return rules.mapNotNull { rule ->
            val cleanName = rule.name.trim().replace(FIELD_SEP, "").replace("\n", "")
            if (cleanName.isEmpty()) return@mapNotNull null
            listOf(
                cleanName,
                rule.mode.name,
                rule.expression.replace(FIELD_SEP, " ").replace("\n", " "),
                rule.leftDelimiter.replace(FIELD_SEP, " ").replace("\n", " "),
                rule.rightDelimiter.replace(FIELD_SEP, " ").replace("\n", " "),
                if (rule.showInOutput) "1" else "0",
                if (rule.recursive) "1" else "0",
                rule.defaultValue.replace(FIELD_SEP, " ").replace("\n", " ")
            ).joinToString(FIELD_SEP)
        }.joinToString(RULE_LINE_SEP)
    }

    fun parseParseRules(spec: String): List<CustomParseRule> {
        if (spec.isBlank()) return emptyList()
        return spec.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = if (line.contains(FIELD_SEP)) {
                line.split(FIELD_SEP)
            } else {
                line.split(LEGACY_PIPE_SEP)
            }
            if (parts.size < 2) return@mapNotNull null
            val name = parts[0].trim()
            if (name.isEmpty()) return@mapNotNull null
            val mode = runCatching { ParseRuleMode.valueOf(parts[1].trim().uppercase(Locale.ROOT)) }
                .getOrDefault(ParseRuleMode.JSON)
            val expr = parts.getOrElse(2) { "" }
            val left = parts.getOrElse(3) { "" }
            val right = parts.getOrElse(4) { "" }
            val show = parts.getOrElse(5) { "1" } != "0"
            val recursive = parts.getOrElse(6) { "0" } == "1"
            val defaultVal = parts.getOrElse(7) { "" }
            CustomParseRule(
                name = name,
                mode = mode,
                expression = expr,
                leftDelimiter = left,
                rightDelimiter = right,
                showInOutput = show,
                recursive = recursive,
                defaultValue = defaultVal
            )
        }
    }

    /**
     * Executes all configured [CustomParseRule] items against the HTTP response body and headers,
     * returning an ordered map of `variableName -> extractedValue`.
     */
    fun extractCaptures(
        bodyStr: String,
        responseHeaders: Map<String, String>,
        rules: List<CustomParseRule>
    ): Map<String, String> {
        if (rules.isEmpty()) return emptyMap()
        val captured = LinkedHashMap<String, String>()
        for (rule in rules) {
            val varName = rule.name.trim()
            if (varName.isEmpty()) continue
            val rawExtracted: String? = when (rule.mode) {
                ParseRuleMode.JSON -> extractJsonPathValue(
                    jsonText = bodyStr,
                    path = rule.expression.trim(),
                    recursive = rule.recursive
                )
                ParseRuleMode.LR -> extractLeftRightValue(
                    text = bodyStr,
                    left = rule.leftDelimiter,
                    right = rule.rightDelimiter,
                    recursive = rule.recursive
                )
                ParseRuleMode.REGEX -> extractRegexGroupValue(
                    text = bodyStr,
                    patternStr = rule.expression,
                    recursive = rule.recursive
                )
                ParseRuleMode.HEADER -> {
                    val key = rule.expression.trim().lowercase(Locale.ROOT)
                    if (key.isEmpty()) null else {
                        responseHeaders[key] ?: responseHeaders.entries.firstOrNull {
                            it.key.equals(key, ignoreCase = true)
                        }?.value
                    }
                }
                ParseRuleMode.COOKIE -> extractCookieValue(
                    responseHeaders = responseHeaders,
                    cookieName = rule.expression.trim()
                )
                ParseRuleMode.CSS -> extractHtmlCssValue(
                    htmlText = bodyStr,
                    selector = rule.expression.trim()
                )
            }
            val resolved = rawExtracted?.takeIf { it.isNotBlank() }
                ?: rule.defaultValue.trim().takeIf { it.isNotEmpty() }
            if (!resolved.isNullOrBlank()) {
                captured[varName] = resolved.trim().take(120)
            }
        }
        return captured
    }

    fun formatCapturesForSummary(
        captures: Map<String, String>,
        rules: List<CustomParseRule>
    ): String {
        if (captures.isEmpty()) return ""
        val visibleNames = rules.filter { it.showInOutput }.map { it.name.trim() }.toSet()
        val entries = if (rules.isEmpty()) {
            captures.entries.toList()
        } else {
            captures.entries.filter { it.key in visibleNames }
        }
        if (entries.isEmpty()) return ""
        return entries.take(4).joinToString(" ") { (k, v) -> "[$k=${v.take(28)}]" }
    }

    /**
     * Pure-Kotlin JSON field/dot-path & array-index extractor:
     * Supports top-level `code`, nested `error.code`, `json.card.scheme`, array indexing `errors[0].code` or `errors.0.code`,
     * and recursive array extraction (`errors.code` across array items when `recursive = true`).
     * Works identically on Android runtime and pure JVM unit tests without `org.json.JSONObject` Stub! exceptions.
     */
    private sealed interface JsonNode {
        data class Obj(val entries: Map<String, JsonNode>) : JsonNode
        data class Arr(val items: List<JsonNode>) : JsonNode
        data class Str(val value: String) : JsonNode
        data class Prim(val value: String?) : JsonNode
    }

    private fun parseJsonDom(jsonText: String): JsonNode? {
        val s = jsonText.trim()
        if (s.isEmpty()) return null
        var idx = 0

        fun skipWs() {
            while (idx < s.length && s[idx].isWhitespace()) idx++
        }

        fun parseString(): String? {
            if (idx >= s.length || s[idx] != '"') return null
            idx++ // skip opening "
            val sb = StringBuilder()
            while (idx < s.length) {
                val ch = s[idx]
                when {
                    ch == '"' -> {
                        idx++
                        return sb.toString()
                    }
                    ch == '\\' && idx + 1 < s.length -> {
                        val next = s[idx + 1]
                        when (next) {
                            '"' -> { sb.append('"'); idx += 2 }
                            '\\' -> { sb.append('\\'); idx += 2 }
                            '/' -> { sb.append('/'); idx += 2 }
                            'b' -> { sb.append('\b'); idx += 2 }
                            'f' -> { sb.append('\u000C'); idx += 2 }
                            'n' -> { sb.append('\n'); idx += 2 }
                            'r' -> { sb.append('\r'); idx += 2 }
                            't' -> { sb.append('\t'); idx += 2 }
                            'u' -> {
                                if (idx + 5 < s.length) {
                                    val code = s.substring(idx + 2, idx + 6).toIntOrNull(16)
                                    if (code != null) {
                                        sb.append(code.toChar())
                                        idx += 6
                                    } else {
                                        sb.append(next)
                                        idx += 2
                                    }
                                } else {
                                    sb.append(next)
                                    idx += 2
                                }
                            }
                            else -> { sb.append(next); idx += 2 }
                        }
                    }
                    else -> {
                        sb.append(ch)
                        idx++
                    }
                }
            }
            return null
        }

        fun parseValue(depth: Int): JsonNode? {
            if (depth > 48) return null
            skipWs()
            if (idx >= s.length) return null
            return when (s[idx]) {
                '{' -> {
                    idx++
                    val map = LinkedHashMap<String, JsonNode>()
                    skipWs()
                    if (idx < s.length && s[idx] == '}') {
                        idx++
                        return JsonNode.Obj(map)
                    }
                    while (idx < s.length) {
                        skipWs()
                        val key = parseString() ?: break
                        skipWs()
                        if (idx >= s.length || s[idx] != ':') break
                        idx++
                        val value = parseValue(depth + 1) ?: JsonNode.Prim(null)
                        map[key] = value
                        skipWs()
                        if (idx < s.length && s[idx] == ',') {
                            idx++
                            continue
                        }
                        if (idx < s.length && s[idx] == '}') {
                            idx++
                            break
                        }
                        break
                    }
                    JsonNode.Obj(map)
                }
                '[' -> {
                    idx++
                    val items = ArrayList<JsonNode>()
                    skipWs()
                    if (idx < s.length && s[idx] == ']') {
                        idx++
                        return JsonNode.Arr(items)
                    }
                    while (idx < s.length) {
                        val value = parseValue(depth + 1) ?: break
                        items.add(value)
                        skipWs()
                        if (idx < s.length && s[idx] == ',') {
                            idx++
                            continue
                        }
                        if (idx < s.length && s[idx] == ']') {
                            idx++
                            break
                        }
                        break
                    }
                    JsonNode.Arr(items)
                }
                '"' -> JsonNode.Str(parseString() ?: return null)
                else -> {
                    val start = idx
                    while (idx < s.length && s[idx] !in charArrayOf(',', '}', ']') && !s[idx].isWhitespace()) {
                        idx++
                    }
                    if (start == idx) return null
                    val raw = s.substring(start, idx)
                    when {
                        raw.equals("null", ignoreCase = true) -> JsonNode.Prim(null)
                        raw.equals("true", ignoreCase = true) -> JsonNode.Prim("true")
                        raw.equals("false", ignoreCase = true) -> JsonNode.Prim("false")
                        else -> JsonNode.Prim(raw)
                    }
                }
            }
        }

        return parseValue(0)
    }

    private fun JsonNode.asDisplayString(recursive: Boolean): String? = when (this) {
        is JsonNode.Str -> value
        is JsonNode.Prim -> value
        is JsonNode.Arr -> {
            val scalars = items.mapNotNull { it.asDisplayString(false)?.takeIf(String::isNotBlank) }
            if (recursive) {
                scalars.joinToString(", ").takeIf { it.isNotBlank() }
            } else {
                scalars.firstOrNull()
            }
        }
        is JsonNode.Obj -> entries.entries.joinToString(prefix = "{", postfix = "}") { (k, v) ->
            val renderedVal = when (v) {
                is JsonNode.Str -> "\"${escapeJson(v.value)}\""
                is JsonNode.Prim -> v.value ?: "null"
                is JsonNode.Arr -> "[${v.items.size}]"
                is JsonNode.Obj -> "{...}"
            }
            "\"${escapeJson(k)}\":$renderedVal"
        }
    }

    internal fun extractJsonPathValue(
        jsonText: String,
        path: String,
        recursive: Boolean = false
    ): String? {
        if (jsonText.isBlank() || path.isBlank()) return null
        val normalizedPath = path.trim()
            .replace(Regex("""\[(\d+)\]"""), ".$1")
            .trimStart('.')
        val segments = normalizedPath.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        val root = parseJsonDom(jsonText) ?: return null
        return resolveJsonSegments(root, segments, 0, recursive)
    }

    private fun resolveJsonSegments(
        node: JsonNode,
        segments: List<String>,
        segIndex: Int,
        recursive: Boolean
    ): String? {
        if (segIndex >= segments.size) {
            return node.asDisplayString(recursive)
        }
        val key = segments[segIndex]
        val isLast = segIndex == segments.lastIndex
        return when (node) {
            is JsonNode.Obj -> {
                val child = node.entries[key] ?: return null
                if (isLast) {
                    child.asDisplayString(recursive)
                } else {
                    resolveJsonSegments(child, segments, segIndex + 1, recursive)
                }
            }
            is JsonNode.Arr -> {
                val arrayIdx = key.toIntOrNull()
                if (arrayIdx != null) {
                    val item = node.items.getOrNull(arrayIdx) ?: return null
                    if (isLast) item.asDisplayString(false)
                    else resolveJsonSegments(item, segments, segIndex + 1, recursive)
                } else if (recursive) {
                    val collected = node.items.mapNotNull { item ->
                        resolveJsonSegments(item, segments, segIndex, false)
                    }.filter { it.isNotBlank() }
                    if (collected.isEmpty()) null else collected.joinToString(", ")
                } else {
                    val first = node.items.firstOrNull() ?: return null
                    resolveJsonSegments(first, segments, segIndex, false)
                }
            }
            is JsonNode.Str, is JsonNode.Prim -> null
        }
    }

    /**
     * Scans a sample JSON body and discovers up to 14 useful `dotPath -> sampleValue` pairs
     * so users can generate Parse Rules and Checkpoints with 1 tap.
     */
    fun discoverJsonKeySuggestions(jsonText: String): List<Pair<String, String>> {
        val root = parseJsonDom(jsonText) as? JsonNode.Obj ?: return emptyList()
        val results = LinkedHashMap<String, String>()

        fun scanNode(node: JsonNode, prefix: String, depth: Int) {
            if (depth > 3 || results.size >= 14) return
            when (node) {
                is JsonNode.Obj -> {
                    for ((key, child) in node.entries) {
                        if (results.size >= 14) break
                        val fullPath = if (prefix.isEmpty()) key else "$prefix.$key"
                        when (child) {
                            is JsonNode.Obj -> scanNode(child, fullPath, depth + 1)
                            is JsonNode.Arr -> {
                                val firstEl = child.items.firstOrNull()
                                when (firstEl) {
                                    is JsonNode.Obj -> scanNode(firstEl, "$fullPath[0]", depth + 1)
                                    is JsonNode.Str -> if (firstEl.value.isNotBlank()) {
                                        results.putIfAbsent("$fullPath[0]", firstEl.value.take(36))
                                    }
                                    is JsonNode.Prim -> firstEl.value?.takeIf { it.isNotBlank() }?.let {
                                        results.putIfAbsent("$fullPath[0]", it.take(36))
                                    }
                                    else -> Unit
                                }
                            }
                            is JsonNode.Str -> {
                                val v = child.value.trim()
                                if (v.isNotEmpty() && !key.equals("headers", ignoreCase = true)) {
                                    results.putIfAbsent(fullPath, v.take(36))
                                }
                            }
                            is JsonNode.Prim -> {
                                child.value?.takeIf { it.isNotBlank() }?.let {
                                    results.putIfAbsent(fullPath, it.take(36))
                                }
                            }
                        }
                    }
                }
                else -> Unit
            }
        }

        scanNode(root, "", 0)
        return results.entries.map { it.key to it.value }
    }

    internal fun extractLeftRightValue(
        text: String,
        left: String,
        right: String,
        recursive: Boolean = false
    ): String? {
        if (text.isEmpty() || left.isEmpty()) return null
        if (!recursive) {
            val startIdx = text.indexOf(left)
            if (startIdx < 0) return null
            val contentStart = startIdx + left.length
            if (right.isEmpty()) {
                return text.substring(contentStart).lineSequence().firstOrNull()?.trim()
            }
            val endIdx = text.indexOf(right, startIndex = contentStart)
            if (endIdx < 0) return null
            return text.substring(contentStart, endIdx)
        }

        val matches = mutableListOf<String>()
        var cursor = 0
        while (cursor < text.length && matches.size < 20) {
            val startIdx = text.indexOf(left, startIndex = cursor)
            if (startIdx < 0) break
            val contentStart = startIdx + left.length
            if (right.isEmpty()) {
                val line = text.substring(contentStart).lineSequence().firstOrNull()?.trim().orEmpty()
                if (line.isNotEmpty()) matches.add(line)
                break
            }
            val endIdx = text.indexOf(right, startIndex = contentStart)
            if (endIdx < 0) break
            val piece = text.substring(contentStart, endIdx).trim()
            if (piece.isNotEmpty()) matches.add(piece)
            cursor = endIdx + right.length.coerceAtLeast(1)
        }
        return if (matches.isEmpty()) null else matches.joinToString(", ")
    }

    internal fun extractRegexGroupValue(
        text: String,
        patternStr: String,
        recursive: Boolean = false
    ): String? {
        if (text.isEmpty() || patternStr.isBlank()) return null
        return runCatching {
            val regex = Regex(patternStr, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            if (!recursive) {
                val match = regex.find(text) ?: return@runCatching null
                if (match.groupValues.size > 1) match.groupValues[1] else match.value
            } else {
                val all = regex.findAll(text).take(20).map { m ->
                    if (m.groupValues.size > 1) m.groupValues[1] else m.value
                }.filter { it.isNotBlank() }.toList()
                if (all.isEmpty()) null else all.joinToString(", ")
            }
        }.getOrNull()
    }

    internal fun extractCookieValue(
        responseHeaders: Map<String, String>,
        cookieName: String
    ): String? {
        if (responseHeaders.isEmpty() || cookieName.isBlank()) return null
        val cookieHeaders = responseHeaders.entries
            .filter {
                it.key.equals("set-cookie", ignoreCase = true) ||
                    it.key.equals("cookie", ignoreCase = true) ||
                    it.key.startsWith("set-cookie", ignoreCase = true)
            }
            .map { it.value }
        if (cookieHeaders.isEmpty()) return null

        val parsedPairs = LinkedHashMap<String, String>()
        for (rawHeader in cookieHeaders) {
            rawHeader.split('\n', ';').forEach { segment ->
                val trimmed = segment.trim()
                val eq = trimmed.indexOf('=')
                if (eq > 0) {
                    val k = trimmed.substring(0, eq).trim()
                    val v = trimmed.substring(eq + 1).trim()
                    val lowerK = k.lowercase(Locale.ROOT)
                    if (lowerK !in setOf("path", "domain", "expires", "max-age", "samesite", "secure", "httponly")) {
                        parsedPairs[k] = v
                    }
                }
            }
        }
        if (parsedPairs.isEmpty()) return null
        if (cookieName == "*") {
            return parsedPairs.entries.joinToString("; ") { "${it.key}=${it.value}" }
        }
        return parsedPairs[cookieName]
            ?: parsedPairs.entries.firstOrNull { it.key.equals(cookieName, ignoreCase = true) }?.value
    }

    /**
     * Lightweight HTML / CSS attribute & inner-text extractor.
     * Examples:
     * - `input[name=csrf_token]@value`
     * - `meta[name=csrf-token]@content`
     * - `title` (extracts `<title>...</title>`)
     */
    internal fun extractHtmlCssValue(htmlText: String, selector: String): String? {
        if (htmlText.isBlank() || selector.isBlank()) return null
        val cleanSel = selector.trim()
        val targetAttr = cleanSel.substringAfter('@', "").trim()
        val elementPart = cleanSel.substringBefore('@').trim()

        val attrFilterMatch = Regex("""^([A-Za-z0-9_-]+)?\[([A-Za-z0-9_:-]+)\s*=\s*['"]?([^'"\]]+)['"]?\]$""")
            .find(elementPart)

        if (attrFilterMatch != null) {
            val tag = attrFilterMatch.groupValues[1].ifBlank { "[A-Za-z0-9_-]+" }
            val filterAttr = Regex.escape(attrFilterMatch.groupValues[2])
            val filterVal = Regex.escape(attrFilterMatch.groupValues[3])
            val tagRegex = Regex(
                """<$tag\b[^>]*\b$filterAttr\s*=\s*['"]?$filterVal['"]?[^>]*>""",
                RegexOption.IGNORE_CASE
            )
            val tagMatch = tagRegex.find(htmlText) ?: return null
            val tagHtml = tagMatch.value
            val attrToRead = targetAttr.ifEmpty { "value" }
            val valRegex = Regex("""\b${Regex.escape(attrToRead)}\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
            return valRegex.find(tagHtml)?.groupValues?.getOrNull(2)
        }

        val simpleTag = Regex.escape(elementPart.removePrefix("#").removePrefix("."))
        if (targetAttr.isNotEmpty()) {
            val tagRegex = Regex("""<$simpleTag\b[^>]*>""", RegexOption.IGNORE_CASE)
            val tagHtml = tagRegex.find(htmlText)?.value ?: return null
            val valRegex = Regex("""\b${Regex.escape(targetAttr)}\s*=\s*(['"])(.*?)\1""", RegexOption.IGNORE_CASE)
            return valRegex.find(tagHtml)?.groupValues?.getOrNull(2)
        }

        val innerRegex = Regex(
            """<$simpleTag\b[^>]*>(.*?)</$simpleTag>""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        )
        return innerRegex.find(htmlText)?.groupValues?.getOrNull(1)?.trim()
    }

    // =========================================================================
    // 2. Checkpoints / Keycheck Rules Serialization & Evaluation
    // =========================================================================

    fun serializeCheckpointRules(rules: List<CustomCheckpointRule>): String {
        if (rules.isEmpty()) return ""
        return rules.mapNotNull { rule ->
            if (rule.expectedValue.isBlank() &&
                rule.condition != CheckpointCondition.CAPTURED_VAR_CONTAINS &&
                rule.condition != CheckpointCondition.CAPTURED_VAR_NOT_CONTAINS
            ) {
                return@mapNotNull null
            }
            listOf(
                rule.verdict.name,
                rule.condition.name,
                rule.targetKey.trim().replace(FIELD_SEP, " ").replace("\n", " "),
                rule.expectedValue.trim().replace(FIELD_SEP, " ").replace("\n", " "),
                rule.label.trim().replace(FIELD_SEP, " ").replace("\n", " ")
            ).joinToString(FIELD_SEP)
        }.joinToString(RULE_LINE_SEP)
    }

    fun parseCheckpointRules(spec: String): List<CustomCheckpointRule> {
        if (spec.isBlank()) return emptyList()
        return spec.lines().mapNotNull { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@mapNotNull null
            val parts = if (line.contains(FIELD_SEP)) {
                line.split(FIELD_SEP)
            } else {
                line.split(LEGACY_PIPE_SEP)
            }
            if (parts.size < 4) return@mapNotNull null
            val verdict = runCatching { ResultBucket.valueOf(parts[0].trim().uppercase(Locale.ROOT)) }
                .getOrDefault(ResultBucket.UNKNOWN)
            val condition = runCatching { CheckpointCondition.valueOf(parts[1].trim().uppercase(Locale.ROOT)) }
                .getOrDefault(CheckpointCondition.BODY_CONTAINS)
            val targetKey = parts.getOrElse(2) { "" }.trim()
            val expectedValue = parts.getOrElse(3) { "" }.trim()
            val label = parts.getOrElse(4) { "" }.trim()
            CustomCheckpointRule(
                verdict = verdict,
                condition = condition,
                targetKey = targetKey,
                expectedValue = expectedValue,
                label = label
            )
        }
    }

    /**
     * Evaluates ordered [CustomCheckpointRule] items and returns the first matching `(ResultBucket, reasonLabel)`
     * or `null` if no checkpoint matched (allowing fallback to keyword/status classification).
     */
    fun evaluateCheckpoints(
        statusCode: Int,
        bodyStr: String,
        captures: Map<String, String>,
        rules: List<CustomCheckpointRule>,
        responseHeaders: Map<String, String> = emptyMap(),
        latencyMs: Long = 0L
    ): Pair<ResultBucket, String>? {
        return evaluateCheckpointsDetailed(
            statusCode = statusCode,
            bodyStr = bodyStr,
            captures = captures,
            rules = rules,
            responseHeaders = responseHeaders,
            latencyMs = latencyMs
        ).winner
    }

    /**
     * Evaluates all [CustomCheckpointRule] items in priority order (#1 -> #N), returning both the
     * winning verdict and a step-by-step trace for the Keycheck Debugger UI.
     */
    fun evaluateCheckpointsDetailed(
        statusCode: Int,
        bodyStr: String,
        captures: Map<String, String>,
        rules: List<CustomCheckpointRule>,
        responseHeaders: Map<String, String> = emptyMap(),
        latencyMs: Long = 0L
    ): CheckpointEvaluationResult {
        if (rules.isEmpty()) return CheckpointEvaluationResult(null, emptyList())
        var winner: Pair<ResultBucket, String>? = null
        val steps = ArrayList<CheckpointEvalStep>(rules.size)

        for ((idx, rule) in rules.withIndex()) {
            val expected = rule.expectedValue.trim()
            var detail: String
            val matched = when (rule.condition) {
                CheckpointCondition.BODY_CONTAINS -> {
                    val ok = expected.isNotEmpty() && bodyStr.contains(expected, ignoreCase = true)
                    detail = if (ok) "Body contains \"$expected\"" else "Not found in body"
                    ok
                }
                CheckpointCondition.BODY_CONTAINS_ANY -> {
                    val terms = expected.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    val hit = terms.firstOrNull { term -> bodyStr.contains(term, ignoreCase = true) }
                    detail = if (hit != null) "Matched term \"$hit\"" else "None of ${terms.size} terms matched"
                    hit != null
                }
                CheckpointCondition.BODY_CONTAINS_ALL -> {
                    val terms = expected.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    val ok = terms.isNotEmpty() && terms.all { term -> bodyStr.contains(term, ignoreCase = true) }
                    detail = if (ok) "All ${terms.size} terms matched" else "Missing required term(s)"
                    ok
                }
                CheckpointCondition.BODY_NOT_CONTAINS -> {
                    val ok = expected.isNotEmpty() && !bodyStr.contains(expected, ignoreCase = true)
                    detail = if (ok) "Body excludes \"$expected\"" else "Forbidden text found"
                    ok
                }
                CheckpointCondition.STATUS_CODE_EQUALS -> {
                    val ok = expected.toIntOrNull() == statusCode
                    detail = "HTTP $statusCode ${if (ok) "==" else "!="} $expected"
                    ok
                }
                CheckpointCondition.STATUS_CODE_IN -> {
                    val codes = expected.split(',', '|', ';', ' ').mapNotNull { it.trim().toIntOrNull() }
                    val ok = statusCode in codes
                    detail = "HTTP $statusCode ${if (ok) "in" else "not in"} [$expected]"
                    ok
                }
                CheckpointCondition.JSON_KEY_EQUALS -> {
                    val actual = extractJsonPathValue(bodyStr, rule.targetKey.trim())
                    val ok = actual != null && (expected.isEmpty() || actual.equals(expected, ignoreCase = true))
                    detail = "${rule.targetKey} = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.JSON_KEY_CONTAINS -> {
                    val actual = extractJsonPathValue(bodyStr, rule.targetKey.trim())
                    val ok = actual != null && (expected.isEmpty() || actual.contains(expected, ignoreCase = true))
                    detail = "${rule.targetKey} = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.CAPTURED_VAR_CONTAINS -> {
                    val actual = lookupCapturedVar(captures, rule.targetKey)
                    val ok = actual != null && (expected.isEmpty() || actual.contains(expected, ignoreCase = true))
                    detail = "var[${rule.targetKey}] = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.CAPTURED_VAR_EQUALS -> {
                    val actual = lookupCapturedVar(captures, rule.targetKey)
                    val ok = actual != null && actual.equals(expected, ignoreCase = true)
                    detail = "var[${rule.targetKey}] = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.CAPTURED_VAR_NOT_CONTAINS -> {
                    val actual = lookupCapturedVar(captures, rule.targetKey)
                    val ok = actual != null && (expected.isEmpty() || !actual.contains(expected, ignoreCase = true))
                    detail = "var[${rule.targetKey}] = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.HEADER_CONTAINS -> {
                    val hdrKey = rule.targetKey.trim().lowercase(Locale.ROOT)
                    val actual = responseHeaders[hdrKey]
                        ?: responseHeaders.entries.firstOrNull { it.key.equals(hdrKey, ignoreCase = true) }?.value
                    val ok = actual != null && (expected.isEmpty() || actual.contains(expected, ignoreCase = true))
                    detail = "hdr[${rule.targetKey}] = ${actual ?: "null"}"
                    ok
                }
                CheckpointCondition.REGEX_MATCH -> {
                    val ok = expected.isNotEmpty() && runCatching {
                        Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(bodyStr)
                    }.getOrDefault(false)
                    detail = if (ok) "Regex matched body" else "Regex did not match"
                    ok
                }
                CheckpointCondition.LATENCY_GT_MS -> {
                    val threshold = expected.toLongOrNull() ?: Long.MAX_VALUE
                    val ok = latencyMs > threshold
                    detail = "${latencyMs}ms ${if (ok) ">" else "<="} ${threshold}ms"
                    ok
                }
            }

            val isFirstWinner = matched && winner == null
            if (isFirstWinner) {
                val tag = rule.label.ifBlank {
                    when (rule.condition) {
                        CheckpointCondition.STATUS_CODE_EQUALS,
                        CheckpointCondition.STATUS_CODE_IN -> "HTTP $statusCode"
                        CheckpointCondition.JSON_KEY_EQUALS,
                        CheckpointCondition.JSON_KEY_CONTAINS -> "${rule.targetKey}=${expected.take(18)}"
                        CheckpointCondition.CAPTURED_VAR_CONTAINS,
                        CheckpointCondition.CAPTURED_VAR_EQUALS,
                        CheckpointCondition.CAPTURED_VAR_NOT_CONTAINS -> "${rule.targetKey}${if (expected.isNotEmpty()) "=$expected" else ""}"
                        CheckpointCondition.HEADER_CONTAINS -> "Header ${rule.targetKey}"
                        CheckpointCondition.LATENCY_GT_MS -> ">${expected}ms"
                        else -> expected.take(24)
                    }
                }
                winner = rule.verdict to "Checkpoint: $tag"
            }

            steps.add(
                CheckpointEvalStep(
                    index = idx + 1,
                    rule = rule,
                    matched = matched,
                    isWinner = isFirstWinner,
                    detail = detail
                )
            )
        }
        return CheckpointEvaluationResult(winner, steps)
    }

    private fun lookupCapturedVar(captures: Map<String, String>, key: String): String? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return captures[k] ?: captures.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value
    }

    // =========================================================================
    // 3. Proxy List Parsing, Diagnostics & Rotation
    // =========================================================================

    fun parseProxyPool(
        proxyListText: String,
        defaultProtocol: ProxyProtocol = ProxyProtocol.HTTP
    ): List<ParsedProxyEndpoint> {
        if (proxyListText.isBlank()) return emptyList()
        return proxyListText.lines().mapNotNull { rawLine ->
            parseSingleProxyLine(rawLine, defaultProtocol)
        }
    }

    /**
     * Returns 1-based line numbers and snippets for any non-empty, non-comment lines in `proxyListText`
     * that fail proxy syntax validation.
     */
    fun findInvalidProxyLines(
        proxyListText: String,
        defaultProtocol: ProxyProtocol = ProxyProtocol.HTTP
    ): List<Pair<Int, String>> {
        if (proxyListText.isBlank()) return emptyList()
        val invalid = mutableListOf<Pair<Int, String>>()
        proxyListText.lines().forEachIndexed { idx, raw ->
            val trimmed = raw.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.startsWith("//")) {
                if (parseSingleProxyLine(trimmed, defaultProtocol) == null) {
                    invalid.add((idx + 1) to trimmed.take(36))
                }
            }
        }
        return invalid
    }

    fun parseSingleProxyLine(
        rawLine: String,
        defaultProtocol: ProxyProtocol = ProxyProtocol.HTTP
    ): ParsedProxyEndpoint? {
        var line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) return null

        var protocol = defaultProtocol
        val lower = line.lowercase(Locale.ROOT)
        when {
            lower.startsWith("socks5://") -> {
                protocol = ProxyProtocol.SOCKS5
                line = line.substring("socks5://".length).trim()
            }
            lower.startsWith("socks://") -> {
                protocol = ProxyProtocol.SOCKS5
                line = line.substring("socks://".length).trim()
            }
            lower.startsWith("http://") -> {
                protocol = ProxyProtocol.HTTP
                line = line.substring("http://".length).trim()
            }
            lower.startsWith("https://") -> {
                protocol = ProxyProtocol.HTTP
                line = line.substring("https://".length).trim()
            }
        }

        if (line.contains('@')) {
            val authPart = line.substringBeforeLast('@')
            val hostPortPart = line.substringAfterLast('@')
            val host = hostPortPart.substringBeforeLast(':').trim()
            val port = hostPortPart.substringAfterLast(':', "").trim().toIntOrNull()
            if (host.isEmpty() || port == null || port !in 1..65535) return null
            val user = authPart.substringBefore(':', "").trim()
            val pass = authPart.substringAfter(':', "").trim()
            return ParsedProxyEndpoint(
                host = host,
                port = port,
                username = user,
                password = pass,
                protocol = protocol
            )
        }

        val parts = line.split(':')
        if (parts.size < 2) return null
        val host = parts[0].trim()
        val port = parts[1].trim().toIntOrNull()
        if (host.isEmpty() || port == null || port !in 1..65535) return null
        val user = parts.getOrElse(2) { "" }.trim()
        val pass = if (parts.size >= 4) parts.subList(3, parts.size).joinToString(":").trim() else ""

        return ParsedProxyEndpoint(
            host = host,
            port = port,
            username = user,
            password = pass,
            protocol = protocol
        )
    }

    fun selectProxyForRequest(config: CustomHttpConfig): ParsedProxyEndpoint? {
        if (!config.proxyEnabled) return null
        val pool = parseProxyPool(config.proxyListText, config.proxyProtocol)
        if (pool.isEmpty()) return null
        return when (config.proxyRotation) {
            ProxyRotationMode.STICKY -> pool.first()
            ProxyRotationMode.RANDOM -> pool[Random.nextInt(pool.size)]
            ProxyRotationMode.ROUND_ROBIN -> {
                val idx = (roundRobinCounter.getAndIncrement() and Int.MAX_VALUE) % pool.size
                pool[idx]
            }
        }
    }

    // =========================================================================
    // 4. Universal Request Importer: cURL, JS fetch(), Raw HTTP & LoliScript
    // =========================================================================

    /**
     * Imports a Chrome/DevTools `curl ...`, `fetch("...", { ... })`, Burp/HTTP Toolkit Raw HTTP request,
     * or OpenBullet/SilverBullet LoliScript snippet into a [CustomHttpConfig], extracting URL, HTTP method,
     * headers, User-Agent, request body, and (for LoliScript) Parse & Keycheck rules!
     */
    fun importFromCurlOrFetch(
        rawSnippet: String,
        autoTokenizeFields: Boolean = true
    ): Result<CustomHttpConfig> = runCatching {
        val snippet = rawSnippet.trim()
        require(snippet.isNotEmpty()) {
            "Paste a cURL command, JS fetch(...), Raw HTTP request, or LoliScript block first."
        }

        // Check if LoliScript / OpenBullet / SilverBullet block
        if (isLoliScriptSnippet(snippet)) {
            return@runCatching parseLoliScriptSnippet(snippet, autoTokenizeFields)
        }

        val parsed = when {
            isRawHttpSnippet(snippet) -> parseRawHttpSnippet(snippet)
            snippet.contains("fetch(", ignoreCase = true) &&
                !snippet.trimStart().startsWith("curl", ignoreCase = true) -> parseFetchSnippet(snippet)
            else -> parseCurlSnippet(snippet)
        }

        require(parsed.url.startsWith("http://", ignoreCase = true) || parsed.url.startsWith("https://", ignoreCase = true)) {
            "Could not detect an http:// or https:// URL in the snippet."
        }

        var userAgent = CustomHttpConfig.DEFAULT_USER_AGENT
        val filteredHeaders = mutableListOf<String>()
        for ((k, v) in parsed.headers) {
            if (k.equals("user-agent", ignoreCase = true)) {
                userAgent = v
            } else if (!k.equals("content-length", ignoreCase = true) && !k.equals("host", ignoreCase = true)) {
                filteredHeaders.add("$k: $v")
            }
        }

        val contentType = parsed.headers
            .firstOrNull { it.first.equals("content-type", ignoreCase = true) }
            ?.second
            ?.lowercase(Locale.ROOT)
            .orEmpty()

        val rawBody = parsed.body
        val tokenizedBody = if (autoTokenizeFields && rawBody.isNotBlank()) {
            autoTokenizePayload(rawBody)
        } else {
            rawBody
        }
        val tokenizedUrl = if (autoTokenizeFields) {
            autoTokenizePayload(parsed.url)
        } else {
            parsed.url
        }

        val resolvedMethod = when {
            parsed.explicitMethod.equals("GET", ignoreCase = true) && tokenizedBody.isBlank() -> HttpRequestMethod.GET
            contentType.contains("x-www-form-urlencoded") -> HttpRequestMethod.POST_FORM
            contentType.contains("application/json") || tokenizedBody.trimStart().startsWith("{") -> HttpRequestMethod.POST_JSON
            tokenizedBody.isNotBlank() && tokenizedBody.contains('=') && !tokenizedBody.trimStart().startsWith("{") -> HttpRequestMethod.POST_FORM
            tokenizedBody.isNotBlank() -> HttpRequestMethod.POST_JSON
            else -> HttpRequestMethod.GET
        }

        val hostLabel = Regex("""https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(parsed.url)?.groupValues?.getOrNull(1).orEmpty().ifEmpty { "Imported" }

        CustomHttpConfig(
            profileName = "$hostLabel Config",
            url = tokenizedUrl,
            method = resolvedMethod,
            headersText = filteredHeaders.joinToString("\n"),
            bodyTemplate = tokenizedBody,
            compressBodyGzip = parsed.compressGzip,
            userAgent = userAgent,
            followRedirects = true
        )
    }

    private data class RawParsedSnippet(
        val url: String,
        val explicitMethod: String,
        val headers: List<Pair<String, String>>,
        val body: String,
        val compressGzip: Boolean = false
    )

    private fun isRawHttpSnippet(snippet: String): Boolean {
        val firstLine = snippet.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        return Regex("""^(GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\s+(\S+)\s+HTTP/\d(?:\.\d)?$""", RegexOption.IGNORE_CASE)
            .matches(firstLine)
    }

    private fun parseRawHttpSnippet(snippet: String): RawParsedSnippet {
        val normalized = snippet.replace("\r\n", "\n")
        val lines = normalized.lines()
        val firstIdx = lines.indexOfFirst { it.isNotBlank() }
        val requestLine = lines[firstIdx].trim()
        val reqMatch = Regex("""^(GET|POST|PUT|PATCH|DELETE|OPTIONS|HEAD)\s+(\S+)\s+HTTP/\d(?:\.\d)?$""", RegexOption.IGNORE_CASE)
            .find(requestLine)
            ?: throw IllegalArgumentException("Invalid Raw HTTP request line.")

        val method = reqMatch.groupValues[1].uppercase(Locale.ROOT)
        val pathOrUrl = reqMatch.groupValues[2].trim()

        val headers = mutableListOf<Pair<String, String>>()
        var hostHeader = ""
        var i = firstIdx + 1
        while (i < lines.size) {
            val line = lines[i]
            if (line.isBlank()) {
                i++
                break
            }
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim()
                val v = line.substring(colon + 1).trim()
                if (k.equals("host", ignoreCase = true)) {
                    hostHeader = v
                } else {
                    headers.add(k to v)
                }
            }
            i++
        }

        val body = if (i < lines.size) {
            lines.subList(i, lines.size).joinToString("\n").trim()
        } else ""

        val fullUrl = when {
            pathOrUrl.startsWith("http://", ignoreCase = true) || pathOrUrl.startsWith("https://", ignoreCase = true) -> pathOrUrl
            hostHeader.isNotBlank() -> {
                val scheme = if (hostHeader.endsWith(":80")) "http" else "https"
                val slashPath = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
                "$scheme://$hostHeader$slashPath"
            }
            else -> throw IllegalArgumentException("Raw HTTP request is missing a Host: header.")
        }

        return RawParsedSnippet(
            url = fullUrl,
            explicitMethod = method,
            headers = headers,
            body = body
        )
    }

    private fun isLoliScriptSnippet(snippet: String): Boolean {
        val upper = snippet.uppercase(Locale.ROOT)
        return (upper.contains("REQUEST ") && (
            upper.contains("HEADER ") ||
                upper.contains("CONTENT ") ||
                upper.contains("KEYCHECK") ||
                upper.contains("PARSE ")
            )) ||
            upper.contains("FUNCTION ") ||
            upper.contains("BROWSERACTION ") ||
            upper.contains("ELEMENTACTION ") ||
            upper.contains("SELENIUM ") ||
            upper.contains("SOLVECAPTCHA ")
    }

    private fun parseLoliScriptSnippet(snippet: String, autoTokenize: Boolean): CustomHttpConfig {
        fun convertLoliTokens(input: String): String {
            var s = input
                .replace("<USER>", "{user}", ignoreCase = true)
                .replace("<USERNAME>", "{user}", ignoreCase = true)
                .replace("<EMAIL>", "{email}", ignoreCase = true)
                .replace("<PASS>", "{pass}", ignoreCase = true)
                .replace("<PASSWORD>", "{pass}", ignoreCase = true)
                .replace("<CC>", "{card}", ignoreCase = true)
                .replace("<CARD>", "{card}", ignoreCase = true)
                .replace("<PAN>", "{card}", ignoreCase = true)
                .replace("<MM>", "{mm}", ignoreCase = true)
                .replace("<MONTH>", "{mm}", ignoreCase = true)
                .replace("<YY>", "{yy}", ignoreCase = true)
                .replace("<YYYY>", "{yyyy}", ignoreCase = true)
                .replace("<YEAR>", "{yyyy}", ignoreCase = true)
                .replace("<CVV>", "{cvv}", ignoreCase = true)
                .replace("<CVC>", "{cvv}", ignoreCase = true)
                .replace("<BIN>", "{bin}", ignoreCase = true)
                .replace("<LISTA>", "{lista}", ignoreCase = true)
            // Convert remaining <VAR> references to {cap:VAR}
            s = Regex("""<([A-Za-z0-9_]{2,32})>""").replace(s) { m ->
                "{cap:${m.groupValues[1]}}"
            }
            return if (autoTokenize) autoTokenizePayload(s) else s
        }

        val reqMatch = Regex("""REQUEST\s+(GET|POST|PUT|DELETE|PATCH)\s+"([^"]+)"""", RegexOption.IGNORE_CASE)
            .find(snippet)
        val rawMethod = reqMatch?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT) ?: "GET"
        val url = reqMatch?.groupValues?.getOrNull(2)?.trim()?.let { convertLoliTokens(it) }.orEmpty()

        val importedBlocks = mutableListOf<ConfigBlock>()
        Regex("""FUNCTION\s+([A-Za-z0-9_-]+)(?:\s+([A-Za-z0-9_-]+))?\s+"((?:\\.|[^"\\])*)"(?:\s*->\s*(VAR|CAP)\s+"([^"]+)")?""", RegexOption.IGNORE_CASE)
            .findAll(snippet).forEach { m ->
                val fnName = m.groupValues[1].uppercase(Locale.ROOT)
                val subArg = m.groupValues[2].uppercase(Locale.ROOT)
                val valStr = convertLoliTokens(unescapeJsonString(m.groupValues[3]))
                val isCap = m.groupValues[4].equals("CAP", ignoreCase = true)
                val outVar = m.groupValues[5].ifBlank { "fn_out" }
                val blk = when {
                    fnName == "HASH" -> ConfigBlock(type = BlockType.FUNC_HASH, inputTarget = valStr, secondaryParam = subArg.ifBlank { "SHA-256" }, outputVar = outVar, captureToOutput = isCap)
                    fnName == "BASE64ENCODE" -> ConfigBlock(type = BlockType.FUNC_BASE64_ENCODE, inputTarget = valStr, outputVar = outVar, captureToOutput = isCap)
                    fnName == "BASE64DECODE" -> ConfigBlock(type = BlockType.FUNC_BASE64_DECODE, inputTarget = valStr, outputVar = outVar, captureToOutput = isCap)
                    fnName == "URLENCODE" -> ConfigBlock(type = BlockType.FUNC_URL_ENCODE, inputTarget = valStr, secondaryParam = "ENCODE", outputVar = outVar, captureToOutput = isCap)
                    fnName == "RANDOMSTRING" -> ConfigBlock(type = BlockType.FUNC_RANDOM_STRING, inputTarget = "HEX", secondaryParam = "16", outputVar = outVar, captureToOutput = isCap)
                    fnName == "CURRENTUNIXTIME" -> ConfigBlock(type = BlockType.FUNC_TIMESTAMP, inputTarget = "UNIX_S", outputVar = outVar, captureToOutput = isCap)
                    fnName == "COMPUTE" -> ConfigBlock(type = BlockType.FUNC_MATH_EVAL, inputTarget = valStr, outputVar = outVar, captureToOutput = isCap)
                    else -> ConfigBlock(type = BlockType.FUNC_CONSTANT, inputTarget = valStr, outputVar = outVar, captureToOutput = isCap)
                }
                importedBlocks.add(blk)
            }

        Regex("""BROWSERACTION\s+([A-Za-z]+)(?:\s+"((?:\\.|[^"\\])*)")?(?:\s+"((?:\\.|[^"\\])*)")?(?:\s*->\s*(VAR|CAP)\s+"([^"]+)")?""", RegexOption.IGNORE_CASE)
            .findAll(snippet).forEach { m ->
                val action = m.groupValues[1].uppercase(Locale.ROOT)
                val arg1 = convertLoliTokens(unescapeJsonString(m.groupValues[2]))
                val arg2 = convertLoliTokens(unescapeJsonString(m.groupValues[3]))
                val isCap = m.groupValues[4].equals("CAP", ignoreCase = true)
                val outVar = m.groupValues[5]
                val bType = when (action) {
                    "OPEN" -> BlockType.BROWSER_OPEN
                    "NAVIGATE" -> BlockType.BROWSER_NAVIGATE
                    "CLICK" -> BlockType.BROWSER_CLICK
                    "SENDKEYS", "TYPE" -> BlockType.BROWSER_TYPE
                    "WAITFORELEMENT" -> BlockType.BROWSER_WAIT_ELEMENT
                    "EXECUTEJS" -> BlockType.BROWSER_EXEC_JS
                    "GETCOOKIES" -> BlockType.BROWSER_GET_COOKIES
                    "SCREENSHOT" -> BlockType.BROWSER_SCREENSHOT
                    else -> BlockType.BROWSER_CLOSE
                }
                importedBlocks.add(
                    ConfigBlock(
                        type = bType,
                        inputTarget = arg1,
                        secondaryParam = arg2,
                        outputVar = outVar.ifBlank { bType.defaultOutputVar },
                        captureToOutput = isCap
                    )
                )
            }

        Regex("""(?:SELENIUM|ELEMENTACTION)\s+([A-Za-z]+)(?:\s+"((?:\\.|[^"\\])*)")?(?:\s+"((?:\\.|[^"\\])*)")?(?:\s*->\s*(VAR|CAP)\s+"([^"]+)")?""", RegexOption.IGNORE_CASE)
            .findAll(snippet).forEach { m ->
                val action = m.groupValues[1].uppercase(Locale.ROOT)
                val arg1 = convertLoliTokens(unescapeJsonString(m.groupValues[2]))
                val arg2 = convertLoliTokens(unescapeJsonString(m.groupValues[3]))
                val isCap = m.groupValues[4].equals("CAP", ignoreCase = true)
                val outVar = m.groupValues[5]
                val sType = when (action) {
                    "OPEN" -> BlockType.SELENIUM_OPEN
                    "NAVIGATE" -> BlockType.SELENIUM_NAVIGATE
                    "FIND" -> BlockType.SELENIUM_FIND_ELEMENT
                    "SENDKEYS" -> BlockType.SELENIUM_SEND_KEYS
                    "CLICK" -> BlockType.SELENIUM_CLICK
                    "WAIT" -> BlockType.SELENIUM_WAIT
                    "EXECUTEJS" -> BlockType.SELENIUM_EXEC_SCRIPT
                    "GETCOOKIES" -> BlockType.SELENIUM_GET_COOKIES
                    else -> BlockType.SELENIUM_QUIT
                }
                importedBlocks.add(
                    ConfigBlock(
                        type = sType,
                        inputTarget = arg1,
                        secondaryParam = arg2,
                        outputVar = outVar.ifBlank { sType.defaultOutputVar },
                        captureToOutput = isCap
                    )
                )
            }

        Regex("""SOLVECAPTCHA\s+([A-Za-z0-9_]+)\s+"((?:\\.|[^"\\])*)"(?:\s+"((?:\\.|[^"\\])*)")?(?:\s*->\s*(VAR|CAP)\s+"([^"]+)")?""", RegexOption.IGNORE_CASE)
            .findAll(snippet).forEach { m ->
                val cType = runCatching { BlockType.valueOf(m.groupValues[1].uppercase(Locale.ROOT)) }
                    .getOrDefault(BlockType.CAPTCHA_RECAPTCHA_V2)
                val arg1 = convertLoliTokens(unescapeJsonString(m.groupValues[2]))
                val arg2 = convertLoliTokens(unescapeJsonString(m.groupValues[3]))
                val isCap = m.groupValues[4].equals("CAP", ignoreCase = true)
                val outVar = m.groupValues[5]
                importedBlocks.add(
                    ConfigBlock(
                        type = cType,
                        inputTarget = arg1,
                        secondaryParam = arg2.ifBlank { "2Captcha" },
                        outputVar = outVar.ifBlank { cType.defaultOutputVar },
                        captureToOutput = isCap
                    )
                )
            }

        Regex("""SHELL\s+"((?:\\.|[^"\\])*)"(?:\s+"((?:\\.|[^"\\])*)")?(?:\s*->\s*(VAR|CAP)\s+"([^"]+)")?""", RegexOption.IGNORE_CASE)
            .findAll(snippet).forEach { m ->
                importedBlocks.add(
                    ConfigBlock(
                        type = BlockType.INTEROP_SHELL,
                        inputTarget = convertLoliTokens(unescapeJsonString(m.groupValues[1])),
                        secondaryParam = convertLoliTokens(unescapeJsonString(m.groupValues[2])),
                        outputVar = m.groupValues[4].ifBlank { "shell_stdout" },
                        captureToOutput = m.groupValues[3].equals("CAP", ignoreCase = true)
                    )
                )
            }

        Regex("""DELAY\s+"?(\d+)"?""", RegexOption.IGNORE_CASE).findAll(snippet).forEach { m ->
            importedBlocks.add(ConfigBlock(type = BlockType.UTIL_DELAY, inputTarget = m.groupValues[1]))
        }
        Regex("""LOG\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE).findAll(snippet).forEach { m ->
            importedBlocks.add(ConfigBlock(type = BlockType.UTIL_LOG, inputTarget = convertLoliTokens(unescapeJsonString(m.groupValues[1]))))
        }

        if (reqMatch == null && importedBlocks.isEmpty()) {
            throw IllegalArgumentException("Could not find REQUEST <METHOD> \"<URL>\" or block directives in LoliScript.")
        }

        var userAgent = CustomHttpConfig.DEFAULT_USER_AGENT
        val headers = mutableListOf<String>()
        Regex("""HEADER\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE).findAll(snippet).forEach { m ->
            val hdr = unescapeJsonString(m.groupValues[1]).trim()
            val colon = hdr.indexOf(':')
            if (colon > 0) {
                val k = hdr.substring(0, colon).trim()
                val v = convertLoliTokens(hdr.substring(colon + 1).trim())
                if (k.equals("user-agent", ignoreCase = true)) {
                    userAgent = v
                } else if (!k.equals("host", ignoreCase = true) && !k.equals("content-length", ignoreCase = true)) {
                    headers.add("$k: $v")
                }
            }
        }

        Regex("""COOKIE\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE).findAll(snippet).forEach { m ->
            val ck = convertLoliTokens(unescapeJsonString(m.groupValues[1]).trim())
            if (ck.isNotBlank()) headers.add("Cookie: $ck")
        }

        val contentMatch = Regex("""CONTENT\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE).find(snippet)
        val body = contentMatch?.groupValues?.getOrNull(1)?.let { convertLoliTokens(unescapeJsonString(it)) }.orEmpty()

        val ctypeMatch = Regex("""CONTENTTYPE\s+"([^"]*)"""", RegexOption.IGNORE_CASE).find(snippet)
        val ctype = ctypeMatch?.groupValues?.getOrNull(1)?.lowercase(Locale.ROOT).orEmpty()

        val method = when {
            rawMethod == "GET" && body.isBlank() -> HttpRequestMethod.GET
            ctype.contains("json") || body.trimStart().startsWith("{") -> HttpRequestMethod.POST_JSON
            ctype.contains("form") || (body.isNotBlank() && body.contains('=')) -> HttpRequestMethod.POST_FORM
            body.isNotBlank() -> HttpRequestMethod.POST_JSON
            else -> HttpRequestMethod.GET
        }

        val parseRules = mutableListOf<CustomParseRule>()
        // PARSE "<SOURCE>" LR "left" "right" -> VAR/CAP "name"
        Regex(
            """PARSE\s+"[^"]*"\s+LR\s+"((?:\\.|[^"\\])*)"\s+"((?:\\.|[^"\\])*)"[^-\n]*->\s*(?:VAR|CAP)\s+"([^"]+)"""",
            RegexOption.IGNORE_CASE
        ).findAll(snippet).forEach { m ->
            val isCap = m.value.uppercase(Locale.ROOT).contains("-> CAP")
            parseRules.add(
                CustomParseRule(
                    name = m.groupValues[3].trim(),
                    mode = ParseRuleMode.LR,
                    leftDelimiter = unescapeJsonString(m.groupValues[1]),
                    rightDelimiter = unescapeJsonString(m.groupValues[2]),
                    showInOutput = isCap
                )
            )
        }
        // PARSE "<SOURCE>" JSON "field" -> VAR/CAP "name"
        Regex(
            """PARSE\s+"[^"]*"\s+JSON\s+"([^"]+)"[^-\n]*->\s*(?:VAR|CAP)\s+"([^"]+)"""",
            RegexOption.IGNORE_CASE
        ).findAll(snippet).forEach { m ->
            val isCap = m.value.uppercase(Locale.ROOT).contains("-> CAP")
            parseRules.add(
                CustomParseRule(
                    name = m.groupValues[2].trim(),
                    mode = ParseRuleMode.JSON,
                    expression = m.groupValues[1].trim(),
                    showInOutput = isCap
                )
            )
        }
        // PARSE "<SOURCE>" REGEX "pattern" ... -> VAR/CAP "name"
        Regex(
            """PARSE\s+"[^"]*"\s+REGEX\s+"((?:\\.|[^"\\])*)"[^-\n]*->\s*(?:VAR|CAP)\s+"([^"]+)"""",
            RegexOption.IGNORE_CASE
        ).findAll(snippet).forEach { m ->
            val isCap = m.value.uppercase(Locale.ROOT).contains("-> CAP")
            parseRules.add(
                CustomParseRule(
                    name = m.groupValues[2].trim(),
                    mode = ParseRuleMode.REGEX,
                    expression = unescapeJsonString(m.groupValues[1]),
                    showInOutput = isCap
                )
            )
        }

        val checkpoints = mutableListOf<CustomCheckpointRule>()
        val keychainBlocks = Regex(
            """KEYCHAIN\s+(Success|Failure|Ban|Retry|Custom)\s+(OR|AND)(.*?)(?=KEYCHAIN|\z)""",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
        ).findAll(snippet)
        for (kc in keychainBlocks) {
            val kind = kc.groupValues[1].lowercase(Locale.ROOT)
            val logic = kc.groupValues[2].uppercase(Locale.ROOT)
            val blockBody = kc.groupValues[3]
            val bucket = when (kind) {
                "success" -> ResultBucket.LIVE
                "failure" -> ResultBucket.DIE
                else -> ResultBucket.UNKNOWN
            }
            val keys = Regex("""KEY\s+"((?:\\.|[^"\\])*)"""", RegexOption.IGNORE_CASE)
                .findAll(blockBody)
                .map { unescapeJsonString(it.groupValues[1]).trim() }
                .filter { it.isNotEmpty() }
                .toList()
            if (keys.isNotEmpty()) {
                val cond = when {
                    logic == "AND" && keys.size > 1 -> CheckpointCondition.BODY_CONTAINS_ALL
                    keys.size > 1 -> CheckpointCondition.BODY_CONTAINS_ANY
                    else -> CheckpointCondition.BODY_CONTAINS
                }
                checkpoints.add(
                    CustomCheckpointRule(
                        verdict = bucket,
                        condition = cond,
                        expectedValue = keys.joinToString(","),
                        label = "Loli ${kind.replaceFirstChar { it.uppercase() }}"
                    )
                )
            }
        }

        val hostLabel = Regex("""https?://([^/:?#]+)""", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1).orEmpty().ifEmpty { "LoliScript" }

        val usesCombo = url.contains("{user}") || url.contains("{email}") || url.contains("{pass}") ||
            body.contains("{user}") || body.contains("{email}") || body.contains("{pass}")

        return CustomHttpConfig(
            profileName = "$hostLabel Loli Config",
            url = url,
            method = method,
            headersText = headers.joinToString("\n"),
            bodyTemplate = body,
            userAgent = userAgent,
            followRedirects = true,
            parseRulesSpec = serializeParseRules(parseRules),
            checkpointRulesSpec = serializeCheckpointRules(checkpoints),
            skipLocalLuhnPrecheck = usesCombo,
            blocksSpec = serializeBlocks(importedBlocks)
        )
    }

    private fun parseFetchSnippet(snippet: String): RawParsedSnippet {
        val urlMatch = Regex("""fetch\s*\(\s*["'`](https?://[^"'`\s]+)["'`]""", RegexOption.IGNORE_CASE)
            .find(snippet)
            ?: throw IllegalArgumentException("Could not find URL inside fetch(\"...\").")
        val url = urlMatch.groupValues[1].trim()

        val methodMatch = Regex(""""?method"?\s*:\s*["'`]([A-Za-z]+)["'`]""", RegexOption.IGNORE_CASE)
            .find(snippet)
        val method = methodMatch?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT) ?: ""

        val headers = mutableListOf<Pair<String, String>>()
        val headersKeyMatch = Regex(""""?headers"?\s*:\s*\{""", RegexOption.IGNORE_CASE).find(snippet)
        if (headersKeyMatch != null) {
            val block = extractBalancedGroup(snippet, headersKeyMatch.range.last, '{', '}')
            if (block != null) {
                val kvRegex = Regex("""["'`]([^"'`]+)["'`]\s*:\s*["'`]([^"'`]*)["'`]""")
                kvRegex.findAll(block).forEach { m ->
                    headers.add(m.groupValues[1].trim() to m.groupValues[2].trim())
                }
            }
        }

        var rawBody = ""
        val stringifyMatch = Regex(""""?body"?\s*:\s*JSON\.stringify\s*\(\s*\{""", RegexOption.IGNORE_CASE).find(snippet)
        if (stringifyMatch != null) {
            rawBody = extractBalancedGroup(snippet, stringifyMatch.range.last, '{', '}').orEmpty()
        } else {
            val bodyMatch = Regex(""""?body"?\s*:\s*"((?:\\.|[^"\\])*)"""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(snippet)
                ?: Regex(""""?body"?\s*:\s*'((?:\\.|[^'\\])*)'""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(snippet)
                ?: Regex(""""?body"?\s*:\s*`((?:\\.|[^`\\])*)`""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                    .find(snippet)
            rawBody = bodyMatch?.groupValues?.getOrNull(1)?.let { unescapeJsonString(it) }.orEmpty()
        }

        return RawParsedSnippet(
            url = url,
            explicitMethod = method,
            headers = headers,
            body = rawBody
        )
    }

    private fun extractBalancedGroup(
        source: String,
        openIndex: Int,
        openChar: Char,
        closeChar: Char
    ): String? {
        if (openIndex !in source.indices || source[openIndex] != openChar) return null
        var depth = 0
        var inQuote: Char? = null
        var escaped = false
        for (i in openIndex until source.length) {
            val ch = source[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (inQuote != null) {
                if (ch == '\\' && inQuote != '`') {
                    escaped = true
                } else if (ch == inQuote) {
                    inQuote = null
                }
                continue
            }
            when (ch) {
                '"', '\'', '`' -> inQuote = ch
                openChar -> depth++
                closeChar -> {
                    depth--
                    if (depth == 0) {
                        return source.substring(openIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseCurlSnippet(snippet: String): RawParsedSnippet {
        // Normalize bash backslash-newline, Windows cmd ^-newline, and PowerShell `-newline continuations
        val cleaned = snippet
            .replace(Regex("""\\\r?\n"""), " ")
            .replace(Regex("""\^\r?\n"""), " ")
            .replace(Regex("""`\r?\n"""), " ")
            .replace("$'", "'")
        val tokens = tokenizeShellArgs(cleaned)

        var url = ""
        var explicitMethod = ""
        var compressGzip = false
        val headers = mutableListOf<Pair<String, String>>()
        val bodyParts = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            when {
                (tok.equals("curl", ignoreCase = true) || tok.equals("curl.exe", ignoreCase = true)) && i == 0 -> Unit
                (tok == "-X" || tok == "--request") && i + 1 < tokens.size -> {
                    explicitMethod = tokens[++i].uppercase(Locale.ROOT)
                }
                (tok == "-H" || tok == "--header") && i + 1 < tokens.size -> {
                    val h = tokens[++i]
                    val idx = h.indexOf(':')
                    if (idx > 0) {
                        headers.add(h.substring(0, idx).trim() to h.substring(idx + 1).trim())
                    }
                }
                (tok == "-A" || tok == "--user-agent") && i + 1 < tokens.size -> {
                    headers.add("User-Agent" to tokens[++i])
                }
                (tok == "-b" || tok == "--cookie") && i + 1 < tokens.size -> {
                    headers.add("Cookie" to tokens[++i])
                }
                (tok == "-e" || tok == "--referer") && i + 1 < tokens.size -> {
                    headers.add("Referer" to tokens[++i])
                }
                (tok == "-u" || tok == "--user") && i + 1 < tokens.size -> {
                    val creds = tokens[++i]
                    headers.add("Authorization" to "Basic ${encodeBase64(creds)}")
                }
                (tok == "-d" || tok == "--data" || tok == "--data-raw" || tok == "--data-binary" || tok == "--data-urlencode") && i + 1 < tokens.size -> {
                    bodyParts.add(tokens[++i])
                }
                tok.startsWith("--data-raw=") -> bodyParts.add(tok.substringAfter('='))
                tok.startsWith("--data=") -> bodyParts.add(tok.substringAfter('='))
                tok.startsWith("--data-binary=") -> bodyParts.add(tok.substringAfter('='))
                tok == "--compressed" -> {
                    compressGzip = false // --compressed in curl requests Accept-Encoding, handled natively by OkHttp
                }
                (tok.startsWith("http://", ignoreCase = true) || tok.startsWith("https://", ignoreCase = true)) && url.isEmpty() -> {
                    url = tok
                }
                tok == "--url" && i + 1 < tokens.size -> {
                    url = tokens[++i]
                }
            }
            i++
        }

        return RawParsedSnippet(
            url = url,
            explicitMethod = explicitMethod,
            headers = headers,
            body = bodyParts.joinToString("&"),
            compressGzip = compressGzip
        )
    }

    private fun tokenizeShellArgs(cmd: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaped = false
        var hasToken = false

        for (ch in cmd) {
            if (escaped) {
                current.append(ch)
                escaped = false
                hasToken = true
                continue
            }
            if (ch == '\\' && !inSingle) {
                escaped = true
                continue
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                hasToken = true
                continue
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                hasToken = true
                continue
            }
            if (ch.isWhitespace() && !inSingle && !inDouble) {
                if (hasToken) {
                    result.add(current.toString())
                    current.setLength(0)
                    hasToken = false
                }
                continue
            }
            current.append(ch)
            hasToken = true
        }
        if (hasToken) {
            result.add(current.toString())
        }
        return result
    }

    internal fun autoTokenizePayload(rawBody: String): String {
        var out = rawBody
        // Replace Form card[number]=... / number=... / pan=... (13-19 digits with optional spaces/+)
        out = out.replace(
            Regex("""((?:card\[number\]|card_number|cardNumber|number|pan)\s*=\s*)([0-9+\s-]{13,25})""", RegexOption.IGNORE_CASE),
            "$1{card}"
        )
        out = out.replace(
            Regex("""((?:card\[cvc\]|card\[cvv\]|security_code|cvc|cvv)\s*=\s*)(\d{3,4})""", RegexOption.IGNORE_CASE),
            "$1{cvv}"
        )
        out = out.replace(
            Regex("""((?:card\[exp_month\]|exp_month|expiry_month|month)\s*=\s*)(\d{1,2})""", RegexOption.IGNORE_CASE),
            "$1{mm}"
        )
        out = out.replace(
            Regex("""((?:card\[exp_year\]|exp_year|expiry_year|year)\s*=\s*)(\d{2,4})""", RegexOption.IGNORE_CASE),
            "$1{yyyy}"
        )
        out = out.replace(
            Regex("""((?:email|user_email|login_email)\s*=\s*)([A-Za-z0-9._%+-]+(?:%40|@)[A-Za-z0-9.-]+\.[A-Za-z]{2,})""", RegexOption.IGNORE_CASE),
            "$1{email}"
        )
        out = out.replace(
            Regex("""((?:password|passwd|pass|pwd)\s*=\s*)([^&\s]{3,48})""", RegexOption.IGNORE_CASE),
            "$1{pass}"
        )
        // Replace JSON "number":"...", "cvv":"...", "exp_month":..., "exp_year":..., "email":"...", "password":"..."
        out = out.replace(
            Regex("""("(?:number|card_number|cardNumber|pan)"\s*:\s*")([0-9\s-]{13,24})(")""", RegexOption.IGNORE_CASE),
            "$1{card}$3"
        )
        out = out.replace(
            Regex("""("(?:cvc|cvv|security_code|securityCode)"\s*:\s*")(\d{3,4})(")""", RegexOption.IGNORE_CASE),
            "$1{cvv}$3"
        )
        out = out.replace(
            Regex("""("(?:exp_month|expiry_month|expirationMonth|month)"\s*:\s*")(\d{1,2})(")""", RegexOption.IGNORE_CASE),
            "$1{mm}$3"
        )
        out = out.replace(
            Regex("""("(?:exp_year|expiry_year|expirationYear|year)"\s*:\s*")(\d{2,4})(")""", RegexOption.IGNORE_CASE),
            "$1{yyyy}$3"
        )
        out = out.replace(
            Regex("""("(?:email|emailAddress|user_email)"\s*:\s*")([A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,})(")""", RegexOption.IGNORE_CASE),
            "$1{email}$3"
        )
        out = out.replace(
            Regex("""("(?:password|pass|passwd)"\s*:\s*")([^"]{3,48})(")""", RegexOption.IGNORE_CASE),
            "$1{pass}$3"
        )
        // Standalone raw 15-16 digit card pattern fallback
        out = out.replace(Regex("""\b(?:4|5|3[47]|6(?:011|5))\d{11,15}\b"""), "{card}")
        return out
    }

    // =========================================================================
    // 5. Dynamic Request Tokens, Hash/Base64 Utilities & Live Request Preview
    // =========================================================================

    fun generateUuidToken(): String = UUID.randomUUID().toString()

    fun generateTimestampSeconds(): String = (System.currentTimeMillis() / 1000L).toString()

    fun generateTimestampMillis(): String = System.currentTimeMillis().toString()

    fun generateIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun generateRandomDigits(count: Int = 6): String =
        buildString(count) {
            repeat(count.coerceIn(1, 16)) { append(Random.nextInt(0, 10)) }
        }

    fun generateRandomHex(length: Int = 16): String {
        val hex = "0123456789abcdef"
        return buildString(length) {
            repeat(length.coerceIn(1, 64)) { append(hex[Random.nextInt(16)]) }
        }
    }

    fun generateRandomUserAgent(): String =
        ROTATING_USER_AGENTS[Random.nextInt(ROTATING_USER_AGENTS.size)]

    fun formatSpacedPan(pan: String): String {
        val digits = pan.filter { it.isDigit() }
        if (digits.length <= 4) return digits
        return digits.chunked(4).joinToString(" ")
    }

    fun formatDashedPan(pan: String): String {
        val digits = pan.filter { it.isDigit() }
        if (digits.length <= 4) return digits
        return digits.chunked(4).joinToString("-")
    }

    fun encodeBase64(text: String): String {
        val raw = text.toByteArray(Charsets.UTF_8)
        return runCatching { Base64.getEncoder().encodeToString(raw) }
            .getOrElse { encodeBase64Fallback(raw) }
    }

    fun sha256Hex(text: String): String = digestHex("SHA-256", text)

    fun md5Hex(text: String): String = digestHex("MD5", text)

    private fun digestHex(algorithm: String, text: String): String = runCatching {
        val bytes = MessageDigest.getInstance(algorithm).digest(text.toByteArray(Charsets.UTF_8))
        buildString(bytes.size * 2) {
            for (b in bytes) {
                append(String.format(Locale.US, "%02x", b.toInt() and 0xFF))
            }
        }
    }.getOrDefault("")

    /**
     * Detects whether a sample input line is a Credential Combo (`email:pass` / `user:pass`)
     * vs a standard 4-field Card record (`PAN|MM|YYYY|CVV`).
     */
    fun isComboInputLine(rawLine: String, config: CustomHttpConfig? = null): Boolean {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return false
        val sepIndex = trimmed.indexOfFirst { it == ':' || it == '|' || it == ';' }
        if (sepIndex <= 0 || sepIndex >= trimmed.lastIndex) return false
        val firstToken = trimmed.substring(0, sepIndex).trim()
        val hasFourCardFields = trimmed.split('|', '/', ':', ';').count { it.isNotBlank() } >= 4 &&
            firstToken.none { it == '@' || it.isLetter() }
        if (hasFourCardFields) return false

        if (firstToken.contains('@') || firstToken.any { it.isLetter() }) {
            return true
        }
        if (config != null) {
            val combinedTemplates = "${config.url} ${config.headersText} ${config.bodyTemplate}"
            if (combinedTemplates.contains("{email}", ignoreCase = true) ||
                combinedTemplates.contains("{user}", ignoreCase = true) ||
                combinedTemplates.contains("{pass}", ignoreCase = true) ||
                combinedTemplates.contains("{base64_combo}", ignoreCase = true)
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Renders a live, deterministic preview of the HTTP request for the given [config] and [sampleLine]
     * so users can verify token substitution and JSON syntax before running a batch.
     */
    fun renderRequestPreview(
        config: CustomHttpConfig,
        sampleLine: String
    ): RenderedRequestPreview {
        val cleanLine = sampleLine.trim().ifEmpty { "4532250055360003|08|2028|113" }
        val isCombo = isComboInputLine(cleanLine, config)
        val parts = cleanLine.split('|', ':', '/')
        val rawPan = parts.getOrElse(0) { "4532250055360003" }.trim()
        val pan = if (!isCombo && rawPan.any { it.equals('x', ignoreCase = true) }) {
            CardValidatorEngine.resolveWildcardPan(rawPan)
        } else {
            rawPan
        }
        val mm = parts.getOrElse(1) { "08" }.trim().let { if (!isCombo) it.padStart(2, '0') else it }
        val yyyy = parts.getOrElse(2) { "2028" }.trim().let { if (!isCombo) CardValidatorEngine.normalizeYear(it) else it }
        val cvv = parts.getOrElse(3) { "113" }.trim()
        val scheme = CardValidatorEngine.detectScheme(pan)

        val step1PreviewCaptures = config.parsedPreRequestParseRules().associate { rule ->
            rule.name to (rule.defaultValue.ifBlank { "step1_${rule.name}_val" })
        }

        val built = CustomHttpClient.buildRequest(
            normalizedLine = if (isCombo) cleanLine else "$pan|$mm|$yyyy|$cvv",
            pan = pan,
            mm = mm,
            yyyy = yyyy,
            cvv = cvv,
            scheme = scheme,
            config = config,
            step1Captures = step1PreviewCaptures
        )

        val resolvedMethod = built?.method ?: when (config.method) {
            HttpRequestMethod.GET -> "GET"
            else -> "POST"
        }
        val resolvedUrl = built?.url?.toString() ?: config.url.trim()
        val resolvedHeaders = built?.headers?.let { hdrs ->
            (0 until hdrs.size).map { idx -> hdrs.name(idx) to hdrs.value(idx) }
        } ?: emptyList()

        val resolvedBody = if (resolvedMethod == "GET") {
            ""
        } else {
            CustomHttpClient.renderPreviewBody(
                normalizedLine = if (isCombo) cleanLine else "$pan|$mm|$yyyy|$cvv",
                pan = pan,
                mm = mm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                config = config,
                step1Captures = step1PreviewCaptures
            )
        }

        val isJsonMode = config.method == HttpRequestMethod.POST_JSON ||
            (config.method == HttpRequestMethod.AUTO && resolvedMethod == "POST")
        val (jsonOk, jsonDiag) = if (isJsonMode && resolvedBody.isNotBlank()) {
            validateJsonSyntaxQuick(resolvedBody)
        } else {
            null to ""
        }

        val combinedOut = "$resolvedUrl\n$resolvedBody"
        val unresolved = Regex("""\{[A-Za-z0-9_:]+\}""")
            .findAll(combinedOut)
            .map { it.value }
            .distinct()
            .toList()

        return RenderedRequestPreview(
            resolvedMethod = resolvedMethod,
            resolvedUrl = resolvedUrl,
            resolvedHeaders = resolvedHeaders,
            resolvedBody = resolvedBody,
            isJsonValid = jsonOk,
            jsonDiagnostic = jsonDiag,
            detectedInputType = if (isCombo) "COMBO (email:pass)" else "CARD (${scheme.displayName})",
            unresolvedTokens = unresolved
        )
    }

    internal fun validateJsonSyntaxQuick(jsonText: String): Pair<Boolean, String> {
        val t = jsonText.trim()
        if (t.isEmpty()) return false to "Empty JSON body"
        if (!(t.startsWith("{") && t.endsWith("}")) && !(t.startsWith("[") && t.endsWith("]"))) {
            return false to "Must start/end with { } or [ ]"
        }
        var braceDepth = 0
        var bracketDepth = 0
        var inQuotes = false
        var escaped = false
        for (ch in t) {
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\') {
                escaped = true
                continue
            }
            if (ch == '"') {
                inQuotes = !inQuotes
                continue
            }
            if (!inQuotes) {
                when (ch) {
                    '{' -> braceDepth++
                    '}' -> {
                        braceDepth--
                        if (braceDepth < 0) return false to "Unexpected closing '}'"
                    }
                    '[' -> bracketDepth++
                    ']' -> {
                        bracketDepth--
                        if (bracketDepth < 0) return false to "Unexpected closing ']'"
                    }
                }
            }
        }
        if (inQuotes) return false to "Unclosed string quote (\")"
        if (braceDepth != 0) return false to "Unbalanced braces ({ })"
        if (bracketDepth != 0) return false to "Unbalanced brackets ([ ])"
        return true to "Valid JSON"
    }

    // =========================================================================
    // 6. Export to cURL, Export to LoliScript & JSON Config Import / Export
    // =========================================================================

    fun exportToCurlCommand(
        config: CustomHttpConfig,
        sampleLine: String = "4532250055360003|08|2028|113"
    ): String {
        val preview = renderRequestPreview(config, sampleLine)
        return buildString(256) {
            append("curl -X ").append(preview.resolvedMethod)
            append(" '").append(preview.resolvedUrl.replace("'", "'\\''")).append("'")
            for ((k, v) in preview.resolvedHeaders) {
                append(" \\\n  -H '").append(k).append(": ").append(v.replace("'", "'\\''")).append("'")
            }
            if (preview.resolvedBody.isNotBlank()) {
                append(" \\\n  --data-raw '").append(preview.resolvedBody.replace("'", "'\\''")).append("'")
            }
        }
    }

    fun exportToLoliScript(config: CustomHttpConfig): String {
        fun toLoliTokens(text: String): String = text
            .replace("{card}", "<CC>", ignoreCase = true)
            .replace("{pan}", "<CC>", ignoreCase = true)
            .replace("{mm}", "<MM>", ignoreCase = true)
            .replace("{yy}", "<YY>", ignoreCase = true)
            .replace("{yyyy}", "<YYYY>", ignoreCase = true)
            .replace("{cvv}", "<CVV>", ignoreCase = true)
            .replace("{email}", "<EMAIL>", ignoreCase = true)
            .replace("{user}", "<USER>", ignoreCase = true)
            .replace("{pass}", "<PASS>", ignoreCase = true)
            .replace("{bin}", "<BIN>", ignoreCase = true)
            .replace("{lista}", "<LISTA>", ignoreCase = true)
            .replace(Regex("""\{cap:([A-Za-z0-9_]+)\}""", RegexOption.IGNORE_CASE), "<$1>")

        val methodStr = if (config.method == HttpRequestMethod.GET) "GET" else "POST"
        val ctype = when (config.method) {
            HttpRequestMethod.POST_FORM -> "application/x-www-form-urlencoded"
            else -> "application/json"
        }

        return buildString(512) {
            append("# Profile: ").append(config.profileName).append('\n')
            if (config.preRequestEnabled && config.preRequestUrl.isNotBlank()) {
                val preMethod = if (config.preRequestMethod == HttpRequestMethod.GET) "GET" else "POST"
                append("REQUEST ").append(preMethod).append(" \"").append(toLoliTokens(config.preRequestUrl)).append("\"\n")
                if (config.preRequestBody.isNotBlank()) {
                    append("  CONTENT \"").append(escapeJson(toLoliTokens(config.preRequestBody))).append("\"\n")
                    append("  CONTENTTYPE \"application/x-www-form-urlencoded\"\n")
                }
                for (rule in config.parsedPreRequestParseRules()) {
                    when (rule.mode) {
                        ParseRuleMode.LR -> append("PARSE \"<SOURCE>\" LR \"${escapeJson(rule.leftDelimiter)}\" \"${escapeJson(rule.rightDelimiter)}\" -> VAR \"${rule.name}\"\n")
                        ParseRuleMode.JSON -> append("PARSE \"<SOURCE>\" JSON \"${escapeJson(rule.expression)}\" -> VAR \"${rule.name}\"\n")
                        ParseRuleMode.REGEX -> append("PARSE \"<SOURCE>\" REGEX \"${escapeJson(rule.expression)}\" -> VAR \"${rule.name}\"\n")
                        else -> Unit
                    }
                }
                append('\n')
            }

            append("REQUEST ").append(methodStr).append(" \"").append(toLoliTokens(config.url)).append("\"\n")
            if (config.bodyTemplate.isNotBlank() && methodStr != "GET") {
                append("  CONTENT \"").append(escapeJson(toLoliTokens(config.bodyTemplate))).append("\"\n")
                append("  CONTENTTYPE \"").append(ctype).append("\"\n")
            }
            if (config.userAgent.isNotBlank()) {
                append("  HEADER \"User-Agent: ").append(escapeJson(config.userAgent)).append("\"\n")
            }
            config.headersText.lines().map { it.trim() }.filter { it.contains(':') && !it.startsWith("#") }.forEach { hdr ->
                append("  HEADER \"").append(escapeJson(toLoliTokens(hdr))).append("\"\n")
            }

            val parseRules = config.parsedParseRules()
            if (parseRules.isNotEmpty()) {
                append('\n')
                for (rule in parseRules) {
                    val outKind = if (rule.showInOutput) "CAP" else "VAR"
                    when (rule.mode) {
                        ParseRuleMode.LR -> append("PARSE \"<SOURCE>\" LR \"${escapeJson(rule.leftDelimiter)}\" \"${escapeJson(rule.rightDelimiter)}\" -> $outKind \"${rule.name}\"\n")
                        ParseRuleMode.JSON -> append("PARSE \"<SOURCE>\" JSON \"${escapeJson(rule.expression)}\" -> $outKind \"${rule.name}\"\n")
                        ParseRuleMode.REGEX -> append("PARSE \"<SOURCE>\" REGEX \"${escapeJson(rule.expression)}\" -> $outKind \"${rule.name}\"\n")
                        else -> append("PARSE \"<HEADERS>\" JSON \"${escapeJson(rule.expression)}\" -> $outKind \"${rule.name}\"\n")
                    }
                }
            }

            val checkpoints = config.parsedCheckpointRules()
            if (checkpoints.isNotEmpty()) {
                append("\nKEYCHECK\n")
                for (cp in checkpoints) {
                    val chainType = when (cp.verdict) {
                        ResultBucket.LIVE -> "Success"
                        ResultBucket.DIE -> "Failure"
                        ResultBucket.UNKNOWN -> "Retry"
                    }
                    val op = if (cp.condition == CheckpointCondition.BODY_CONTAINS_ALL) "AND" else "OR"
                    append("  KEYCHAIN ").append(chainType).append(' ').append(op).append('\n')
                    val terms = if (cp.condition == CheckpointCondition.BODY_CONTAINS_ANY || cp.condition == CheckpointCondition.BODY_CONTAINS_ALL) {
                        cp.expectedValue.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    } else {
                        listOf(cp.expectedValue)
                    }
                    for (t in terms) {
                        append("    KEY \"").append(escapeJson(t)).append("\"\n")
                    }
                }
            }

            val blocks = config.parsedBlocks()
            if (blocks.isNotEmpty()) {
                append("\n# --- Config Maker Blocks (${blocks.size}) ---\n")
                for (blk in blocks) {
                    if (!blk.enabled) append("# [DISABLED] ")
                    val outKind = if (blk.captureToOutput) "CAP" else "VAR"
                    val outVar = blk.outputVar.ifBlank { "out" }
                    when (blk.type) {
                        BlockType.BROWSER_OPEN -> append("BROWSERACTION Open\n")
                        BlockType.BROWSER_NAVIGATE -> append("BROWSERACTION Navigate \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                        BlockType.BROWSER_CLICK -> append("BROWSERACTION Click \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.BROWSER_TYPE -> append("BROWSERACTION SendKeys \"${escapeJson(blk.inputTarget)}\" \"${escapeJson(toLoliTokens(blk.secondaryParam))}\"\n")
                        BlockType.BROWSER_WAIT_ELEMENT -> append("BROWSERACTION WaitForElement \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.BROWSER_EXEC_JS -> append("BROWSERACTION ExecuteJS \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.BROWSER_GET_COOKIES -> append("BROWSERACTION GetCookies \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.BROWSER_SCREENSHOT -> append("BROWSERACTION Screenshot \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.BROWSER_CLOSE -> append("BROWSERACTION Close\n")
                        BlockType.CAPTCHA_RECAPTCHA_V2,
                        BlockType.CAPTCHA_RECAPTCHA_V3,
                        BlockType.CAPTCHA_HCAPTCHA,
                        BlockType.CAPTCHA_TURNSTILE,
                        BlockType.CAPTCHA_IMAGE,
                        BlockType.CAPTCHA_FUNCAPTCHA,
                        BlockType.CAPTCHA_BALANCE -> append("SOLVECAPTCHA ${blk.type.name} \"${escapeJson(toLoliTokens(blk.inputTarget))}\" \"${escapeJson(blk.secondaryParam)}\" -> $outKind \"$outVar\"\n")
                        BlockType.CONDITION_KEYCHECK -> append("KEYCHECK\n  KEYCHAIN ${if (blk.verdictOnMatch == ResultBucket.LIVE) "Success" else "Failure"} OR\n    KEY \"${escapeJson(toLoliTokens(blk.tertiaryParam))}\"\n")
                        BlockType.CONDITION_IF_STRING,
                        BlockType.CONDITION_IF_NUMBER,
                        BlockType.CONDITION_IF_STATUS,
                        BlockType.CONDITION_IF_EXISTS -> append("IF \"${escapeJson(toLoliTokens(blk.inputTarget))}\" ${blk.secondaryParam.ifBlank { "EQUALS" }} \"${escapeJson(toLoliTokens(blk.tertiaryParam))}\"\n")
                        BlockType.FUNC_HASH -> append("FUNCTION Hash ${blk.secondaryParam.ifBlank { "SHA-256" }} \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_HMAC -> append("FUNCTION HMAC \"${escapeJson(toLoliTokens(blk.inputTarget))}\" \"${escapeJson(blk.secondaryParam)}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_BASE64_ENCODE -> append("FUNCTION Base64Encode \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_BASE64_DECODE -> append("FUNCTION Base64Decode \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_URL_ENCODE -> append("FUNCTION URLEncode \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_RANDOM_STRING -> append("FUNCTION RandomString \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_TIMESTAMP -> append("FUNCTION CurrentUnixTime \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_STRING_REPLACE -> append("FUNCTION Replace \"${escapeJson(blk.secondaryParam)}\" \"${escapeJson(blk.tertiaryParam)}\" \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_STRING_CASE -> append("FUNCTION ${blk.secondaryParam.ifBlank { "Lowercase" }} \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_MATH_EVAL -> append("FUNCTION Compute \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.FUNC_CONSTANT -> append("FUNCTION Constant \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.INTEROP_SHELL -> append("SHELL \"${escapeJson(toLoliTokens(blk.inputTarget))}\" \"${escapeJson(toLoliTokens(blk.secondaryParam))}\" -> $outKind \"$outVar\"\n")
                        BlockType.INTEROP_SCRIPT,
                        BlockType.INTEROP_WEBHOOK_POST,
                        BlockType.INTEROP_TCP_SOCKET,
                        BlockType.INTEROP_INTENT_EXPORT -> append("INTEROP ${blk.type.name} \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.PARSE_LR -> append("PARSE \"<SOURCE>\" LR \"${escapeJson(blk.inputTarget)}\" \"${escapeJson(blk.secondaryParam)}\" -> $outKind \"$outVar\"\n")
                        BlockType.PARSE_JSON -> append("PARSE \"<SOURCE>\" JSON \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.PARSE_REGEX -> append("PARSE \"<SOURCE>\" REGEX \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.PARSE_CSS -> append("PARSE \"<SOURCE>\" CSS \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.PARSE_HEADER,
                        BlockType.PARSE_COOKIE -> append("PARSE \"<HEADERS>\" LR \"${escapeJson(blk.inputTarget)}\" \"\" -> $outKind \"$outVar\"\n")
                        BlockType.REQUEST_HTTP -> append("REQUEST ${blk.secondaryParam.ifBlank { "GET" }} \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                        BlockType.REQUEST_SET_COOKIE -> append("COOKIE \"${escapeJson(blk.inputTarget)}=${escapeJson(toLoliTokens(blk.secondaryParam))}\"\n")
                        BlockType.REQUEST_CLEAR_COOKIES -> append("UTILITY ClearCookies\n")
                        BlockType.REQUEST_MULTIPART -> append("REQUEST POST \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                        BlockType.SELENIUM_OPEN -> append("SELENIUM Open \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.SELENIUM_NAVIGATE -> append("SELENIUM Navigate \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                        BlockType.SELENIUM_FIND_ELEMENT -> append("ELEMENTACTION Find \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.SELENIUM_SEND_KEYS -> append("ELEMENTACTION SendKeys \"${escapeJson(blk.inputTarget)}\" \"${escapeJson(toLoliTokens(blk.secondaryParam))}\"\n")
                        BlockType.SELENIUM_CLICK -> append("ELEMENTACTION Click \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.SELENIUM_WAIT -> append("ELEMENTACTION Wait \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.SELENIUM_EXEC_SCRIPT -> append("SELENIUM ExecuteJS \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.SELENIUM_GET_COOKIES -> append("SELENIUM GetCookies \"${escapeJson(blk.inputTarget)}\" -> $outKind \"$outVar\"\n")
                        BlockType.SELENIUM_QUIT -> append("SELENIUM Quit\n")
                        BlockType.UTIL_DELAY -> append("DELAY \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.UTIL_LOG -> append("LOG \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                        BlockType.UTIL_SET_VAR -> append("UTILITY SetVar \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.UTIL_CLEAR_VAR -> append("UTILITY ClearVar \"${escapeJson(blk.inputTarget)}\"\n")
                        BlockType.UTIL_LIST_OP -> append("UTILITY List ${blk.secondaryParam.ifBlank { "COUNT" }} \"${escapeJson(toLoliTokens(blk.inputTarget))}\" -> $outKind \"$outVar\"\n")
                        BlockType.UTIL_FILE_APPEND -> append("UTILITY FileAppend \"${escapeJson(toLoliTokens(blk.inputTarget))}\"\n")
                    }
                }
            }
        }
    }

    fun exportConfigToJson(config: CustomHttpConfig): String {
        val parseRules = parseParseRules(config.parseRulesSpec)
        val checkpoints = parseCheckpointRules(config.checkpointRulesSpec)
        val blocks = parseBlocks(config.blocksSpec)

        return buildString(640) {
            append("{\n")
            append("  \"format\": \"bincheck_custom_config_v2\",\n")
            append("  \"profileName\": \"${escapeJson(config.profileName)}\",\n")
            append("  \"url\": \"${escapeJson(config.url)}\",\n")
            append("  \"method\": \"${escapeJson(config.method.name)}\",\n")
            append("  \"headersText\": \"${escapeJson(config.headersText)}\",\n")
            append("  \"bodyTemplate\": \"${escapeJson(config.bodyTemplate)}\",\n")
            append("  \"userAgent\": \"${escapeJson(config.userAgent)}\",\n")
            append("  \"followRedirects\": ${config.followRedirects},\n")
            append("  \"compressBodyGzip\": ${config.compressBodyGzip},\n")
            append("  \"timeoutSeconds\": ${config.timeoutSeconds},\n")
            append("  \"maxRetries\": ${config.maxRetries},\n")
            append("  \"liveKeywords\": \"${escapeJson(config.liveKeywords)}\",\n")
            append("  \"dieKeywords\": \"${escapeJson(config.dieKeywords)}\",\n")
            append("  \"proxyEnabled\": ${config.proxyEnabled},\n")
            append("  \"proxyProtocol\": \"${escapeJson(config.proxyProtocol.name)}\",\n")
            append("  \"proxyRotation\": \"${escapeJson(config.proxyRotation.name)}\",\n")
            append("  \"proxyListText\": \"${escapeJson(config.proxyListText)}\",\n")
            append("  \"parseRulesSpec\": \"${escapeJson(config.parseRulesSpec)}\",\n")
            append("  \"checkpointRulesSpec\": \"${escapeJson(config.checkpointRulesSpec)}\",\n")
            append("  \"preRequestEnabled\": ${config.preRequestEnabled},\n")
            append("  \"preRequestUrl\": \"${escapeJson(config.preRequestUrl)}\",\n")
            append("  \"preRequestMethod\": \"${escapeJson(config.preRequestMethod.name)}\",\n")
            append("  \"preRequestHeaders\": \"${escapeJson(config.preRequestHeaders)}\",\n")
            append("  \"preRequestBody\": \"${escapeJson(config.preRequestBody)}\",\n")
            append("  \"preRequestParseRulesSpec\": \"${escapeJson(config.preRequestParseRulesSpec)}\",\n")
            append("  \"skipLocalLuhnPrecheck\": ${config.skipLocalLuhnPrecheck},\n")
            append("  \"blocksSpec\": \"${escapeJson(config.blocksSpec)}\",\n")
            append("  \"parseRulesCount\": ${parseRules.size},\n")
            append("  \"checkpointsCount\": ${checkpoints.size},\n")
            append("  \"blocksCount\": ${blocks.size}\n")
            append("}")
        }
    }

    fun importConfigFromJson(rawJson: String): Result<CustomHttpConfig> = runCatching {
        val trimmed = rawJson.trim()
        require(trimmed.startsWith("{") && trimmed.endsWith("}")) {
            "Invalid JSON config format (must be a JSON object)."
        }
        val url = extractJsonPathValue(trimmed, "url").orEmpty().trim()
        val profileName = extractJsonPathValue(trimmed, "profileName")
            ?.ifBlank { null }
            ?: "Imported Config"
        val methodStr = extractJsonPathValue(trimmed, "method").orEmpty().uppercase(Locale.ROOT)
        val method = runCatching { HttpRequestMethod.valueOf(methodStr) }
            .getOrDefault(HttpRequestMethod.AUTO)
        val headersText = extractJsonPathValue(trimmed, "headersText").orEmpty()
        val bodyTemplate = extractJsonPathValue(trimmed, "bodyTemplate").orEmpty()
        val userAgent = extractJsonPathValue(trimmed, "userAgent")
            ?: CustomHttpConfig.DEFAULT_USER_AGENT
        val followRedirects = extractJsonPathValue(trimmed, "followRedirects")
            ?.toBooleanStrictOrNull() ?: true
        val compressBodyGzip = extractJsonPathValue(trimmed, "compressBodyGzip")
            ?.toBooleanStrictOrNull() ?: false
        val timeoutSeconds = extractJsonPathValue(trimmed, "timeoutSeconds")
            ?.toIntOrNull()?.coerceIn(5, 60) ?: 12
        val maxRetries = extractJsonPathValue(trimmed, "maxRetries")
            ?.toIntOrNull()?.coerceIn(0, 3) ?: 1
        val liveKeywords = extractJsonPathValue(trimmed, "liveKeywords")
            ?: CustomHttpConfig.DEFAULT_LIVE_KEYWORDS
        val dieKeywords = extractJsonPathValue(trimmed, "dieKeywords")
            ?: CustomHttpConfig.DEFAULT_DIE_KEYWORDS
        val proxyEnabled = extractJsonPathValue(trimmed, "proxyEnabled")
            ?.toBooleanStrictOrNull() ?: false
        val proxyProtocol = runCatching {
            ProxyProtocol.valueOf(extractJsonPathValue(trimmed, "proxyProtocol").orEmpty().uppercase(Locale.ROOT))
        }.getOrDefault(ProxyProtocol.HTTP)
        val proxyRotation = runCatching {
            ProxyRotationMode.valueOf(extractJsonPathValue(trimmed, "proxyRotation").orEmpty().uppercase(Locale.ROOT))
        }.getOrDefault(ProxyRotationMode.ROUND_ROBIN)
        val proxyListText = extractJsonPathValue(trimmed, "proxyListText").orEmpty()
        val parseRulesSpec = extractJsonPathValue(trimmed, "parseRulesSpec").orEmpty()
        val checkpointRulesSpec = extractJsonPathValue(trimmed, "checkpointRulesSpec").orEmpty()
        val preRequestEnabled = extractJsonPathValue(trimmed, "preRequestEnabled")
            ?.toBooleanStrictOrNull() ?: false
        val preRequestUrl = extractJsonPathValue(trimmed, "preRequestUrl").orEmpty().trim()
        val preRequestMethodStr = extractJsonPathValue(trimmed, "preRequestMethod").orEmpty().uppercase(Locale.ROOT)
        val preRequestMethod = runCatching { HttpRequestMethod.valueOf(preRequestMethodStr) }
            .getOrDefault(HttpRequestMethod.POST_FORM)
        val preRequestHeaders = extractJsonPathValue(trimmed, "preRequestHeaders").orEmpty()
        val preRequestBody = extractJsonPathValue(trimmed, "preRequestBody").orEmpty()
        val preRequestParseRulesSpec = extractJsonPathValue(trimmed, "preRequestParseRulesSpec").orEmpty()
        val skipLocalLuhnPrecheck = extractJsonPathValue(trimmed, "skipLocalLuhnPrecheck")
            ?.toBooleanStrictOrNull() ?: false
        val blocksSpec = extractJsonPathValue(trimmed, "blocksSpec").orEmpty()

        CustomHttpConfig(
            profileName = profileName,
            url = url,
            method = method,
            headersText = headersText,
            bodyTemplate = bodyTemplate,
            timeoutSeconds = timeoutSeconds,
            maxRetries = maxRetries,
            liveKeywords = liveKeywords,
            dieKeywords = dieKeywords,
            compressBodyGzip = compressBodyGzip,
            userAgent = userAgent,
            followRedirects = followRedirects,
            parseRulesSpec = parseRulesSpec,
            checkpointRulesSpec = checkpointRulesSpec,
            proxyEnabled = proxyEnabled,
            proxyProtocol = proxyProtocol,
            proxyRotation = proxyRotation,
            proxyListText = proxyListText,
            preRequestEnabled = preRequestEnabled,
            preRequestUrl = preRequestUrl,
            preRequestMethod = preRequestMethod,
            preRequestHeaders = preRequestHeaders,
            preRequestBody = preRequestBody,
            preRequestParseRulesSpec = preRequestParseRulesSpec,
            skipLocalLuhnPrecheck = skipLocalLuhnPrecheck,
            blocksSpec = blocksSpec
        )
    }

    private const val PROFILE_ENTRY_SEP = "\u001E" // ASCII Record Separator

    fun serializeSavedProfiles(profiles: List<CustomConfigProfile>): String {
        if (profiles.isEmpty()) return ""
        return profiles.filter { !it.isBuiltIn }.joinToString(PROFILE_ENTRY_SEP) { profile ->
            val json = exportConfigToJson(profile.config.copy(profileName = profile.name))
            listOf(
                profile.id.replace(FIELD_SEP, ""),
                profile.name.replace(FIELD_SEP, ""),
                profile.description.replace(FIELD_SEP, ""),
                json
            ).joinToString(FIELD_SEP)
        }
    }

    fun parseSavedProfiles(raw: String): List<CustomConfigProfile> {
        if (raw.isBlank()) return emptyList()
        return raw.split(PROFILE_ENTRY_SEP).mapNotNull { entry ->
            if (entry.isBlank()) return@mapNotNull null
            val parts = entry.split(FIELD_SEP, limit = 4)
            if (parts.size < 4) return@mapNotNull null
            val id = parts[0].trim()
            val name = parts[1].trim()
            val desc = parts[2].trim()
            val config = importConfigFromJson(parts[3]).getOrNull() ?: return@mapNotNull null
            CustomConfigProfile(
                id = id.ifEmpty { "custom_${name.hashCode()}" },
                name = name.ifEmpty { config.profileName },
                description = desc,
                config = config.copy(profileName = name.ifEmpty { config.profileName }),
                isBuiltIn = false
            )
        }
    }

    private fun escapeJson(value: String): String {
        val sb = StringBuilder(value.length + 12)
        for (ch in value) {
            when (ch) {
                '\\' -> sb.append("\\\\")
                '"' -> sb.append("\\\"")
                '\b' -> sb.append("\\b")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') {
                    sb.append(String.format(Locale.US, "\\u%04x", ch.code))
                } else {
                    sb.append(ch)
                }
            }
        }
        return sb.toString()
    }

    private fun unescapeJsonString(escaped: String): String {
        if (!escaped.contains('\\')) return escaped
        val sb = StringBuilder(escaped.length)
        var i = 0
        while (i < escaped.length) {
            val ch = escaped[i]
            if (ch == '\\' && i + 1 < escaped.length) {
                when (val next = escaped[i + 1]) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'b' -> { sb.append('\b'); i += 2 }
                    'f' -> { sb.append('\u000C'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < escaped.length) {
                            val hex = escaped.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) {
                                sb.append(code.toChar())
                                i += 6
                            } else {
                                sb.append(ch)
                                i++
                            }
                        } else {
                            sb.append(ch)
                            i++
                        }
                    }
                    else -> {
                        sb.append(next)
                        i += 2
                    }
                }
            } else {
                sb.append(ch)
                i++
            }
        }
        return sb.toString()
    }
}

private fun encodeBase64Fallback(bytes: ByteArray): String {
    val table = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val out = StringBuilder(((bytes.size + 2) / 3) * 4)
    var i = 0
    while (i < bytes.size) {
        val b0 = bytes[i].toInt() and 0xFF
        val b1 = if (i + 1 < bytes.size) bytes[i + 1].toInt() and 0xFF else 0
        val b2 = if (i + 2 < bytes.size) bytes[i + 2].toInt() and 0xFF else 0
        val triple = (b0 shl 16) or (b1 shl 8) or b2
        out.append(table[(triple ushr 18) and 0x3F])
        out.append(table[(triple ushr 12) and 0x3F])
        out.append(if (i + 1 < bytes.size) table[(triple ushr 6) and 0x3F] else '=')
        out.append(if (i + 2 < bytes.size) table[triple and 0x3F] else '=')
        i += 3
    }
    return out.toString()
}
