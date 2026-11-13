package com.example.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class BinInfo(
    val bin: String,
    val scheme: String,
    val type: String = "",
    val brand: String = "",
    val bankName: String = "",
    val bankPhone: String = "",
    val bankUrl: String = "",
    val countryName: String = "",
    val countryCode: String = "",
    val countryEmoji: String = "",
    val currency: String = "",
    val isPrepaid: Boolean? = null,
    val source: String = "binlist.net",
    val isNetworkVerified: Boolean = true
) {
    fun compactSummary(): String {
        val parts = mutableListOf<String>()
        val countryPart = listOf(countryEmoji, countryCode.ifBlank { countryName })
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (countryPart.isNotBlank()) parts.add(countryPart)

        val typeBrand = listOf(type, brand)
            .filter { it.isNotBlank() }
            .joinToString(" • ")
        if (typeBrand.isNotBlank()) parts.add(typeBrand)

        if (bankName.isNotBlank()) parts.add(bankName)

        return if (parts.isNotEmpty()) {
            parts.joinToString(" | ")
        } else {
            "$scheme • BIN $bin"
        }
    }

    fun fullClipboardText(): String = buildString {
        appendLine("BIN: $bin")
        appendLine("Scheme: ${scheme.ifBlank { "UNKNOWN" }}")
        if (type.isNotBlank()) appendLine("Type: $type")
        if (brand.isNotBlank()) appendLine("Tier/Brand: $brand")
        if (isPrepaid != null) appendLine("Prepaid: ${if (isPrepaid) "Yes" else "No"}")
        if (bankName.isNotBlank()) appendLine("Issuer/Bank: $bankName")
        if (bankPhone.isNotBlank()) appendLine("Bank Phone: $bankPhone")
        if (bankUrl.isNotBlank()) appendLine("Bank URL: $bankUrl")
        if (countryName.isNotBlank() || countryCode.isNotBlank()) {
            appendLine("Country: ${listOf(countryEmoji, countryName, countryCode).filter { it.isNotBlank() }.joinToString(" ")}")
        }
        if (currency.isNotBlank()) appendLine("Currency: $currency")
        append("Source: $source")
    }
}

