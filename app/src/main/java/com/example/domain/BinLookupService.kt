package com.example.domain

import com.example.data.local.BinCacheDao
import com.example.data.local.BinCacheEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    private val VERIFIED_MEM_TTL_MS = TimeUnit.HOURS.toMillis(24)
    private val VERIFIED_DISK_TTL_MS = TimeUnit.DAYS.toMillis(7)
    private val FALLBACK_MEM_TTL_MS = TimeUnit.MINUTES.toMillis(5)

    private val memoryCache = TtlLruCache<String, BinInfo>(maxEntries = 500)
    private val deduplicator = RequestDeduplicator<String, BinInfo?>()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var binCacheDao: BinCacheDao? = null
    private val hasPrunedThisSession = AtomicBoolean(false)

    fun attachDao(dao: BinCacheDao) {
        binCacheDao = dao
    }

    suspend fun pruneExpiredIfNeeded() {
        val dao = binCacheDao ?: return
        if (hasPrunedThisSession.compareAndSet(false, true)) {
            runCatching { dao.pruneExpired(System.currentTimeMillis()) }
        }
    }

    fun extractBin6(panOrBin: String): String {
        return panOrBin.filter { it.isDigit() }.take(6)
    }

    fun getCached(panOrBin: String): BinInfo? {
        val bin6 = extractBin6(panOrBin)
        if (bin6.length < 6) return null
        return memoryCache.get(bin6)
    }

    fun cacheVerifiedBin(info: BinInfo) {
        if (info.bin.length == 6 && info.isNetworkVerified) {
            memoryCache.put(info.bin, info, VERIFIED_MEM_TTL_MS)
            val dao = binCacheDao
            if (dao != null) {
                ioScope.launch {
                    runCatching {
                        dao.upsertBin(BinCacheEntity.fromBinInfo(info, VERIFIED_DISK_TTL_MS))
                    }
                }
            }
        }
    }

    suspend fun clearAllCaches() {
        memoryCache.clear()
        binCacheDao?.let { dao ->
            runCatching { dao.clearAll() }
        }
    }

    suspend fun lookupBin(
        panOrBin: String,
        forceRefresh: Boolean = false
    ): BinInfo? = withContext(Dispatchers.IO) {
        val bin6 = extractBin6(panOrBin)
        if (bin6.length < 6) return@withContext null

        if (!forceRefresh) {
            memoryCache.get(bin6)?.let { cached ->
                if (cached.isNetworkVerified) {
                    NetworkClientProvider.telemetry.recordCacheHit()
                    return@withContext cached
                }
            }
            val diskCached = binCacheDao?.let { dao ->
                pruneExpiredIfNeeded()
                runCatching { dao.getValidBin(bin6, System.currentTimeMillis())?.toBinInfo() }.getOrNull()
            }
            if (diskCached != null && diskCached.isNetworkVerified) {
                memoryCache.put(bin6, diskCached, VERIFIED_MEM_TTL_MS)
                NetworkClientProvider.telemetry.recordCacheHit()
                return@withContext diskCached
            }
        }

        val dedupKey = if (forceRefresh) "refresh:$bin6" else bin6
        val (result, wasCoalesced) = deduplicator.getOrExecute(dedupKey) {
            if (!forceRefresh) {
                memoryCache.get(bin6)?.let { cached ->
                    if (cached.isNetworkVerified) {
                        NetworkClientProvider.telemetry.recordCacheHit()
                        return@getOrExecute cached
                    }
                }
            }

            // Primary provider: lookup.binlist.net (protected by circuit breaker & backoff)
            val binlistResult = fetchFromBinlist(bin6)
            if (binlistResult != null) {
                cacheVerifiedBin(binlistResult)
                return@getOrExecute binlistResult
            }

            // Secondary provider: data.handyapi.com
            val handyResult = fetchFromHandyApi(bin6)
            if (handyResult != null) {
                cacheVerifiedBin(handyResult)
                return@getOrExecute handyResult
            }

            // Offline / rate-limited fallback cached briefly (5m) so recovery is prompt
            val localFallback = buildLocalFallback(bin6)
            if (memoryCache.get(bin6) == null) {
                memoryCache.put(bin6, localFallback, FALLBACK_MEM_TTL_MS)
            }
            memoryCache.get(bin6) ?: localFallback
        }

        if (wasCoalesced) {
            NetworkClientProvider.telemetry.recordDeduplicatedSaved()
        }
        result
    }

    private suspend fun fetchFromBinlist(bin6: String): BinInfo? {
        val client = NetworkClientProvider.clientWithTimeout(8)
        val request = Request.Builder()
            .url("https://lookup.binlist.net/$bin6")
            .header("Accept-Version", "3")
            .header("Accept", "application/json")
            .get()
            .build()

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = "lookup.binlist.net",
            maxRetries = 1,
            baseDelayMs = 450L,
            maxDelayMs = 3_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(client, request) },
            onResult = { code, body, _ ->
                if (code in 200..299 && body.isNotBlank()) {
                    parseBinlistJson(bin6, body)
                } else {
                    null
                }
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
    }

    private suspend fun fetchFromHandyApi(bin6: String): BinInfo? {
        val client = NetworkClientProvider.clientWithTimeout(8)
        val request = Request.Builder()
            .url("https://data.handyapi.com/bin/$bin6")
            .header("Accept", "application/json")
            .get()
            .build()

        return RetryAndBackoffPolicy.executeWithExponentialBackoff(
            host = "data.handyapi.com",
            maxRetries = 1,
            baseDelayMs = 450L,
            maxDelayMs = 3_000L,
            circuitBreaker = NetworkClientProvider.circuitBreaker,
            telemetry = NetworkClientProvider.telemetry,
            callBlock = { NetworkClientProvider.awaitRawCall(client, request) },
            onResult = { code, body, _ ->
                if (code in 200..299 && body.isNotBlank()) {
                    parseHandyApiJson(bin6, body)
                } else {
                    null
                }
            },
            onCircuitOpen = { null },
            onFailure = { _, _ -> null }
        )
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
}
