package com.example.domain

import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * Extraction mode for Custom Config Studio response parsing (Capture Rules).
 */
enum class ParseRuleMode(val label: String, val hint: String) {
    JSON("JSON Key", "e.g. error.code or data.status"),
    LR("Left / Right", "Extract text between Left & Right delimiters"),
    REGEX("Regex Group", "e.g. \"code\":\"([^\"]+)\""),
    HEADER("HTTP Header", "e.g. x-request-id or cf-ray")
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
    val showInOutput: Boolean = true
)

/**
 * Condition types for Custom Config Studio Checkpoints (Keycheck Rules).
 */
enum class CheckpointCondition(val label: String, val requiresKey: Boolean) {
    BODY_CONTAINS("Body Contains", false),
    BODY_CONTAINS_ANY("Body Contains Any (,)", false),
    BODY_NOT_CONTAINS("Body Not Contains", false),
    STATUS_CODE_EQUALS("HTTP Status ==", false),
    STATUS_CODE_IN("HTTP Status In (,)", false),
    JSON_KEY_EQUALS("JSON Key ==", true),
    CAPTURED_VAR_CONTAINS("Var Contains", true),
    CAPTURED_VAR_EQUALS("Var Equals", true),
    REGEX_MATCH("Regex Matches Body", false)
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
 * - Parse Rule serialization & extraction (JSON dot-path, Left/Right delimiter, Regex, Header)
 * - Checkpoint / Keycheck ordered evaluation (9 condition modes + priority ordering)
 * - Proxy list parsing & rotation (Round-Robin, Random, Sticky)
 * - cURL & JS fetch() DevTools importer with optional card/combo auto-tokenization
 * - Pure-Kotlin JSON Config Import / Export (zero external serialization dependency, JVM-test safe)
 * - Built-in safe Sandbox / Echo config templates
 */
object CustomConfigStudioEngine {

    private const val RULE_LINE_SEP = "\n"
    private const val FIELD_SEP = "\u001F" // ASCII Unit Separator for collision-free field packing
    private const val LEGACY_PIPE_SEP = "::"

    private val roundRobinCounter = AtomicInteger(0)

