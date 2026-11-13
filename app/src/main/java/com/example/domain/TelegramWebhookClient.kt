package com.example.domain

import com.example.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .writeTimeout(12, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()
    }

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

        val getMeReq = Request.Builder()
            .url("$TELEGRAM_API_BASE/bot$token/getMe")
            .get()
            .build()

        val getMeRes = runCatching { awaitCall(getMeReq) }.getOrElse { err ->
            return TelegramProbeResult(
                success = false,
                message = "Telegram API error: ${err.localizedMessage ?: err.javaClass.simpleName}"
            )
        }

        if (getMeRes.first !in 200..299) {
            return TelegramProbeResult(
                success = false,
                message = "Bot auth failed (HTTP ${getMeRes.first}). Check TELEGRAM_BOT_TOKEN."
            )
        }

        val botUsername = parseBotUsername(getMeRes.second)

        val updatesReq = Request.Builder()
            .url("$TELEGRAM_API_BASE/bot$token/getUpdates?limit=15")
            .get()
            .build()

        val detectedChatId = runCatching {
            val updatesRes = awaitCall(updatesReq)
            if (updatesRes.first in 200..299) parseLatestChatId(updatesRes.second) else null
        }.getOrNull()

        val msg = if (detectedChatId != null) {
            "Connected @$botUsername • Detected Chat ID: $detectedChatId"
        } else {
            "Connected @$botUsername • Send /start to @$botUsername in Telegram, then tap Detect Chat ID."
        }

        return TelegramProbeResult(
            success = true,
            botUsername = botUsername,
            detectedChatId = detectedChatId,
            message = msg
        )
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

        return runCatching {
            val (code, body) = awaitCall(req)
            if (code in 200..299) {
                TelegramProbeResult(
                    success = true,
                    detectedChatId = targetChatId,
                    message = "Test alert delivered to Chat ID $targetChatId"
                )
            } else {
                val desc = parseTelegramErrorDescription(body)
                TelegramProbeResult(
                    success = false,
                    message = "Telegram HTTP $code: $desc"
                )
            }
        }.getOrElse { err ->
            TelegramProbeResult(
                success = false,
                message = "Webhook failed: ${err.localizedMessage ?: err.javaClass.simpleName}"
            )
        }
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

        val request = buildSendMessageRequest(
            botToken = token,
            chatId = targetChatId,
            htmlText = html
        )

        return runCatching {
            val (code, _) = awaitCall(request)
            code in 200..299
        }.getOrDefault(false)
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

    private suspend fun awaitCall(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = okHttpClient.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(e)
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { res ->
                        if (continuation.isActive) {
                            try {
                                continuation.resume(res.code to res.body?.string().orEmpty())
                            } catch (t: Throwable) {
                                continuation.resumeWithException(t)
                            }
                        }
                    }
                }
            })
        }
}
