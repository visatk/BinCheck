package com.example.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * Executes an ordered pipeline of [ConfigBlock] items across all 9 block categories:
 * Browser, Captchas, Conditions, Functions, Interop, Parsing, Requests, Selenium, and Utility.
 */
object ConfigBlockExecutor {

    suspend fun executeStack(
        blocks: List<ConfigBlock>,
        normalizedLine: String,
        pan: String,
        mm: String,
        yyyy: String,
        cvv: String,
        scheme: CardScheme,
        config: CustomHttpConfig,
        initialSourceBody: String = "",
        initialStatusCode: Int? = null,
        initialHeaders: Map<String, String> = emptyMap(),
        initialCaptures: Map<String, String> = emptyMap(),
        initialCookies: String = "",
        dryRunNetwork: Boolean = false
    ): BlockPipelineResult {
        val pipelineStart = System.nanoTime()
        val variables = LinkedHashMap<String, String>(initialCaptures)
        val captures = LinkedHashMap<String, String>()
        val cookies = LinkedHashMap<String, String>()
        val logs = mutableListOf<String>()
        val steps = mutableListOf<BlockExecutionStep>()

        var currentSource = initialSourceBody
        var currentStatusCode = initialStatusCode
        var currentHeaders = LinkedHashMap<String, String>(initialHeaders)
        var winningVerdict: Pair<ResultBucket, String>? = null

        if (initialSourceBody.isNotEmpty()) {
            variables["SOURCE"] = initialSourceBody
        }
        if (initialStatusCode != null) {
            variables["STATUS_CODE"] = initialStatusCode.toString()
        }
        if (initialCookies.isNotBlank()) {
            parseCookieStringInto(initialCookies, cookies)
            variables["COOKIE"] = initialCookies
        }

        val selectedProxy = CustomConfigStudioEngine.selectProxyForRequest(config)

        for ((idx, block) in blocks.withIndex()) {
            val stepStart = System.nanoTime()
            if (!block.enabled) {
                steps.add(
                    BlockExecutionStep(
                        index = idx + 1,
                        block = block,
                        status = BlockStepStatus.SKIPPED,
                        detail = "Disabled",
                        durationMs = 0L
                    )
                )
                continue
            }

            fun resolve(raw: String, urlEncode: Boolean = false): String {
                var out = CustomHttpClient.substitutePlaceholders(
                    template = raw,
                    normalizedLine = normalizedLine,
                    pan = pan,
                    mm = mm,
                    yyyy = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    urlEncodeValues = urlEncode,
                    step1Captures = variables,
                    step1Cookies = formatCookieJar(cookies)
                )
                out = out.replace("<SOURCE>", currentSource, ignoreCase = true)
                out = out.replace("<STATUS>", currentStatusCode?.toString().orEmpty(), ignoreCase = true)
                out = out.replace("<COOKIES>", formatCookieJar(cookies), ignoreCase = true)
                for ((k, v) in variables) {
                    out = out.replace("<$k>", v, ignoreCase = true)
                }
                return out
            }

            fun writeOutput(rawValue: String, targetVarOverride: String = "") {
                val outName = targetVarOverride.ifBlank { block.outputVar.trim() }
                if (outName.equals("SOURCE", ignoreCase = true)) {
                    currentSource = rawValue
                    variables["SOURCE"] = rawValue
                } else if (outName.isNotEmpty()) {
                    variables[outName] = rawValue
                    if (block.captureToOutput) {
                        captures[outName] = rawValue.take(160)
                    }
                }
            }

            var status = BlockStepStatus.SUCCESS
            var detail = ""
            var outputPreview = ""

            when (block.type) {
                // =============================================================
                // 4. FUNCTIONS
                // =============================================================
                BlockType.FUNC_HASH -> {
                    val input = resolve(block.inputTarget)
                    val algo = block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "SHA-256" }
                    val stdAlgo = when (algo) {
                        "MD5" -> "MD5"
                        "SHA1", "SHA-1" -> "SHA-1"
                        "SHA512", "SHA-512" -> "SHA-512"
                        else -> "SHA-256"
                    }
                    val digest = computeDigestHex(stdAlgo, input)
                    writeOutput(digest)
                    detail = "$stdAlgo(${input.length} chars)"
                    outputPreview = digest
                }

                BlockType.FUNC_HMAC -> {
                    val msg = resolve(block.inputTarget)
                    val secret = resolve(block.secondaryParam)
                    val algo = block.tertiaryParam.trim().ifEmpty { "HmacSHA256" }
                    val hmac = computeHmacHex(algo, msg, secret)
                    writeOutput(hmac)
                    detail = "$algo signature (${hmac.length} hex chars)"
                    outputPreview = hmac
                }

                BlockType.FUNC_BASE64_ENCODE -> {
                    val input = resolve(block.inputTarget)
                    val encoded = CustomConfigStudioEngine.encodeBase64(input)
                    writeOutput(encoded)
                    detail = "Encoded ${input.length} chars to Base64"
                    outputPreview = encoded
                }

                BlockType.FUNC_BASE64_DECODE -> {
                    val input = resolve(block.inputTarget)
                    val decoded = decodeBase64Safe(input)
                    if (decoded == null) {
                        status = BlockStepStatus.FAILED
                        detail = "Invalid Base64 input"
                    } else {
                        writeOutput(decoded)
                        detail = "Decoded ${decoded.length} chars"
                        outputPreview = decoded
                    }
                }

                BlockType.FUNC_URL_ENCODE -> {
                    val input = resolve(block.inputTarget)
                    val mode = block.secondaryParam.trim().uppercase(Locale.ROOT)
                    val result = runCatching {
                        if (mode == "DECODE") URLDecoder.decode(input, "UTF-8")
                        else URLEncoder.encode(input, "UTF-8")
                    }.getOrDefault(input)
                    writeOutput(result)
                    detail = if (mode == "DECODE") "URL-decoded" else "URL-encoded"
                    outputPreview = result
                }

                BlockType.FUNC_RANDOM_STRING -> {
                    val mode = block.inputTarget.trim().uppercase(Locale.ROOT).ifEmpty { "HEX" }
                    val len = block.secondaryParam.trim().toIntOrNull()?.coerceIn(1, 128) ?: 16
                    val generated = when (mode) {
                        "UUID", "GUID" -> CustomConfigStudioEngine.generateUuidToken()
                        "DIGITS", "NUM", "NUMBER" -> CustomConfigStudioEngine.generateRandomDigits(len)
                        "ALNUM", "ALPHANUMERIC" -> generateRandomAlnum(len)
                        else -> CustomConfigStudioEngine.generateRandomHex(len)
                    }
                    writeOutput(generated)
                    detail = "Generated $mode ($len)"
                    outputPreview = generated
                }

                BlockType.FUNC_TIMESTAMP -> {
                    val fmt = block.inputTarget.trim().uppercase(Locale.ROOT).ifEmpty { "UNIX_S" }
                    val out = when (fmt) {
                        "UNIX_MS", "MS" -> CustomConfigStudioEngine.generateTimestampMillis()
                        "ISO", "ISO_UTC", "ISO_DATE" -> CustomConfigStudioEngine.generateIsoTimestamp()
                        else -> CustomConfigStudioEngine.generateTimestampSeconds()
                    }
                    writeOutput(out)
                    detail = "Timestamp ($fmt)"
                    outputPreview = out
                }

                BlockType.FUNC_STRING_REPLACE -> {
                    val input = resolve(block.inputTarget)
                    val find = resolve(block.secondaryParam)
                    val replaceWith = resolve(block.tertiaryParam)
                    val useRegex = block.extraParams["regex"] == "true"
                    val out = if (find.isEmpty()) {
                        input
                    } else if (useRegex) {
                        runCatching { Regex(find).replace(input, replaceWith) }.getOrDefault(input)
                    } else {
                        input.replace(find, replaceWith)
                    }
                    writeOutput(out)
                    detail = "Replaced '${find.take(18)}'"
                    outputPreview = out
                }

                BlockType.FUNC_STRING_CASE -> {
                    val input = resolve(block.inputTarget)
                    val op = block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "LOWER" }
                    val out = when (op) {
                        "UPPER", "UPPERCASE" -> input.uppercase(Locale.ROOT)
                        "TRIM" -> input.trim()
                        "LENGTH", "LEN" -> input.length.toString()
                        else -> input.lowercase(Locale.ROOT)
                    }
                    writeOutput(out)
                    detail = "String $op"
                    outputPreview = out
                }

                BlockType.FUNC_MATH_EVAL -> {
                    val expr = resolve(block.inputTarget)
                    val decimals = block.secondaryParam.trim().toIntOrNull()?.coerceIn(0, 6) ?: 0
                    val value = evaluateMathExpression(expr)
                    if (value == null) {
                        status = BlockStepStatus.FAILED
                        detail = "Invalid math expression: $expr"
                    } else {
                        val formatted = if (decimals == 0 && value % 1.0 == 0.0) {
                            value.roundToLong().toString()
                        } else if (decimals == 0) {
                            value.roundToLong().toString()
                        } else {
                            String.format(Locale.US, "%.${decimals}f", value)
                        }
                        writeOutput(formatted)
                        detail = "$expr = $formatted"
                        outputPreview = formatted
                    }
                }

                BlockType.FUNC_CONSTANT -> {
                    val valResolved = resolve(block.inputTarget)
                    writeOutput(valResolved)
                    detail = "Assigned constant (${valResolved.length} chars)"
                    outputPreview = valResolved
                }

                // =============================================================
                // 6. PARSING
                // =============================================================
                BlockType.PARSE_LR -> {
                    val sourceKey = block.tertiaryParam.substringBefore('|').trim().ifEmpty { "<SOURCE>" }
                    val fallback = block.tertiaryParam.substringAfter('|', "").trim()
                    val sourceText = resolveSourceVar(sourceKey, currentSource, variables)
                    val left = resolve(block.inputTarget)
                    val right = resolve(block.secondaryParam)
                    val rec = block.extraParams["recursive"] == "true"
                    val extracted = CustomConfigStudioEngine.extractLeftRightValue(sourceText, left, right, rec)
                        ?.takeIf { it.isNotBlank() } ?: fallback.takeIf { it.isNotEmpty() }
                    if (extracted != null) {
                        writeOutput(extracted)
                        detail = "LR extracted ${extracted.length} chars"
                        outputPreview = extracted
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "LR delimiters not found"
                    }
                }

                BlockType.PARSE_JSON -> {
                    val path = resolve(block.inputTarget)
                    val sourceKey = block.secondaryParam.trim().ifEmpty { "<SOURCE>" }
                    val sourceText = resolveSourceVar(sourceKey, currentSource, variables)
                    val rec = block.extraParams["recursive"] == "true"
                    val extracted = CustomConfigStudioEngine.extractJsonPathValue(sourceText, path, rec)
                        ?.takeIf { it.isNotBlank() } ?: block.tertiaryParam.trim().takeIf { it.isNotEmpty() }
                    if (extracted != null) {
                        writeOutput(extracted)
                        detail = "JSON '$path'"
                        outputPreview = extracted
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "JSON path '$path' not found"
                    }
                }

                BlockType.PARSE_REGEX -> {
                    val pattern = block.inputTarget
                    val sourceKey = block.secondaryParam.trim().ifEmpty { "<SOURCE>" }
                    val sourceText = resolveSourceVar(sourceKey, currentSource, variables)
                    val rec = block.extraParams["recursive"] == "true"
                    val extracted = CustomConfigStudioEngine.extractRegexGroupValue(sourceText, pattern, rec)
                        ?.takeIf { it.isNotBlank() } ?: block.tertiaryParam.trim().takeIf { it.isNotEmpty() }
                    if (extracted != null) {
                        writeOutput(extracted)
                        detail = "Regex matched"
                        outputPreview = extracted
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "Regex did not match"
                    }
                }

                BlockType.PARSE_CSS -> {
                    val selector = resolve(block.inputTarget)
                    val sourceKey = block.secondaryParam.trim().ifEmpty { "<SOURCE>" }
                    val sourceText = resolveSourceVar(sourceKey, currentSource, variables)
                    val extracted = CustomConfigStudioEngine.extractHtmlCssValue(sourceText, selector)
                        ?.takeIf { it.isNotBlank() } ?: block.tertiaryParam.trim().takeIf { it.isNotEmpty() }
                    if (extracted != null) {
                        writeOutput(extracted)
                        detail = "CSS '$selector'"
                        outputPreview = extracted
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "CSS selector '$selector' not found"
                    }
                }

                BlockType.PARSE_HEADER -> {
                    val hdrName = resolve(block.inputTarget).trim()
                    val value = currentHeaders[hdrName.lowercase(Locale.ROOT)]
                        ?: currentHeaders.entries.firstOrNull { it.key.equals(hdrName, ignoreCase = true) }?.value
                        ?: block.tertiaryParam.trim().takeIf { it.isNotEmpty() }
                    if (value != null) {
                        writeOutput(value)
                        detail = "Header '$hdrName'"
                        outputPreview = value
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "Header '$hdrName' missing"
                    }
                }

                BlockType.PARSE_COOKIE -> {
                    val cookieName = resolve(block.inputTarget).trim().ifEmpty { "*" }
                    val fromHeaders = CustomConfigStudioEngine.extractCookieValue(currentHeaders, cookieName)
                    val fromJar = if (cookieName == "*") {
                        formatCookieJar(cookies).takeIf { it.isNotBlank() }
                    } else {
                        cookies[cookieName] ?: cookies.entries.firstOrNull { it.key.equals(cookieName, ignoreCase = true) }?.value
                    }
                    val resolvedCookie = fromHeaders ?: fromJar ?: block.tertiaryParam.trim().takeIf { it.isNotEmpty() }
                    if (resolvedCookie != null) {
                        writeOutput(resolvedCookie)
                        detail = "Cookie '$cookieName'"
                        outputPreview = resolvedCookie
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "Cookie '$cookieName' not found"
                    }
                }

                // =============================================================
                // 3. CONDITIONS
                // =============================================================
                BlockType.CONDITION_KEYCHECK -> {
                    val targetExpr = block.inputTarget.trim().ifEmpty { "<SOURCE>" }
                    val textToCheck = resolveSourceVar(targetExpr, currentSource, variables)
                    val op = block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "CONTAINS_ANY" }
                    val expectedRaw = resolve(block.tertiaryParam)
                    val matched = evaluateStringCondition(textToCheck, op, expectedRaw)
                    writeOutput(matched.toString())
                    if (matched) {
                        status = BlockStepStatus.MATCHED
                        if (winningVerdict == null) {
                            winningVerdict = block.verdictOnMatch to "Block: ${block.label}"
                        }
                        detail = "Matched -> ${block.verdictOnMatch.name} ($op)"
                        outputPreview = expectedRaw.take(40)
                    } else {
                        detail = "Not matched ($op)"
                        outputPreview = "false"
                    }
                }