object BinLookupService {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(8, TimeUnit.SECONDS)
            .callTimeout(12, TimeUnit.SECONDS)
            .build()
    }

    private val cache = ConcurrentHashMap<String, BinInfo>()
    private val inFlightLocks = ConcurrentHashMap<String, Mutex>()

    fun extractBin6(panOrBin: String): String {
        return panOrBin.filter { it.isDigit() }.take(6)
    }

    fun getCached(panOrBin: String): BinInfo? {
        val bin6 = extractBin6(panOrBin)
        if (bin6.length < 6) return null
        return cache[bin6]
    }

    fun cacheVerifiedBin(info: BinInfo) {
        if (info.bin.length == 6 && info.isNetworkVerified) {
            cache[info.bin] = info
        }
    }

    suspend fun lookupBin(
        panOrBin: String,
        forceRefresh: Boolean = false
    ): BinInfo? = withContext(Dispatchers.IO) {
        val bin6 = extractBin6(panOrBin)
        if (bin6.length < 6) return@withContext null

        if (!forceRefresh) {
            cache[bin6]?.let { cached ->
                if (cached.isNetworkVerified) return@withContext cached
            }
        }

        val lock = inFlightLocks.getOrPut(bin6) { Mutex() }
        lock.withLock {
            if (!forceRefresh) {
                cache[bin6]?.let { cached ->
                    if (cached.isNetworkVerified) return@withContext cached
                }
            }

            // Primary provider: lookup.binlist.net
            val binlistResult = fetchFromBinlist(bin6)
            if (binlistResult != null) {
                cache[bin6] = binlistResult
                return@withContext binlistResult
            }

            // Secondary provider: data.handyapi.com
            val handyResult = fetchFromHandyApi(bin6)
            if (handyResult != null) {
                cache[bin6] = handyResult
                return@withContext handyResult
            }

            // Offline / rate-limited fallback using deterministic ISO/IEC 7812 IIN scheme rules
            val localFallback = buildLocalFallback(bin6)
            cache.putIfAbsent(bin6, localFallback)
            cache[bin6] ?: localFallback
        }
    }

    private suspend fun fetchFromBinlist(bin6: String): BinInfo? {
        return runCatching {
            val request = Request.Builder()
                .url("https://lookup.binlist.net/$bin6")
                .header("Accept-Version", "3")
                .header("Accept", "application/json")
                .get()
                .build()

            val (code, body) = awaitCall(request)
            if (code in 200..299 && body.isNotBlank()) {
                parseBinlistJson(bin6, body)
            } else {
                null
            }
        }.getOrNull()
    }

    private suspend fun fetchFromHandyApi(bin6: String): BinInfo? {
        return runCatching {
            val request = Request.Builder()
                .url("https://data.handyapi.com/bin/$bin6")
                .header("Accept", "application/json")
                .get()
                .build()

            val (code, body) = awaitCall(request)
            if (code in 200..299 && body.isNotBlank()) {
                parseHandyApiJson(bin6, body)
            } else {
                null
            }
        }.getOrNull()
    }

    internal fun parseBinlistJson(bin6: String, jsonText: String): BinInfo? {
        return runCatching {
            val json = JSONObject(jsonText)
            val detectedScheme = CardValidatorEngine.detectScheme(bin6).displayName
            val scheme = json.optString("scheme", "").trim().uppercase().ifEmpty { detectedScheme }
            val type = json.optString("type", "").trim().uppercase()
            val brand = json.optString("brand", "").trim().uppercase()
            val prepaid = if (json.has("prepaid") && !json.isNull("prepaid")) {
                json.optBoolean("prepaid")
            } else {
                null
            }

            val countryObj = json.optJSONObject("country")
            val countryName = countryObj?.optString("name", "")?.trim().orEmpty()
            val countryCode = countryObj?.optString("alpha2", "")?.trim()?.uppercase().orEmpty()
            val rawEmoji = countryObj?.optString("emoji", "")?.trim().orEmpty()
            val countryEmoji = rawEmoji.ifEmpty { countryCodeToEmoji(countryCode) }
            val currency = countryObj?.optString("currency", "")?.trim()?.uppercase().orEmpty()

            val bankObj = json.optJSONObject("bank")
            val bankName = bankObj?.optString("name", "")?.trim().orEmpty()
            val bankPhone = bankObj?.optString("phone", "")?.trim().orEmpty()
            val bankUrl = bankObj?.optString("url", "")?.trim().orEmpty()

            BinInfo(
                bin = bin6,
                scheme = scheme,
                type = type,
                brand = brand,
                bankName = bankName,
                bankPhone = bankPhone,
                bankUrl = bankUrl,
                countryName = countryName,
                countryCode = countryCode,
                countryEmoji = countryEmoji,
                currency = currency,
                isPrepaid = prepaid,
                source = "binlist.net",
                isNetworkVerified = true
            )
        }.getOrNull()
    }

    internal fun parseHandyApiJson(bin6: String, jsonText: String): BinInfo? {
        return runCatching {
            val json = JSONObject(jsonText)
            val status = json.optString("Status", "").trim()
            if (status.isNotEmpty() && !status.equals("SUCCESS", ignoreCase = true)) {
                return@runCatching null
            }

            val detectedScheme = CardValidatorEngine.detectScheme(bin6).displayName
            val scheme = json.optString("Scheme", "").trim().uppercase().ifEmpty { detectedScheme }
            val type = json.optString("Type", "").trim().uppercase()
            val brand = json.optString("CardTier", "").trim().uppercase()
            val bankName = json.optString("Issuer", "").trim()

            val countryObj = json.optJSONObject("Country")
            val countryName = countryObj?.optString("Name", "")?.trim().orEmpty()
            val countryCode = countryObj?.optString("A2", "")?.trim()?.uppercase().orEmpty()
            val countryEmoji = countryCodeToEmoji(countryCode)

            BinInfo(
                bin = bin6,
                scheme = scheme,
                type = type,
                brand = brand,
                bankName = bankName,
                countryName = countryName,
                countryCode = countryCode,
                countryEmoji = countryEmoji,
                isPrepaid = type.equals("PREPAID", ignoreCase = true),
                source = "handyapi.com",
                isNetworkVerified = true
            )
        }.getOrNull()
    }

    internal fun buildLocalFallback(bin6: String): BinInfo {
        val scheme = CardValidatorEngine.detectScheme(bin6)
        return BinInfo(
            bin = bin6,
            scheme = scheme.displayName,
            type = "",
            brand = "ISO/IEC 7812",
            bankName = "Issuer lookup offline / rate-limited",
            source = "Local IIN",
            isNetworkVerified = false
        )
    }

    internal fun countryCodeToEmoji(countryCode: String): String {
        val clean = countryCode.trim().uppercase()
        if (clean.length != 2 || !clean.all { it in 'A'..'Z' }) return ""
        val first = Character.codePointAt(clean, 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(clean, 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    private suspend fun awaitCall(request: Request): Pair<Int, String> =
        suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
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
