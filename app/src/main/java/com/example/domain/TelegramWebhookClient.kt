package com.example.domain

import com.example.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class TelegramWebhookConfig(
    val enabled: Boolean = false,
    val chatId: String = "",
    val notifyLiveOnly: Boolean = true
)

data class TelegramProbeResult(
    val success: Boolean,
    val botUsername: String = "",
    val detectedChatId: String? = null,
    val message: String
)

object TelegramWebhookClient {

    private const val PLACEHOLDER_TOKEN = "MY_TELEGRAM_BOT_TOKEN"
    private const val TELEGRAM_API_BASE = "https://api.telegram.org"
    private const val TELEGRAM_HOST = "api.telegram.org"

    private val detectDeduplicator = RequestDeduplicator<String, TelegramProbeResult>()

    fun resolveConfiguredBotToken(): String {
        val raw = runCatching { BuildConfig.TELEGRAM_BOT_TOKEN }.getOrDefault("").trim()
        return if (raw.isEmpty() || raw == PLACEHOLDER_TOKEN) "" else raw
    }

    fun hasConfiguredToken(): Boolean = resolveConfiguredBotToken().isNotEmpty()

    suspend fun verifyAndDetectChat(
        botToken: String = resolveConfiguredBotToken()
    ): TelegramProbeResult {
        val token = botToken.trim()
        if (token.isEmpty()) {
            return TelegramProbeResult(
                success = false,
                message = "TELEGRAM_BOT_TOKEN is not set. Add it in AI Studio Secrets."
            )
        }

        val (result, wasCoalesced) = detectDeduplicator.getOrExecute("detect:$token") {
            val client = NetworkClientProvider.baseClient
            val getMeReq = Request.Builder()
                .url("$TELEGRAM_API_BASE/bot$token/getMe")
                .get()
                .build()

            val getMeRes = RetryAndBackoffPolicy.executeWithExponentialBackoff(
                host = TELEGRAM_HOST,
                maxRetries = 1,
                baseDelayMs = 450L,
                maxDelayMs = 4_000L,
                circuitBreaker = NetworkClientProvider.circuitBreaker,
                telemetry = NetworkClientProvider.telemetry,
                callBlock = { NetworkClientProvider.awaitRawCall(client, getMeReq) },
                onResult = { code, body, _ -> code to body },
                onCircuitOpen = { remainingMs ->
                    503 to """{"description":"Telegram cooldown ${(remainingMs / 1000L).coerceAtLeast(1L)}s"}"""
                },
                onFailure = { reason, _ -> -1 to reason }
            )

            if (getMeRes.first !in 200..299) {
                val msg = if (getMeRes.first == -1) {
                    "Telegram API error: ${getMeRes.second}"
                } else {
                    "Bot auth failed (HTTP ${getMeRes.first}). Check TELEGRAM_BOT_TOKEN."
                }
                return@getOrExecute TelegramProbeResult(success = false, message = msg)
            }

            val botUsername = parseBotUsername(getMeRes.second)

            val updatesReq = Request.Builder()
                .url("$TELEGRAM_API_BASE/bot$token/getUpdates?limit=15")
                .get()
                .build()

            val detectedChatId = RetryAndBackoffPolicy.executeWithExponentialBackoff(
                host = TELEGRAM_HOST,
                maxRetries = 1,
                baseDelayMs = 450L,
                maxDelayMs = 4_000L,
                circuitBreaker = NetworkClientProvider.circuitBreaker,
                telemetry = NetworkClientProvider.telemetry,
                callBlock = { NetworkClientProvider.awaitRawCall(client, updatesReq) },
                onResult = { code, body, _ ->
                    if (code in 200..299) parseLatestChatId(body) else null
                },
                onCircuitOpen = { null },
                onFailure = { _, _ -> null }
            )

            val msg = if (detectedChatId != null) {
                "Connected @$botUsername • Detected Chat ID: $detectedChatId"
            } else {
                "Connected @$botUsername • Send /start to @$botUsername in Telegram, then tap Detect Chat ID."
            }

            TelegramProbeResult(
                success = true,
                botUsername = botUsername,
                detectedChatId = detectedChatId,
                message = msg
            )
        }
        if (wasCoalesced) {
            NetworkClientProvider.telemetry.recordDeduplicatedSaved()
        }
        return result
    }