                BlockType.CONDITION_IF_STRING -> {
                    val left = resolve(block.inputTarget)
                    val op = block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "EQUALS" }
                    val right = resolve(block.tertiaryParam)
                    val matched = evaluateStringCondition(left, op, right)
                    writeOutput(matched.toString())
                    status = if (matched) BlockStepStatus.MATCHED else BlockStepStatus.SUCCESS
                    detail = "'${left.take(20)}' $op '${right.take(20)}' -> $matched"
                    outputPreview = matched.toString()
                }

                BlockType.CONDITION_IF_NUMBER -> {
                    val leftNum = resolve(block.inputTarget).trim().toDoubleOrNull()
                    val op = block.secondaryParam.trim().ifEmpty { "==" }
                    val rightNum = resolve(block.tertiaryParam).trim().toDoubleOrNull()
                    if (leftNum == null || rightNum == null) {
                        status = BlockStepStatus.FAILED
                        detail = "Non-numeric operand"
                    } else {
                        val matched = when (op) {
                            "==", "=" -> leftNum == rightNum
                            "!=", "<>" -> leftNum != rightNum
                            ">" -> leftNum > rightNum
                            "<" -> leftNum < rightNum
                            ">=" -> leftNum >= rightNum
                            "<=" -> leftNum <= rightNum
                            else -> leftNum == rightNum
                        }
                        writeOutput(matched.toString())
                        status = if (matched) BlockStepStatus.MATCHED else BlockStepStatus.SUCCESS
                        detail = "$leftNum $op $rightNum -> $matched"
                        outputPreview = matched.toString()
                    }
                }

                BlockType.CONDITION_IF_STATUS -> {
                    val codes = resolve(block.inputTarget)
                        .split(',', '|', ';', ' ')
                        .mapNotNull { it.trim().toIntOrNull() }
                    val negate = block.secondaryParam.trim().equals("true", ignoreCase = true)
                    val cur = currentStatusCode
                    val inList = cur != null && cur in codes
                    val matched = if (negate) !inList else inList
                    writeOutput(matched.toString())
                    status = if (matched) BlockStepStatus.MATCHED else BlockStepStatus.SUCCESS
                    detail = "HTTP ${cur ?: "none"} ${if (matched) "matched" else "did not match"} [$codes]"
                    outputPreview = matched.toString()
                }

                BlockType.CONDITION_IF_EXISTS -> {
                    val varName = resolve(block.inputTarget).trim()
                    val mustBeNonBlank = !block.secondaryParam.trim().equals("false", ignoreCase = true)
                    val exists = if (mustBeNonBlank) {
                        !variables[varName].isNullOrBlank()
                    } else {
                        variables.containsKey(varName)
                    }
                    writeOutput(exists.toString())
                    status = if (exists) BlockStepStatus.MATCHED else BlockStepStatus.SUCCESS
                    detail = "Var '$varName' exists = $exists"
                    outputPreview = exists.toString()
                }

                // =============================================================
                // 7. REQUESTS
                // =============================================================
                BlockType.REQUEST_SET_COOKIE -> {
                    val name = resolve(block.inputTarget).trim()
                    val value = resolve(block.secondaryParam)
                    if (name.isNotEmpty()) {
                        cookies[name] = value
                        variables["COOKIE"] = formatCookieJar(cookies)
                        detail = "Set cookie $name"
                        outputPreview = "$name=$value"
                    } else {
                        status = BlockStepStatus.FAILED
                        detail = "Cookie name is empty"
                    }
                }

                BlockType.REQUEST_CLEAR_COOKIES -> {
                    val count = cookies.size
                    cookies.clear()
                    variables.remove("COOKIE")
                    detail = "Cleared $count cookie(s)"
                }

                BlockType.REQUEST_HTTP,
                BlockType.REQUEST_MULTIPART -> {
                    val url = resolve(block.inputTarget, urlEncode = true).trim()
                    val method = if (block.type == BlockType.REQUEST_MULTIPART) {
                        "POST"
                    } else {
                        block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "GET" }
                    }
                    val body = resolve(block.tertiaryParam)
                    val extraHeaders = resolve(block.extraParams["headers"].orEmpty())

                    if (dryRunNetwork) {
                        status = BlockStepStatus.SIMULATED
                        detail = "Dry-run $method $url"
                        outputPreview = body.take(80).ifEmpty { url }
                    } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        status = BlockStepStatus.FAILED
                        detail = "Invalid URL: $url"
                    } else {
                        val reqOutcome = executeBlockHttpCall(
                            url = url,
                            method = method,
                            headersText = extraHeaders,
                            bodyStr = body,
                            isMultipart = block.type == BlockType.REQUEST_MULTIPART,
                            multipartSpec = resolve(block.secondaryParam),
                            userAgent = config.userAgent,
                            cookieHeader = formatCookieJar(cookies),
                            timeoutSeconds = config.timeoutSeconds,
                            followRedirects = config.followRedirects,
                            proxyEndpoint = selectedProxy
                        )
                        if (reqOutcome.isSuccess) {
                            val resp = reqOutcome.getOrThrow()
                            currentStatusCode = resp.statusCode
                            variables["STATUS_CODE"] = resp.statusCode.toString()
                            currentHeaders = LinkedHashMap(resp.headers)
                            CustomConfigStudioEngine.extractCookieValue(resp.headers, "*")?.let { ck ->
                                parseCookieStringInto(ck, cookies)
                                variables["COOKIE"] = formatCookieJar(cookies)
                            }
                            writeOutput(resp.body, block.outputVar.ifBlank { "SOURCE" })
                            detail = "$method -> HTTP ${resp.statusCode} (${resp.body.length} chars)"
                            outputPreview = resp.body.replace(Regex("\\s+"), " ").take(80)
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = "HTTP failed: ${reqOutcome.exceptionOrNull()?.localizedMessage ?: "Error"}"
                        }
                    }
                }

                // =============================================================
                // 2. CAPTCHAS
                // =============================================================
                BlockType.CAPTCHA_BALANCE -> {
                    val provider = resolve(block.inputTarget).trim().ifEmpty { "2Captcha" }
                    val apiKey = resolve(block.secondaryParam).trim()
                    if (dryRunNetwork || apiKey.isEmpty()) {
                        status = BlockStepStatus.SIMULATED
                        detail = if (apiKey.isEmpty()) "$provider: API key not set (dry-run)" else "Dry-run balance query ($provider)"
                        outputPreview = "0.00"
                    } else {
                        val balRes = queryCaptchaBalance(provider, apiKey, config.timeoutSeconds)
                        if (balRes.isSuccess) {
                            val bal = balRes.getOrThrow()
                            writeOutput(bal)
                            detail = "$provider balance: $bal"
                            outputPreview = bal
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = balRes.exceptionOrNull()?.localizedMessage ?: "Balance check failed"
                        }
                    }
                }

                BlockType.CAPTCHA_RECAPTCHA_V2,
                BlockType.CAPTCHA_RECAPTCHA_V3,
                BlockType.CAPTCHA_HCAPTCHA,
                BlockType.CAPTCHA_TURNSTILE,
                BlockType.CAPTCHA_IMAGE,
                BlockType.CAPTCHA_FUNCAPTCHA -> {
                    val siteKeyOrImg = resolve(block.inputTarget).trim()
                    val providerOrAction = resolve(block.secondaryParam).trim().ifEmpty { "2Captcha" }
                    val tertiaryResolved = resolve(block.tertiaryParam).trim()
                    val pageUrl = tertiaryResolved.substringBefore('|').trim()
                    val apiKey = tertiaryResolved.substringAfter('|', "").trim()

                    if (dryRunNetwork || apiKey.isEmpty()) {
                        status = BlockStepStatus.SIMULATED
                        val modeLabel = block.type.displayName
                        val simNote = if (apiKey.isEmpty()) {
                            "$modeLabel configured ($providerOrAction • sitekey=${siteKeyOrImg.take(12)} • add API key for live solve)"
                        } else {
                            "Dry-run $modeLabel via $providerOrAction"
                        }
                        detail = simNote
                        outputPreview = if (siteKeyOrImg.isNotEmpty()) "ready:${siteKeyOrImg.take(14)}" else "ready"
                    } else {
                        val submitRes = submitCaptchaSolveTask(
                            type = block.type,
                            provider = providerOrAction,
                            siteKeyOrImage = siteKeyOrImg,
                            pageUrl = pageUrl,
                            apiKey = apiKey,
                            timeoutSeconds = config.timeoutSeconds
                        )
                        if (submitRes.isSuccess) {
                            val token = submitRes.getOrThrow()
                            writeOutput(token)
                            detail = "${block.type.displayName} solved via $providerOrAction"
                            outputPreview = token
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = submitRes.exceptionOrNull()?.localizedMessage ?: "Captcha solve failed"
                        }
                    }
                }

                // =============================================================
                // 1. BROWSER (Blocks for browser automation)
                // =============================================================
                BlockType.BROWSER_OPEN -> {
                    val engine = resolve(block.inputTarget).ifEmpty { "Headless Chromium" }
                    val ua = resolve(block.secondaryParam).ifEmpty { config.userAgent }
                    val sessionId = "brw_${CustomConfigStudioEngine.generateRandomHex(8)}"
                    variables["BROWSER_ENGINE"] = engine
                    variables["BROWSER_UA"] = ua
                    writeOutput(sessionId)
                    status = BlockStepStatus.SIMULATED
                    detail = "Browser context ready ($engine)"
                    outputPreview = sessionId
                }

                BlockType.BROWSER_NAVIGATE -> {
                    val url = resolve(block.inputTarget).trim()
                    variables["BROWSER_URL"] = url
                    val fetchHtml = block.extraParams["fetchHtml"] == "true"
                    if (!dryRunNetwork && fetchHtml && (url.startsWith("http://") || url.startsWith("https://"))) {
                        val reqOutcome = executeBlockHttpCall(
                            url = url,
                            method = "GET",
                            headersText = "",
                            bodyStr = "",
                            isMultipart = false,
                            multipartSpec = "",
                            userAgent = variables["BROWSER_UA"] ?: config.userAgent,
                            cookieHeader = formatCookieJar(cookies),
                            timeoutSeconds = config.timeoutSeconds,
                            followRedirects = true,
                            proxyEndpoint = selectedProxy
                        )
                        if (reqOutcome.isSuccess) {
                            val resp = reqOutcome.getOrThrow()
                            currentStatusCode = resp.statusCode
                            currentHeaders = LinkedHashMap(resp.headers)
                            CustomConfigStudioEngine.extractCookieValue(resp.headers, "*")?.let { ck ->
                                parseCookieStringInto(ck, cookies)
                            }
                            writeOutput(resp.body, block.outputVar.ifBlank { "SOURCE" })
                            detail = "Fetched DOM (${resp.statusCode}, ${resp.body.length} chars)"
                            outputPreview = url
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = "Navigate fetch failed: ${reqOutcome.exceptionOrNull()?.localizedMessage}"
                        }
                    } else {
                        status = BlockStepStatus.SIMULATED
                        detail = "Browser navigate -> $url"
                        outputPreview = url
                    }
                }

                BlockType.BROWSER_CLICK,
                BlockType.BROWSER_TYPE,
                BlockType.BROWSER_WAIT_ELEMENT -> {
                    val selector = resolve(block.inputTarget)
                    val param2 = resolve(block.secondaryParam)
                    status = BlockStepStatus.SIMULATED
                    detail = when (block.type) {
                        BlockType.BROWSER_CLICK -> "Click '$selector'"
                        BlockType.BROWSER_TYPE -> "Type '${param2.take(24)}' -> '$selector'"
                        else -> "Wait '$selector' ($param2)"
                    }
                    outputPreview = selector
                }

                BlockType.BROWSER_EXEC_JS -> {
                    val script = resolve(block.inputTarget)
                    status = BlockStepStatus.SIMULATED
                    detail = "JS snippet prepared (${script.length} chars)"
                    outputPreview = script.take(48)
                }

                BlockType.BROWSER_GET_COOKIES -> {
                    val name = resolve(block.inputTarget).trim().ifEmpty { "*" }
                    val ckVal = if (name == "*") {
                        formatCookieJar(cookies)
                    } else {
                        cookies[name].orEmpty()
                    }
                    if (ckVal.isNotEmpty()) {
                        writeOutput(ckVal)
                        detail = "Exported browser cookies ($name)"
                        outputPreview = ckVal
                    } else {
                        status = BlockStepStatus.SIMULATED
                        detail = "Browser get cookies '$name' (jar empty)"
                    }
                }

                BlockType.BROWSER_SCREENSHOT -> {
                    val mode = resolve(block.inputTarget).ifEmpty { "DOM_HTML" }
                    if (mode.equals("DOM_HTML", ignoreCase = true) && currentSource.isNotEmpty()) {
                        writeOutput(currentSource)
                        detail = "Captured current DOM (${currentSource.length} chars)"
                        outputPreview = currentSource.take(60)
                    } else {
                        status = BlockStepStatus.SIMULATED
                        detail = "Capture $mode"
                    }
                }

                BlockType.BROWSER_CLOSE -> {
                    if (block.inputTarget.trim().equals("true", ignoreCase = true)) {
                        cookies.clear()
                    }
                    variables.remove("BROWSER_URL")
                    status = BlockStepStatus.SIMULATED
                    detail = "Browser session closed"
                }

                // =============================================================
                // 8. SELENIUM (Blocks for browser automation with Selenium)
                // =============================================================
                BlockType.SELENIUM_OPEN -> {
                    val driver = resolve(block.inputTarget).ifEmpty { "ChromeHeadless" }
                    val args = resolve(block.secondaryParam)
                    val drvId = "wd_${CustomConfigStudioEngine.generateRandomHex(8)}"
                    variables["WEBDRIVER_TYPE"] = driver
                    writeOutput(drvId)
                    status = BlockStepStatus.SIMULATED
                    detail = "WebDriver $driver ($args)"
                    outputPreview = drvId
                }

                BlockType.SELENIUM_NAVIGATE -> {
                    val url = resolve(block.inputTarget).trim()
                    variables["WEBDRIVER_URL"] = url
                    status = BlockStepStatus.SIMULATED
                    detail = "driver.get(\"$url\")"
                    outputPreview = url
                }

                BlockType.SELENIUM_FIND_ELEMENT -> {
                    val locator = resolve(block.inputTarget)
                    val strategy = resolve(block.secondaryParam).ifEmpty { "CSS" }
                    val attr = resolve(block.tertiaryParam)
                    if (strategy.equals("CSS", ignoreCase = true) && currentSource.isNotEmpty()) {
                        val selExpr = if (attr.isNotBlank() && !attr.equals("text", ignoreCase = true)) {
                            "$locator@$attr"
                        } else {
                            locator
                        }
                        val found = CustomConfigStudioEngine.extractHtmlCssValue(currentSource, selExpr)
                        if (found != null) {
                            writeOutput(found)
                            detail = "Found element via CSS '$locator'"
                            outputPreview = found
                        } else {
                            status = BlockStepStatus.SIMULATED
                            detail = "FindElement($strategy: $locator)"
                        }
                    } else {
                        status = BlockStepStatus.SIMULATED
                        detail = "FindElement($strategy: $locator)"
                    }
                }

                BlockType.SELENIUM_SEND_KEYS -> {
                    val locator = resolve(block.inputTarget)
                    val keys = resolve(block.secondaryParam)
                    status = BlockStepStatus.SIMULATED
                    detail = "SendKeys('$locator', '${keys.take(24)}')"
                    outputPreview = locator
                }

                BlockType.SELENIUM_CLICK,
                BlockType.SELENIUM_WAIT -> {
                    val locator = resolve(block.inputTarget)
                    val mode = resolve(block.secondaryParam)
                    status = BlockStepStatus.SIMULATED
                    detail = if (block.type == BlockType.SELENIUM_CLICK) {
                        "Click($mode: '$locator')"
                    } else {
                        "WebDriverWait($mode: '$locator')"
                    }
                    outputPreview = locator
                }

                BlockType.SELENIUM_EXEC_SCRIPT -> {
                    val js = resolve(block.inputTarget)
                    status = BlockStepStatus.SIMULATED
                    detail = "JavascriptExecutor (${js.length} chars)"
                    outputPreview = js.take(48)
                }

                BlockType.SELENIUM_GET_COOKIES -> {
                    val name = resolve(block.inputTarget).trim().ifEmpty { "*" }
                    val ckVal = if (name == "*") formatCookieJar(cookies) else cookies[name].orEmpty()
                    if (ckVal.isNotEmpty()) {
                        writeOutput(ckVal)
                        detail = "WebDriver cookies ($name)"
                        outputPreview = ckVal
                    } else {
                        status = BlockStepStatus.SIMULATED
                        detail = "WebDriver getCookies('$name')"
                    }
                }

                BlockType.SELENIUM_QUIT -> {
                    variables.remove("WEBDRIVER_URL")
                    status = BlockStepStatus.SIMULATED
                    detail = "driver.quit()"
                }

                // =============================================================
                // 5. INTEROP (Blocks for interoperability with other programs)
                // =============================================================
                BlockType.INTEROP_SHELL -> {
                    val cmd = resolve(block.inputTarget).trim()
                    val args = resolve(block.secondaryParam).trim()
                    status = BlockStepStatus.SIMULATED
                    detail = "Shell spec: $cmd $args".trim()
                    outputPreview = "$cmd $args".trim().take(60)
                }

                BlockType.INTEROP_SCRIPT -> {
                    val expr = resolve(block.inputTarget)
                    val runtime = block.secondaryParam.trim().ifEmpty { "Expression" }
                    val evaluated = evaluateSimpleScriptExpression(expr)
                    writeOutput(evaluated)
                    detail = "Script ($runtime) evaluated"
                    outputPreview = evaluated
                }

                BlockType.INTEROP_WEBHOOK_POST -> {
                    val url = resolve(block.inputTarget, urlEncode = true).trim()
                    val payload = resolve(block.secondaryParam)
                    val hdrs = resolve(block.tertiaryParam)
                    if (dryRunNetwork) {
                        status = BlockStepStatus.SIMULATED
                        detail = "Dry-run Webhook POST -> $url"
                        outputPreview = payload.take(80)
                    } else if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
                        status = BlockStepStatus.FAILED
                        detail = "Invalid Webhook URL: $url"
                    } else {
                        val reqOutcome = executeBlockHttpCall(
                            url = url,
                            method = "POST_JSON",
                            headersText = hdrs,
                            bodyStr = payload,
                            isMultipart = false,
                            multipartSpec = "",
                            userAgent = config.userAgent,
                            cookieHeader = "",
                            timeoutSeconds = config.timeoutSeconds,
                            followRedirects = true,
                            proxyEndpoint = selectedProxy
                        )
                        if (reqOutcome.isSuccess) {
                            val resp = reqOutcome.getOrThrow()
                            writeOutput(resp.body)
                            detail = "Webhook HTTP ${resp.statusCode}"
                            outputPreview = resp.body.take(80)
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = "Webhook failed: ${reqOutcome.exceptionOrNull()?.localizedMessage}"
                        }
                    }
                }

                BlockType.INTEROP_TCP_SOCKET -> {
                    val hostPort = resolve(block.inputTarget).trim()
                    val payload = resolve(block.secondaryParam)
                    val timeoutMs = block.tertiaryParam.trim().toIntOrNull()?.coerceIn(300, 10_000) ?: 3_000
                    val host = hostPort.substringBefore(':').trim()
                    val port = hostPort.substringAfter(':', "").trim().toIntOrNull()
                    if (host.isEmpty() || port == null || port !in 1..65535) {
                        status = BlockStepStatus.FAILED
                        detail = "Invalid host:port '$hostPort'"
                    } else if (dryRunNetwork) {
                        status = BlockStepStatus.SIMULATED
                        detail = "Dry-run TCP $host:$port"
                        outputPreview = payload.take(40)
                    } else {
                        val tcpRes = executeTcpExchange(host, port, payload, timeoutMs)
                        if (tcpRes.isSuccess) {
                            val reply = tcpRes.getOrThrow()
                            writeOutput(reply)
                            detail = "TCP $host:$port connected (${reply.length} chars)"
                            outputPreview = reply
                        } else {
                            status = BlockStepStatus.FAILED
                            detail = "TCP error: ${tcpRes.exceptionOrNull()?.localizedMessage}"
                        }
                    }
                }

                BlockType.INTEROP_INTENT_EXPORT -> {
                    val uri = resolve(block.inputTarget)
                    val pkg = resolve(block.secondaryParam)
                    writeOutput(uri)
                    status = BlockStepStatus.SIMULATED
                    detail = if (pkg.isNotBlank()) "Intent URI for $pkg" else "Intent URI prepared"
                    outputPreview = uri
                }

                // =============================================================
                // 9. UTILITY (Utility blocks for miscellaneous purposes)
                // =============================================================
                BlockType.UTIL_DELAY -> {
                    val ms = resolve(block.inputTarget).trim().toLongOrNull()?.coerceIn(0L, 10_000L) ?: 0L
                    if (!dryRunNetwork && ms > 0L) {
                        delay(ms)
                        detail = "Delayed ${ms}ms"
                    } else {
                        status = if (dryRunNetwork) BlockStepStatus.SIMULATED else BlockStepStatus.SUCCESS
                        detail = "Delay ${ms}ms"
                    }
                }

                BlockType.UTIL_LOG -> {
                    val msg = resolve(block.inputTarget)
                    logs.add(msg)
                    detail = msg.take(80)
                    outputPreview = msg
                }

                BlockType.UTIL_SET_VAR -> {
                    val value = resolve(block.inputTarget)
                    val name = block.outputVar.trim().ifEmpty { "custom_var" }
                    writeOutput(value, name)
                    detail = "Set $name (${value.length} chars)"
                    outputPreview = value
                }

                BlockType.UTIL_CLEAR_VAR -> {
                    val name = resolve(block.inputTarget).trim()
                    if (name == "*") {
                        val count = variables.size
                        variables.clear()
                        captures.clear()
                        detail = "Cleared all $count variable(s)"
                    } else {
                        variables.remove(name)
                        captures.remove(name)
                        detail = "Cleared '$name'"
                    }
                }

                BlockType.UTIL_LIST_OP -> {
                    val input = resolve(block.inputTarget)
                    val op = block.secondaryParam.trim().uppercase(Locale.ROOT).ifEmpty { "COUNT" }
                    val delimAndIdx = block.tertiaryParam.ifEmpty { "|" }
                    val delim = delimAndIdx.substringBefore('|').ifEmpty { "|" }
                    val indexArg = delimAndIdx.substringAfter('|', "0").trim().toIntOrNull() ?: 0
                    val items = if (input.isEmpty()) emptyList() else input.split(delim)
                    val out = when (op) {
                        "FIRST" -> items.firstOrNull().orEmpty()
                        "LAST" -> items.lastOrNull().orEmpty()
                        "GET_INDEX", "INDEX", "GET" -> items.getOrNull(indexArg).orEmpty()
                        "JOIN" -> items.joinToString(",")
                        else -> items.size.toString()
                    }
                    writeOutput(out)
                    detail = "List $op (size=${items.size})"
                    outputPreview = out
                }

                BlockType.UTIL_FILE_APPEND -> {
                    val line = resolve(block.inputTarget)
                    logs.add("[OUT] $line")
                    writeOutput(line)
                    detail = "Appended output note"
                    outputPreview = line
                }
            }

            val stepMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - stepStart).coerceAtLeast(0L)
            steps.add(
                BlockExecutionStep(
                    index = idx + 1,
                    block = block,
                    status = status,
                    detail = detail,
                    durationMs = stepMs,
                    outputPreview = outputPreview.take(120)
                )
            )
        }

        val totalMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - pipelineStart).coerceAtLeast(1L)
        return BlockPipelineResult(
            steps = steps,
            variables = variables,
            captures = captures,
            cookies = cookies,
            logs = logs,
            verdict = winningVerdict,
            lastStatusCode = currentStatusCode,
            lastSourceBody = currentSource,
            lastHeaders = currentHeaders,
            totalDurationMs = totalMs
        )
    }

    private fun resolveSourceVar(
        sourceKey: String,
        currentSource: String,
        variables: Map<String, String>
    ): String {
        val k = sourceKey.trim()
        if (k.isEmpty() || k.equals("<SOURCE>", ignoreCase = true) || k.equals("SOURCE", ignoreCase = true)) {
            return currentSource
        }
        val stripped = k.removePrefix("<").removeSuffix(">")
            .removePrefix("{var:").removeSuffix("}")
            .removePrefix("{cap:").removeSuffix("}")
            .removePrefix("{").removeSuffix("}")
        return variables[stripped]
            ?: variables.entries.firstOrNull { it.key.equals(stripped, ignoreCase = true) }?.value
            ?: k
    }

    private fun evaluateStringCondition(actual: String, operator: String, expected: String): Boolean {
        return when (operator.uppercase(Locale.ROOT)) {
            "EQUALS", "==" -> actual.equals(expected, ignoreCase = true)
            "NOT_EQUALS", "!=" -> !actual.equals(expected, ignoreCase = true)
            "CONTAINS" -> expected.isNotEmpty() && actual.contains(expected, ignoreCase = true)
            "NOT_CONTAINS" -> expected.isNotEmpty() && !actual.contains(expected, ignoreCase = true)
            "CONTAINS_ANY" -> {
                val terms = expected.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                terms.any { actual.contains(it, ignoreCase = true) }
            }
            "CONTAINS_ALL" -> {
                val terms = expected.split(',', '|', ';').map { it.trim() }.filter { it.isNotEmpty() }
                terms.isNotEmpty() && terms.all { actual.contains(it, ignoreCase = true) }
            }
            "STARTS_WITH" -> actual.startsWith(expected, ignoreCase = true)
            "ENDS_WITH" -> actual.endsWith(expected, ignoreCase = true)
            "REGEX", "MATCHES" -> expected.isNotEmpty() && runCatching {
                Regex(expected, RegexOption.IGNORE_CASE).containsMatchIn(actual)
            }.getOrDefault(false)
            else -> expected.isNotEmpty() && actual.contains(expected, ignoreCase = true)
        }
    }

    private fun evaluateSimpleScriptExpression(expr: String): String {
        val trimmed = expr.trim()
        val concatMatch = Regex("""^concat\s*\((.*)\)$""", RegexOption.IGNORE_CASE).find(trimmed)
        if (concatMatch != null) {
            return concatMatch.groupValues[1]
                .split(',')
                .joinToString("") { it.trim().trim('\'', '"') }
        }
        val mathVal = evaluateMathExpression(trimmed)
        if (mathVal != null) {
            return if (mathVal % 1.0 == 0.0) mathVal.roundToLong().toString() else mathVal.toString()
        }
        return trimmed
    }

    /**
     * Pure-Kotlin recursive-descent arithmetic parser (+, -, *, /, %, parentheses).
     */
    internal fun evaluateMathExpression(expression: String): Double? {
        val expr = expression.replace(" ", "")
        if (expr.isEmpty()) return null
        return MathParser(expr).parse()
    }

    private class MathParser(private val expr: String) {
        private var pos = 0
        private fun peek(): Char? = if (pos < expr.length) expr[pos] else null

        fun parse(): Double? {
            val res = parseExpr() ?: return null
            return if (pos == expr.length) res else null
        }

        private fun parseExpr(): Double? {
            var left = parseTerm() ?: return null
            while (true) {
                when (peek()) {
                    '+' -> { pos++; left += parseTerm() ?: return null }
                    '-' -> { pos++; left -= parseTerm() ?: return null }
                    else -> return left
                }
            }
        }

        private fun parseTerm(): Double? {
            var left = parseFactor() ?: return null
            while (true) {
                when (peek()) {
                    '*' -> { pos++; left *= parseFactor() ?: return null }
                    '/' -> {
                        pos++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        left /= divisor
                    }
                    '%' -> {
                        pos++
                        val divisor = parseFactor() ?: return null
                        if (divisor == 0.0) return null
                        left %= divisor
                    }
                    else -> return left
                }
            }
        }

        private fun parseFactor(): Double? {
            return when (peek()) {
                '+' -> { pos++; parseFactor() }
                '-' -> { pos++; parseFactor()?.let { -it } }
                '(' -> {
                    pos++
                    val inside = parseExpr() ?: return null
                    if (peek() != ')') return null
                    pos++
                    inside
                }
                else -> {
                    val start = pos
                    while (pos < expr.length && (expr[pos].isDigit() || expr[pos] == '.')) {
                        pos++
                    }
                    if (start == pos) return null
                    expr.substring(start, pos).toDoubleOrNull()
                }
            }
        }
    }

    private data class SimpleHttpResult(
        val statusCode: Int,
        val body: String,
        val headers: Map<String, String>
    )

    private suspend fun executeBlockHttpCall(
        url: String,
        method: String,
        headersText: String,
        bodyStr: String,
        isMultipart: Boolean,
        multipartSpec: String,
        userAgent: String,
        cookieHeader: String,
        timeoutSeconds: Int,
        followRedirects: Boolean,
        proxyEndpoint: ParsedProxyEndpoint?
    ): Result<SimpleHttpResult> = runCatching {
        val client = NetworkClientProvider.clientWithCustomOptions(
            timeoutSeconds = timeoutSeconds,
            followRedirects = followRedirects,
            proxyEndpoint = proxyEndpoint
        )
        val reqBuilder = Request.Builder().url(url)
        if (userAgent.isNotBlank()) {
            val resolvedUa = if (userAgent.contains("{random_ua}", ignoreCase = true)) {
                CustomConfigStudioEngine.generateRandomUserAgent()
            } else userAgent
            reqBuilder.header("User-Agent", resolvedUa)
        }
        if (cookieHeader.isNotBlank()) {
            reqBuilder.header("Cookie", cookieHeader)
        }
        headersText.lines().map { it.trim() }.filter { it.contains(':') }.forEach { line ->
            val idx = line.indexOf(':')
            reqBuilder.header(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
        }

        if (isMultipart) {
            val mpBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)
            multipartSpec.lines().map { it.trim() }.filter { it.contains('=') }.forEach { line ->
                val idx = line.indexOf('=')
                mpBuilder.addFormDataPart(line.substring(0, idx).trim(), line.substring(idx + 1).trim())
            }
            reqBuilder.post(mpBuilder.build())
        } else {
            when (method) {
                "GET" -> reqBuilder.get()
                "HEAD" -> reqBuilder.head()
                "DELETE" -> if (bodyStr.isNotBlank()) {
                    reqBuilder.delete(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                } else {
                    reqBuilder.delete()
                }
                "PUT" -> reqBuilder.put(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                "PATCH" -> reqBuilder.patch(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
                "POST_FORM" -> reqBuilder.post(bodyStr.toRequestBody("application/x-www-form-urlencoded; charset=utf-8".toMediaType()))
                else -> reqBuilder.post(bodyStr.toRequestBody("application/json; charset=utf-8".toMediaType()))
            }
        }

        val raw = NetworkClientProvider.awaitRawCall(client, reqBuilder.build())
        SimpleHttpResult(raw.statusCode, raw.body, raw.headers)
    }

    private suspend fun queryCaptchaBalance(
        provider: String,
        apiKey: String,
        timeoutSeconds: Int
    ): Result<String> = runCatching {
        val client = NetworkClientProvider.clientWithCustomOptions(
            timeoutSeconds = timeoutSeconds,
            followRedirects = true,
            proxyEndpoint = null
        )
        val lower = provider.lowercase(Locale.ROOT)
        val req = if (lower.contains("capsolver")) {
            val json = """{"clientKey":"$apiKey"}"""
            Request.Builder()
                .url("https://api.capsolver.com/getBalance")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
        } else if (lower.contains("anti")) {
            val json = """{"clientKey":"$apiKey"}"""
            Request.Builder()
                .url("https://api.anti-captcha.com/getBalance")
                .post(json.toRequestBody("application/json".toMediaType()))
                .build()
        } else {
            val encodedKey = URLEncoder.encode(apiKey, "UTF-8")
            Request.Builder()
                .url("https://2captcha.com/res.php?key=$encodedKey&action=getbalance&json=1")
                .get()
                .build()
        }
        val raw = NetworkClientProvider.awaitRawCall(client, req)
        CustomConfigStudioEngine.extractJsonPathValue(raw.body, "balance")
            ?: CustomConfigStudioEngine.extractJsonPathValue(raw.body, "request")
            ?: raw.body.take(64)
    }

    private suspend fun submitCaptchaSolveTask(
        type: BlockType,
        provider: String,
        siteKeyOrImage: String,
        pageUrl: String,
        apiKey: String,
        timeoutSeconds: Int
    ): Result<String> = runCatching {
        val client = NetworkClientProvider.clientWithCustomOptions(
            timeoutSeconds = timeoutSeconds,
            followRedirects = true,
            proxyEndpoint = null
        )
        val lower = provider.lowercase(Locale.ROOT)
        if (lower.contains("capsolver") || lower.contains("anti")) {
            val endpoint = if (lower.contains("capsolver")) {
                "https://api.capsolver.com/createTask"
            } else {
                "https://api.anti-captcha.com/createTask"
            }
            val taskType = when (type) {
                BlockType.CAPTCHA_RECAPTCHA_V2 -> "ReCaptchaV2TaskProxyLess"
                BlockType.CAPTCHA_RECAPTCHA_V3 -> "ReCaptchaV3TaskProxyLess"
                BlockType.CAPTCHA_HCAPTCHA -> "HCaptchaTaskProxyLess"
                BlockType.CAPTCHA_TURNSTILE -> "AntiTurnstileTaskProxyLess"
                BlockType.CAPTCHA_IMAGE -> "ImageToTextTask"
                else -> "FunCaptchaTaskProxyLess"
            }
            val payload = if (type == BlockType.CAPTCHA_IMAGE) {
                """{"clientKey":"$apiKey","task":{"type":"$taskType","body":"$siteKeyOrImage"}}"""
            } else {
                """{"clientKey":"$apiKey","task":{"type":"$taskType","websiteURL":"$pageUrl","websiteKey":"$siteKeyOrImage"}}"""
            }
            val req = Request.Builder()
                .url(endpoint)
                .post(payload.toRequestBody("application/json".toMediaType()))
                .build()
            val raw = NetworkClientProvider.awaitRawCall(client, req)
            CustomConfigStudioEngine.extractJsonPathValue(raw.body, "solution.gRecaptchaResponse")
                ?: CustomConfigStudioEngine.extractJsonPathValue(raw.body, "taskId")
                ?: raw.body.take(80)
        } else {
            val methodParam = when (type) {
                BlockType.CAPTCHA_HCAPTCHA -> "hcaptcha"
                BlockType.CAPTCHA_TURNSTILE -> "turnstile"
                BlockType.CAPTCHA_FUNCAPTCHA -> "funcaptcha"
                BlockType.CAPTCHA_IMAGE -> "base64"
                else -> "userrecaptcha"
            }
            val formStr = buildString {
                append("key=").append(URLEncoder.encode(apiKey, "UTF-8"))
                append("&method=").append(methodParam)
                if (type == BlockType.CAPTCHA_IMAGE) {
                    append("&body=").append(URLEncoder.encode(siteKeyOrImage, "UTF-8"))
                } else {
                    append("&googlekey=").append(URLEncoder.encode(siteKeyOrImage, "UTF-8"))
                    append("&sitekey=").append(URLEncoder.encode(siteKeyOrImage, "UTF-8"))
                    append("&pageurl=").append(URLEncoder.encode(pageUrl, "UTF-8"))
                }
                append("&json=1")
            }
            val req = Request.Builder()
                .url("https://2captcha.com/in.php")
                .post(formStr.toRequestBody("application/x-www-form-urlencoded".toMediaType()))
                .build()
            val raw = NetworkClientProvider.awaitRawCall(client, req)
            CustomConfigStudioEngine.extractJsonPathValue(raw.body, "request")
                ?: raw.body.take(80)
        }
    }

    private suspend fun executeTcpExchange(
        host: String,
        port: Int,
        payload: String,
        timeoutMs: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs
                if (payload.isNotEmpty()) {
                    socket.getOutputStream().write((payload + "\n").toByteArray(Charsets.UTF_8))
                    socket.getOutputStream().flush()
                }
                val buf = ByteArray(1024)
                val read = runCatching { socket.getInputStream().read(buf) }.getOrDefault(-1)
                if (read > 0) String(buf, 0, read, Charsets.UTF_8).trim()
                else "CONNECTED"
            }
        }
    }

    private fun computeDigestHex(algorithm: String, input: String): String = runCatching {
        val bytes = MessageDigest.getInstance(algorithm).digest(input.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
    }.getOrDefault("")

    private fun computeHmacHex(algorithm: String, message: String, secret: String): String = runCatching {
        val mac = Mac.getInstance(algorithm)
        mac.init(SecretKeySpec(secret.toByteArray(Charsets.UTF_8), algorithm))
        val bytes = mac.doFinal(message.toByteArray(Charsets.UTF_8))
        bytes.joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }
    }.getOrDefault("")

    private fun decodeBase64Safe(input: String): String? = runCatching {
        val clean = input.trim().filterNot { it.isWhitespace() }
        if (clean.isEmpty()) return ""
        val bytes = runCatching {
            Base64.getDecoder().decode(clean)
        }.getOrElse {
            decodeBase64Fallback(clean) ?: return null
        }
        String(bytes, Charsets.UTF_8)
    }.getOrNull()

    private fun decodeBase64Fallback(clean: String): ByteArray? {
        if (clean.length % 4 != 0) return null
        val out = ArrayList<Byte>((clean.length * 3) / 4)
        var i = 0
        while (i < clean.length) {
            val c0 = base64CharIndex(clean[i])
            val c1 = base64CharIndex(clean[i + 1])
            val c2 = if (clean[i + 2] == '=') -2 else base64CharIndex(clean[i + 2])
            val c3 = if (clean[i + 3] == '=') -2 else base64CharIndex(clean[i + 3])
            if (c0 < 0 || c1 < 0 || c2 == -1 || c3 == -1) return null
            out.add(((c0 shl 2) or (c1 ushr 4)).toByte())
            if (c2 >= 0) {
                out.add((((c1 and 0x0F) shl 4) or (c2 ushr 2)).toByte())
                if (c3 >= 0) {
                    out.add((((c2 and 0x03) shl 6) or c3).toByte())
                }
            }
            i += 4
        }
        return out.toByteArray()
    }

    private fun base64CharIndex(ch: Char): Int = when (ch) {
        in 'A'..'Z' -> ch - 'A'
        in 'a'..'z' -> ch - 'a' + 26
        in '0'..'9' -> ch - '0' + 52
        '+', '-' -> 62
        '/', '_' -> 63
        else -> -1
    }

    private fun generateRandomAlnum(length: Int): String {
        val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        return buildString(length) {
            repeat(length) { append(chars[Random.nextInt(chars.length)]) }
        }
    }

    private fun parseCookieStringInto(raw: String, jar: MutableMap<String, String>) {
        raw.split(';', '\n').forEach { seg ->
            val trimmed = seg.trim()
            val eq = trimmed.indexOf('=')
            if (eq > 0) {
                val k = trimmed.substring(0, eq).trim()
                val v = trimmed.substring(eq + 1).trim()
                if (k.isNotEmpty()) jar[k] = v
            }
        }
    }

    private fun formatCookieJar(jar: Map<String, String>): String =
        jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