    val BUILT_IN_PROFILES: List<CustomConfigProfile> by lazy {
        listOf(
            CustomConfigProfile(
                id = "builtin_zero_dollar_sandbox",
                name = "Zero-Dollar ($0.00) Auth JSON",
                description = "POST JSON $0 verification payload with JSON dot-path captures & keycheck",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Zero-Dollar ($0.00) Auth JSON",
                    url = "https://postman-echo.com/post",
                    method = HttpRequestMethod.POST_JSON,
                    headersText = "Accept: application/json\nX-Idempotency-Key: {uuid}",
                    bodyTemplate = CustomHttpConfig.ZERO_DOLLAR_AUTH_JSON_PAYLOAD,
                    timeoutSeconds = 12,
                    maxRetries = 1,
                    liveKeywords = "succeeded,approved,verified,valid,true",
                    dieKeywords = "declined,invalid,expired,do_not_honor",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "auth_type",
                                mode = ParseRuleMode.JSON,
                                expression = "json.auth_type",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "scheme",
                                mode = ParseRuleMode.JSON,
                                expression = "json.card.scheme",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.JSON_KEY_EQUALS,
                                targetKey = "json.auth_type",
                                expectedValue = "zero_dollar",
                                label = "Echo Verified $0"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "card_declined,incorrect_cvc,expired_card",
                                label = "Gateway Decline"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_form_gateway_capture",
                name = "Form Gateway + LR & Origin Capture",
                description = "URL-encoded form POST with Left/Right & JSON captures and HTTP status keycheck",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Form Gateway + LR & Origin Capture",
                    url = "https://httpbin.org/post",
                    method = HttpRequestMethod.POST_FORM,
                    headersText = "Accept: application/json\nAccept-Language: en-US",
                    bodyTemplate = "card[number]={card_spaced}&card[exp_month]={mm_no_pad}&card[exp_year]={yyyy}&card[cvc]={cvv}&guid={uuid}&ts={timestamp}",
                    timeoutSeconds = 15,
                    maxRetries = 1,
                    liveKeywords = "tok_,seti_,pm_,succeeded,form",
                    dieKeywords = "incorrect_number,invalid_expiry,card_declined,expired_card",
                    userAgent = "Mozilla/5.0 (Linux; Android 15) BinCheck/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "origin",
                                mode = ParseRuleMode.JSON,
                                expression = "origin",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "card_echo",
                                mode = ParseRuleMode.LR,
                                leftDelimiter = "\"card[number]\": \"",
                                rightDelimiter = "\"",
                                showInOutput = false
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.BODY_CONTAINS_ANY,
                                expectedValue = "incorrect_number,card_declined,invalid_cvc",
                                label = "Declined"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.STATUS_CODE_IN,
                                expectedValue = "200,201",
                                label = "HTTP 2xx OK"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_get_query_regex",
                name = "GET Query + Regex & Header Capture",
                description = "GET endpoint with {lista} query param, Regex capture group & HTTP Header check",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "GET Query + Regex & Header Capture",
                    url = "https://postman-echo.com/get?lista={lista}&bin={bin}&scheme={scheme}&ts={timestamp}",
                    method = HttpRequestMethod.GET,
                    headersText = "Accept: application/json",
                    bodyTemplate = "",
                    timeoutSeconds = 10,
                    maxRetries = 1,
                    liveKeywords = "args,lista,live,approved",
                    dieKeywords = "die,declined,error",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "bin",
                                mode = ParseRuleMode.REGEX,
                                expression = "\"bin\"\\s*:\\s*\"(\\d{6})\"",
                                showInOutput = true
                            ),
                            CustomParseRule(
                                name = "ctype",
                                mode = ParseRuleMode.HEADER,
                                expression = "content-type",
                                showInOutput = false
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.CAPTURED_VAR_CONTAINS,
                                targetKey = "bin",
                                expectedValue = "",
                                label = "BIN Captured"
                            )
                        )
                    )
                )
            ),
            CustomConfigProfile(
                id = "builtin_combo_auth_json",
                name = "Credential Combo (email:pass) JSON",
                description = "POST JSON login probe using {email}, {user}, {pass}, {uuid} + status assertions",
                isBuiltIn = true,
                config = CustomHttpConfig(
                    profileName = "Credential Combo (email:pass) JSON",
                    url = "https://httpbin.org/anything",
                    method = HttpRequestMethod.POST_JSON,
                    headersText = "Accept: application/json\nX-Trace-ID: {uuid}",
                    bodyTemplate = """{"email":"{email}","username":"{user}","password":"{pass}","device_id":"{random6}"}""",
                    timeoutSeconds = 12,
                    maxRetries = 1,
                    liveKeywords = "token,session,authenticated,success,data",
                    dieKeywords = "invalid_credentials,unauthorized,locked,banned",
                    userAgent = "BinCheck-ConfigStudio/2.5",
                    followRedirects = true,
                    parseRulesSpec = serializeParseRules(
                        listOf(
                            CustomParseRule(
                                name = "email",
                                mode = ParseRuleMode.JSON,
                                expression = "json.email",
                                showInOutput = true
                            )
                        )
                    ),
                    checkpointRulesSpec = serializeCheckpointRules(
                        listOf(
                            CustomCheckpointRule(
                                verdict = ResultBucket.DIE,
                                condition = CheckpointCondition.STATUS_CODE_IN,
                                expectedValue = "401,403,422",
                                label = "Auth Rejected"
                            ),
                            CustomCheckpointRule(
                                verdict = ResultBucket.LIVE,
                                condition = CheckpointCondition.CAPTURED_VAR_CONTAINS,
                                targetKey = "email",
                                expectedValue = "@",
                                label = "Echo Verified"
                            )
                        )
                    )
                )
            )
        )
    }

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
                if (rule.showInOutput) "1" else "0"
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
            CustomParseRule(
                name = name,
                mode = mode,
                expression = expr,
                leftDelimiter = left,
                rightDelimiter = right,
                showInOutput = show
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
            val extracted: String? = when (rule.mode) {
                ParseRuleMode.JSON -> extractJsonPathValue(bodyStr, rule.expression.trim())
                ParseRuleMode.LR -> extractLeftRightValue(bodyStr, rule.leftDelimiter, rule.rightDelimiter)
                ParseRuleMode.REGEX -> extractRegexGroupValue(bodyStr, rule.expression)
                ParseRuleMode.HEADER -> {
                    val key = rule.expression.trim().lowercase(Locale.ROOT)
                    if (key.isEmpty()) null else {
                        responseHeaders[key] ?: responseHeaders.entries.firstOrNull {
                            it.key.equals(key, ignoreCase = true)
                        }?.value
                    }
                }
            }
            if (!extracted.isNullOrBlank()) {
                captured[varName] = extracted.trim().take(90)
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
        val entries = captures.entries.filter { visibleNames.isEmpty() || it.key in visibleNames }
        if (entries.isEmpty()) return ""
        return entries.take(4).joinToString(" ") { (k, v) -> "[$k=${v.take(28)}]" }
    }

    /**
     * Pure-Kotlin JSON field/dot-path extractor (supports top-level `code` and nested `error.code` or `json.card.scheme`).
     * Works identically on Android runtime and pure JVM unit tests without `org.json.JSONObject` Stub! exceptions.
     */
    internal fun extractJsonPathValue(jsonText: String, path: String): String? {
        if (jsonText.isBlank() || path.isBlank()) return null
        val segments = path.split('.').map { it.trim() }.filter { it.isNotEmpty() }
        if (segments.isEmpty()) return null

        var currentSlice = jsonText.trim()
        for ((index, key) in segments.withIndex()) {
            val isLast = index == segments.lastIndex
            val escapedKey = Regex.escape(key)
            if (!isLast) {
                val objStartMatch = Regex(""""$escapedKey"\s*:\s*\{""").find(currentSlice)
                if (objStartMatch != null) {
                    val braceStart = objStartMatch.range.last
                    val subSlice = extractBalancedBraces(currentSlice, braceStart)
                    if (subSlice != null) {
                        currentSlice = subSlice
                        continue
                    }
                }
            }
            val strRegex = Regex(""""$escapedKey"\s*:\s*"((?:\\.|[^"\\])*)"""")
            val strMatch = strRegex.find(currentSlice)
            if (strMatch != null) {
                return unescapeJsonString(strMatch.groupValues[1])
            }
            val primRegex = Regex(""""$escapedKey"\s*:\s*(-?\d+(?:\.\d+)?|true|false|null)\b""", RegexOption.IGNORE_CASE)
            val primMatch = primRegex.find(currentSlice)
            if (primMatch != null) {
                val rawPrim = primMatch.groupValues[1]
                return if (rawPrim.equals("null", ignoreCase = true)) null else rawPrim
            }
            return null
        }
        return null
    }

    private fun extractBalancedBraces(text: String, openBraceIndex: Int): String? {
        if (openBraceIndex < 0 || openBraceIndex >= text.length || text[openBraceIndex] != '{') return null
        var depth = 0
        var inQuotes = false
        var escaped = false
        for (i in openBraceIndex until text.length) {
            val ch = text[i]
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
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return text.substring(openBraceIndex, i + 1)
                    }
                }
            }
        }
        return null
    }

    internal fun extractLeftRightValue(text: String, left: String, right: String): String? {
        if (text.isEmpty() || left.isEmpty()) return null
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

    internal fun extractRegexGroupValue(text: String, patternStr: String): String? {
        if (text.isEmpty() || patternStr.isBlank()) return null
        return runCatching {
            val regex = Regex(patternStr, setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            val match = regex.find(text) ?: return@runCatching null
            if (match.groupValues.size > 1) {
                match.groupValues[1]
            } else {
                match.value
            }
        }.getOrNull()
    }

    // =========================================================================
    // 2. Checkpoints / Keycheck Rules Serialization & Evaluation
    // =========================================================================

    fun serializeCheckpointRules(rules: List<CustomCheckpointRule>): String {
        if (rules.isEmpty()) return ""
        return rules.mapNotNull { rule ->
            if (rule.expectedValue.isBlank() && rule.condition != CheckpointCondition.CAPTURED_VAR_CONTAINS) {
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
     * Evaluates ordered [CustomCheckpointRule] items. Returns the first matching `(ResultBucket, reasonLabel)`
     * or `null` if no checkpoint matched (allowing fallback to keyword/status classification).
     */
    fun evaluateCheckpoints(
        statusCode: Int,
        bodyStr: String,
        captures: Map<String, String>,
        rules: List<CustomCheckpointRule>
    ): Pair<ResultBucket, String>? {
        if (rules.isEmpty()) return null
        for (rule in rules) {
            val expected = rule.expectedValue.trim()
            val matched = when (rule.condition) {
                CheckpointCondition.BODY_CONTAINS -> {
                    expected.isNotEmpty() && bodyStr.contains(expected, ignoreCase = true)
                }
                CheckpointCondition.BODY_CONTAINS_ANY -> {
                    val terms = expected.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                    terms.any { term -> bodyStr.contains(term, ignoreCase = true) }
                }
                CheckpointCondition.BODY_NOT_CONTAINS -> {
                    expected.isNotEmpty() && !bodyStr.contains(expected, ignoreCase = true)
                }
                CheckpointCondition.STATUS_CODE_EQUALS -> {
                    expected.toIntOrNull() == statusCode
                }
                CheckpointCondition.STATUS_CODE_IN -> {
                    val codes = expected.split(',', '|', ';', ' ').mapNotNull { it.trim().toIntOrNull() }
                    statusCode in codes
                }
                CheckpointCondition.JSON_KEY_EQUALS -> {
                    val actual = extractJsonPathValue(bodyStr, rule.targetKey.trim())
                    actual != null && (expected.isEmpty() || actual.equals(expected, ignoreCase = true))
                }
                CheckpointCondition.CAPTURED_VAR_CONTAINS -> {
                    val actual = lookupCapturedVar(captures, rule.targetKey)
                    actual != null && (expected.isEmpty() || actual.contains(expected, ignoreCase = true))
                }
                CheckpointCondition.CAPTURED_VAR_EQUALS -> {
                    val actual = lookupCapturedVar(captures, rule.targetKey)
                    actual != null && actual.equals(expected, ignoreCase = true)
                }
                CheckpointCondition.REGEX_MATCH -> {
                    expected.isNotEmpty() && runCatching {
                        Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(bodyStr)
                    }.getOrDefault(false)
                }
            }
            if (matched) {
                val tag = rule.label.ifBlank {
                    when (rule.condition) {
                        CheckpointCondition.STATUS_CODE_EQUALS,
                        CheckpointCondition.STATUS_CODE_IN -> "HTTP $statusCode"
                        CheckpointCondition.JSON_KEY_EQUALS -> "${rule.targetKey}=${expected.take(18)}"
                        CheckpointCondition.CAPTURED_VAR_CONTAINS,
                        CheckpointCondition.CAPTURED_VAR_EQUALS -> "${rule.targetKey}${if (expected.isNotEmpty()) "=$expected" else ""}"
                        else -> expected.take(24)
                    }
                }
                return rule.verdict to "Checkpoint: $tag"
            }
        }
        return null
    }

    private fun lookupCapturedVar(captures: Map<String, String>, key: String): String? {
        val k = key.trim()
        if (k.isEmpty()) return null
        return captures[k] ?: captures.entries.firstOrNull { it.key.equals(k, ignoreCase = true) }?.value
    }

    // =========================================================================
    // 3. Proxy List Parsing & Rotation
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
    // 4. DevTools Importer: cURL (bash) & JS fetch(...)
    // =========================================================================

    /**
     * Imports a Chrome/DevTools `curl ...` or `fetch("...", { ... })` snippet into a [CustomHttpConfig],
     * extracting URL, HTTP method, headers, User-Agent, and request body, with optional auto-tokenization
     * of concrete card/expiry/cvv/email fields into `{card}`, `{mm}`, `{yyyy}`, `{cvv}`, `{email}`, `{pass}`.
     */
    fun importFromCurlOrFetch(
        rawSnippet: String,
        autoTokenizeFields: Boolean = true
    ): Result<CustomHttpConfig> = runCatching {
        val snippet = rawSnippet.trim()
        require(snippet.isNotEmpty()) { "Paste a curl command or JS fetch(...) snippet first." }

        val parsed = if (snippet.contains("fetch(", ignoreCase = true) &&
            !snippet.trimStart().startsWith("curl", ignoreCase = true)
        ) {
            parseFetchSnippet(snippet)
        } else {
            parseCurlSnippet(snippet)
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
            url = parsed.url,
            method = resolvedMethod,
            headersText = filteredHeaders.joinToString("\n"),
            bodyTemplate = tokenizedBody,
            userAgent = userAgent,
            followRedirects = true
        )
    }

    private data class RawParsedSnippet(
        val url: String,
        val explicitMethod: String,
        val headers: List<Pair<String, String>>,
        val body: String
    )

    private fun parseFetchSnippet(snippet: String): RawParsedSnippet {
        val urlMatch = Regex("""fetch\s*\(\s*["'`](https?://[^"'`\s]+)["'`]""", RegexOption.IGNORE_CASE)
            .find(snippet)
            ?: throw IllegalArgumentException("Could not find URL inside fetch(\"...\").")
        val url = urlMatch.groupValues[1].trim()

        val methodMatch = Regex(""""?method"?\s*:\s*["'`]([A-Za-z]+)["'`]""", RegexOption.IGNORE_CASE)
            .find(snippet)
        val method = methodMatch?.groupValues?.getOrNull(1)?.uppercase(Locale.ROOT) ?: ""

        val headers = mutableListOf<Pair<String, String>>()
        val headersBlockMatch = Regex(""""?headers"?\s*:\s*\{([^}]*)\}""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(snippet)
        if (headersBlockMatch != null) {
            val block = headersBlockMatch.groupValues[1]
            val kvRegex = Regex("""["'`]([^"'`]+)["'`]\s*:\s*["'`]([^"'`]*)["'`]""")
            kvRegex.findAll(block).forEach { m ->
                headers.add(m.groupValues[1].trim() to m.groupValues[2].trim())
            }
        }

        val bodyMatch = Regex(""""?body"?\s*:\s*"((?:\\.|[^"\\])*)"""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(snippet)
            ?: Regex(""""?body"?\s*:\s*'((?:\\.|[^'\\])*)'""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
                .find(snippet)
        val rawBody = bodyMatch?.groupValues?.getOrNull(1)?.let { unescapeJsonString(it) }.orEmpty()

        return RawParsedSnippet(
            url = url,
            explicitMethod = method,
            headers = headers,
            body = rawBody
        )
    }

    private fun parseCurlSnippet(snippet: String): RawParsedSnippet {
        // Normalize bash backslash-newline line continuations
        val cleaned = snippet.replace(Regex("""\\\r?\n"""), " ")
        val tokens = tokenizeShellArgs(cleaned)

        var url = ""
        var explicitMethod = ""
        val headers = mutableListOf<Pair<String, String>>()
        val bodyParts = mutableListOf<String>()

        var i = 0
        while (i < tokens.size) {
            val tok = tokens[i]
            when {
                tok.equals("curl", ignoreCase = true) && i == 0 -> Unit
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
                (tok == "-d" || tok == "--data" || tok == "--data-raw" || tok == "--data-binary" || tok == "--data-urlencode") && i + 1 < tokens.size -> {
                    bodyParts.add(tokens[++i])
                }
                tok.startsWith("--data-raw=") -> bodyParts.add(tok.substringAfter('='))
                tok.startsWith("--data=") -> bodyParts.add(tok.substringAfter('='))
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
            body = bodyParts.joinToString("&")
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
            Regex("""((?:card\[cvc\]|card\[cvv\]|cvc|cvv)\s*=\s*)(\d{3,4})""", RegexOption.IGNORE_CASE),
            "$1{cvv}"
        )
        out = out.replace(
            Regex("""((?:card\[exp_month\]|exp_month|month)\s*=\s*)(\d{1,2})""", RegexOption.IGNORE_CASE),
            "$1{mm}"
        )
        out = out.replace(
            Regex("""((?:card\[exp_year\]|exp_year|year)\s*=\s*)(\d{2,4})""", RegexOption.IGNORE_CASE),
            "$1{yyyy}"
        )
        // Replace JSON "number":"...", "cvv":"...", "exp_month":..., "exp_year":...
        out = out.replace(
            Regex("""("(?:number|card_number|cardNumber|pan)"\s*:\s*")([0-9\s-]{13,24})(")""", RegexOption.IGNORE_CASE),
            "$1{card}$3"
        )
        out = out.replace(
            Regex("""("(?:cvc|cvv)"\s*:\s*")(\d{3,4})(")""", RegexOption.IGNORE_CASE),
            "$1{cvv}$3"
        )
        out = out.replace(
            Regex("""("(?:exp_month|month)"\s*:\s*")(\d{1,2})(")""", RegexOption.IGNORE_CASE),
            "$1{mm}$3"
        )
        out = out.replace(
            Regex("""("(?:exp_year|year)"\s*:\s*")(\d{2,4})(")""", RegexOption.IGNORE_CASE),
            "$1{yyyy}$3"
        )
        // Standalone raw 15-16 digit card pattern fallback
        out = out.replace(Regex("""\b(?:4|5|3[47]|6(?:011|5))\d{11,15}\b"""), "{card}")
        return out
    }

    // =========================================================================
    // 5. Dynamic Request Tokens
    // =========================================================================

    fun generateUuidToken(): String = UUID.randomUUID().toString()

    fun generateTimestampSeconds(): String = (System.currentTimeMillis() / 1000L).toString()

    fun generateTimestampMillis(): String = System.currentTimeMillis().toString()

    fun generateRandomDigits(count: Int = 6): String =
        buildString(count) {
            repeat(count.coerceIn(1, 16)) { append(Random.nextInt(0, 10)) }
        }

    fun formatSpacedPan(pan: String): String {
        val digits = pan.filter { it.isDigit() }
        if (digits.length <= 4) return digits
        return digits.chunked(4).joinToString(" ")
    }

    // =========================================================================
    // 6. Pure-Kotlin JSON Config Export / Import & Saved Profiles Serialization
    // =========================================================================

    fun exportConfigToJson(config: CustomHttpConfig): String {
        val parseRules = parseParseRules(config.parseRulesSpec)
        val checkpoints = parseCheckpointRules(config.checkpointRulesSpec)

        return buildString(512) {
            append("{\n")
            append("  \"format\": \"bincheck_custom_config_v1\",\n")
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
            append("  \"parseRulesCount\": ${parseRules.size},\n")
            append("  \"checkpointsCount\": ${checkpoints.size}\n")
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
            proxyListText = proxyListText
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