    suspend fun sendTestMessage(
        chatId: String,
        botToken: String = resolveConfiguredBotToken()
    ): TelegramProbeResult {
        val token = botToken.trim()
        val targetChatId = chatId.trim()
        if (token.isEmpty()) {
            return TelegramProbeResult(
                success = false,
                message = "TELEGRAM_BOT_TOKEN missing in AI Studio Secrets."
            )
        }
        if (targetChatId.isEmpty()) {
            return TelegramProbeResult(
                success = false,
                message = "Enter a Telegram Chat ID or tap Detect Chat ID first."
            )
        }

        val text = buildString {
            appendLine("<b>BinCheck Webhook Connected</b>")
            appendLine("Bot: <code>@SandBoxAutoBot</code>")
            appendLine("Chat ID: <code>${escapeHtml(targetChatId)}</code>")
            append("Status: Ready to deliver batch validation alerts.")
        }

        val req = buildSendMessageRequest(
            botToken = token,
            chatId = targetChatId,
            htmlText = text
        )

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = TELEGRAM_HOST,
            maxRetries = 2,
            baseDelayMs = 500L,
            maxDelayMs = 8_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(NetworkClientProvider.baseClient, req) },
            onResult = { code, body, _ ->
                if (code in 200..299) {
                    TelegramProbeResult(
                        success = true,
                        detectedChatId = targetChatId,
                        message = "Test alert delivered to Chat ID $targetChatId"
                    )
                } else {
                    TelegramProbeResult(
                        success = false,
                        message = "Telegram HTTP $code: ${parseTelegramErrorDescription(body)}"
                    )
                }
            },
            onCircuitOpen = { remainingMs ->
                TelegramProbeResult(
                    success = false,
                    message = "Telegram rate-limit cooldown (${(remainingMs / 1000L).coerceAtLeast(1L)}s)"
                )
            },
            onFailure = { reason, _ ->
                TelegramProbeResult(
                    success = false,
                    message = "Webhook failed: $reason"
                )
            }
        )
    }

    suspend fun sendValidationNotification(
        record: ValidationRecord,
        engineLabel: String,
        chatId: String,
        binInfo: BinInfo? = null,
        botToken: String = resolveConfiguredBotToken()
    ): Boolean {
        val token = botToken.trim()
        val targetChatId = chatId.trim()
        if (token.isEmpty() || targetChatId.isEmpty()) return false

        val statusLabel = when (record.bucket) {
            ResultBucket.LIVE -> "LIVE"
            ResultBucket.DIE -> "DIE"
            ResultBucket.UNKNOWN -> "UNKNOWN"
        }

        val html = buildString {
            appendLine("<b>BinCheck • $statusLabel</b>")
            appendLine("<code>${escapeHtml(record.normalizedLine)}</code>")
            appendLine("Scheme: <b>${escapeHtml(record.scheme.displayName)}</b> | Engine: ${escapeHtml(engineLabel)}")
            appendLine("Verdict: ${escapeHtml(record.message)} (${record.latencyMs}ms)")
            if (binInfo != null) {
                append("BIN ${escapeHtml(binInfo.bin)}: ${escapeHtml(binInfo.compactSummary())}")
            }
        }.trim()

        return dispatchHtmlMessage(token = token, chatId = targetChatId, htmlText = html)
    }

    /**
     * Dispatches multiple validation hits in a single compact Telegram message to avoid
     * HTTP 429 flood throttling and save radio/battery under high-traffic batch runs.
     */
    suspend fun sendBatchDigest(
        records: List<ValidationRecord>,
        engineLabel: String,
        chatId: String,
        botToken: String = resolveConfiguredBotToken()
    ): Boolean {
        if (records.isEmpty()) return true
        if (records.size == 1) {
            val single = records.first()
            val binInfo = BinLookupService.getCached(single.pan)
            return sendValidationNotification(
                record = single,
                engineLabel = engineLabel,
                chatId = chatId,
                binInfo = binInfo,
                botToken = botToken
            )
        }
        val token = botToken.trim()
        val targetChatId = chatId.trim()
        if (token.isEmpty() || targetChatId.isEmpty()) return false

        val liveCount = records.count { it.bucket == ResultBucket.LIVE }
        val dieCount = records.count { it.bucket == ResultBucket.DIE }
        val unknownCount = records.size - liveCount - dieCount

        val html = buildString {
            appendLine("<b>BinCheck Batch Digest (${records.size} items)</b>")
            appendLine("Engine: <b>${escapeHtml(engineLabel)}</b> | Live: $liveCount • Die: $dieCount • Unk: $unknownCount")
            appendLine()
            records.take(15).forEach { rec ->
                val tag = when (rec.bucket) {
                    ResultBucket.LIVE -> "LIVE"
                    ResultBucket.DIE -> "DIE"
                    ResultBucket.UNKNOWN -> "UNK"
                }
                appendLine("• <b>[$tag]</b> <code>${escapeHtml(rec.normalizedLine)}</code> (${escapeHtml(rec.scheme.displayName)})")
            }
            if (records.size > 15) {
                append("<i>...and ${records.size - 15} more record(s)</i>")
            }
        }.trim()

        return dispatchHtmlMessage(token = token, chatId = targetChatId, htmlText = html)
    }

    private suspend fun dispatchHtmlMessage(
        token: String,
        chatId: String,
        htmlText: String
    ): Boolean {
        val request = buildSendMessageRequest(
            botToken = token,
            chatId = chatId,
            htmlText = htmlText
        )

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = TELEGRAM_HOST,
            maxRetries = 2,
            baseDelayMs = 600L,
            maxDelayMs = 10_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(NetworkClientProvider.baseClient, request) },
            onResult = { code, _, _ -> code in 200..299 },
            onCircuitOpen = { false },
            onFailure = { _, _ -> false }
        )
    }

    internal fun buildSendMessageRequest(
        botToken: String,
        chatId: String,
        htmlText: String
    ): Request {
        val payload = JSONObject().apply {
            put("chat_id", chatId.trim())
            put("text", htmlText)
            put("parse_mode", "HTML")
            put("disable_web_page_preview", true)
        }.toString()

        return Request.Builder()
            .url("$TELEGRAM_API_BASE/bot${botToken.trim()}/sendMessage")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
    }

    internal fun parseBotUsername(bodyStr: String): String {
        return runCatching {
            val root = JSONObject(bodyStr)
            root.optJSONObject("result")?.optString("username", "").orEmpty().ifBlank { "SandBoxAutoBot" }
        }.getOrDefault("SandBoxAutoBot")
    }

    internal fun parseLatestChatId(updatesBody: String): String? {
        return runCatching {
            val root = JSONObject(updatesBody)
            if (!root.optBoolean("ok", false)) return null
            val array = root.optJSONArray("result") ?: return null
            for (i in array.length() - 1 downTo 0) {
                val update = array.optJSONObject(i) ?: continue
                val msgObj = update.optJSONObject("message")
                    ?: update.optJSONObject("channel_post")
                    ?: update.optJSONObject("my_chat_member")
                    ?: update.optJSONObject("callback_query")?.optJSONObject("message")
                val chatObj = msgObj?.optJSONObject("chat")
                if (chatObj != null && chatObj.has("id")) {
                    return chatObj.getLong("id").toString()
                }
            }
            null
        }.getOrNull()
    }

    private fun parseTelegramErrorDescription(bodyStr: String): String {
        return runCatching {
            JSONObject(bodyStr).optString("description", "").ifBlank { bodyStr.take(120) }
        }.getOrDefault(bodyStr.take(120).ifBlank { "Unknown Telegram error" })
    }

    private fun escapeHtml(raw: String): String =
        raw.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
}
