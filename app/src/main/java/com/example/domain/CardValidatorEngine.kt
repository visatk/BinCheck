package com.example.domain

import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

enum class ResultBucket {
    LIVE,
    DIE,
    UNKNOWN
}

enum class ValidationMode(
    val badgeLabel: String,
    val fixedDelayMs: Long? = null
) {
    LOCAL_DETERMINISTIC("LUHN", fixedDelayMs = null),
    CHKR_CC("CHKR.CC", fixedDelayMs = 10_000L),
    PAYATE_MOCK("PAYATE", fixedDelayMs = 3_000L),
    MULTI_API_AUTO("MULTI API", fixedDelayMs = 5_000L),
    REMOTE_WEBHOOK("CUSTOM HTTP", fixedDelayMs = null);

    val hasFixedDelay: Boolean
        get() = fixedDelayMs != null

    fun resolveDelayMs(userConfiguredDelayMs: Long): Long =
        fixedDelayMs ?: userConfiguredDelayMs.coerceIn(0L, MAX_CUSTOM_DELAY_MS)

    companion object {
        const val MAX_CUSTOM_DELAY_MS = 30_000L
    }
}

enum class CardScheme(
    val displayName: String,
    val validLengths: Set<Int>,
    val cvvLength: Int
) {
    VISA("VISA", setOf(13, 16, 19), 3),
    MASTERCARD("MASTERCARD", setOf(16), 3),
    AMEX("AMEX", setOf(15), 4),
    DISCOVER("DISCOVER", setOf(16, 19), 3),
    JCB("JCB", setOf(16, 17, 18, 19), 3),
    DINERS("DINERS", setOf(14, 16, 19), 3),
    UNIONPAY("UNIONPAY", setOf(16, 17, 18, 19), 3),
    MAESTRO("MAESTRO", setOf(12, 13, 14, 15, 16, 17, 18, 19), 3),
    UNKNOWN("UNKNOWN", setOf(13, 14, 15, 16, 17, 18, 19), 3)
}

data class ValidationRecord(
    val id: String,
    val rawLine: String,
    val normalizedLine: String,
    val pan: String,
    val month: String,
    val year: String,
    val cvv: String,
    val scheme: CardScheme,
    val bucket: ResultBucket,
    val message: String,
    val latencyMs: Long
)

object CardValidatorEngine {

    /**
     * Detects card brand/scheme from IIN/BIN prefix according to ISO/IEC 7812 rules.
     */
    fun detectScheme(panDigits: String): CardScheme {
        val clean = panDigits.filter { it.isDigit() }
        if (clean.isEmpty()) return CardScheme.UNKNOWN

        val p1 = clean.take(1).toIntOrNull() ?: return CardScheme.UNKNOWN
        val p2 = clean.take(2).toIntOrNull() ?: -1
        val p3 = clean.take(3).toIntOrNull() ?: -1
        val p4 = clean.take(4).toIntOrNull() ?: -1
        val p6 = clean.take(6).toIntOrNull() ?: -1

        return when {
            p1 == 4 -> CardScheme.VISA
            p2 in 51..55 || p4 in 2221..2720 -> CardScheme.MASTERCARD
            p2 == 34 || p2 == 37 -> CardScheme.AMEX
            p4 == 6011 || p2 == 65 || p3 in 644..649 || p6 in 622126..622925 -> CardScheme.DISCOVER
            p2 == 62 -> CardScheme.UNIONPAY
            p4 in 3528..3589 -> CardScheme.JCB
            p3 in 300..305 || p2 == 36 || p2 == 38 || p2 == 39 -> CardScheme.DINERS
            p2 == 50 || p2 in 56..69 -> CardScheme.MAESTRO
            else -> CardScheme.UNKNOWN
        }
    }

    /**
     * Standard ISO/IEC 7812 Luhn Mod-10 verification.
     */
    fun verifyLuhn(pan: String): Boolean {
        if (pan.length < 12 || pan.length > 19 || !pan.all { it.isDigit() }) return false
        var sum = 0
        var shouldDouble = false
        for (i in pan.length - 1 downTo 0) {
            var digit = pan[i] - '0'
            if (shouldDouble) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            shouldDouble = !shouldDouble
        }
        return sum % 10 == 0
    }

    /**
     * Solves a PAN containing one or more 'x' or 'X' wildcards so that the resulting PAN
     * satisfies the Luhn Mod-10 checksum equation, even if the last digit is fixed
     * (e.g., 453225005536xxx3).
     */
    fun resolveWildcardPan(pattern: String, random: Random = Random.Default): String {
        val chars = pattern.trim().toCharArray()
        val wildcardIndices = mutableListOf<Int>()
        for (i in chars.indices) {
            if (chars[i] == 'x' || chars[i] == 'X') {
                wildcardIndices.add(i)
            } else if (!chars[i].isDigit()) {
                return pattern
            }
        }
        if (wildcardIndices.isEmpty()) return pattern

        // Fill all wildcards except the last wildcard with random digits 0..9
        val targetIndex = wildcardIndices.last()
        for (i in 0 until wildcardIndices.size - 1) {
            chars[wildcardIndices[i]] = ('0' + random.nextInt(10))
        }

        // Solve the exact digit 0..9 at targetIndex that makes Luhn sum % 10 == 0
        for (candidate in 0..9) {
            chars[targetIndex] = ('0' + candidate)
            val candidateStr = String(chars)
            if (verifyLuhn(candidateStr)) {
                return candidateStr
            }
        }
        return String(chars)
    }

    /**
     * Expands a pipe-delimited template containing 'x'/'X' wildcards (like 453225005536xxx3|08|2028|113)
     * into a list of unique, Luhn-valid records.
     */
    fun expandPatternBatch(
        templateLine: String,
        count: Int,
        random: Random = Random.Default
    ): List<String> {
        val cleanTemplate = templateLine.trim().ifEmpty { "453225005536xxx3|08|2028|113" }
        val parts = cleanTemplate.split('|', '/', ':').map { it.trim() }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val rawPan = parts.getOrNull(0).orEmpty().replace(" ", "").replace("-", "")
        val panPattern = rawPan.ifEmpty { "453225005536xxx3" }
        val mmPattern = parts.getOrNull(1)?.ifEmpty { "xx" } ?: "08"
        val yyyyPattern = parts.getOrNull(2)?.ifEmpty { "xxxx" } ?: (currentYear + 2).toString()
        val schemeForPan = detectScheme(panPattern)
        val defaultCvvMask = "x".repeat(schemeForPan.cvvLength)
        val cvvPattern = parts.getOrNull(3)?.ifEmpty { defaultCvvMask } ?: defaultCvvMask

        val results = LinkedHashSet<String>()
        var attempts = 0
        val maxAttempts = count * 12

        while (results.size < count && attempts < maxAttempts) {
            attempts++
            val resolvedPan = if (panPattern.any { it.equals('x', ignoreCase = true) }) {
                resolveWildcardPan(panPattern, random)
            } else {
                panPattern
            }

            val resolvedMm = if (mmPattern.any { it.equals('x', ignoreCase = true) }) {
                val m = random.nextInt(1, 13)
                m.toString().padStart(2, '0')
            } else {
                mmPattern.padStart(2, '0')
            }

            val resolvedYyyy = if (yyyyPattern.any { it.equals('x', ignoreCase = true) }) {
                (currentYear + random.nextInt(1, 6)).toString()
            } else {
                normalizeYear(yyyyPattern)
            }

            val scheme = detectScheme(resolvedPan)
            val resolvedCvv = if (cvvPattern.any { it.equals('x', ignoreCase = true) }) {
                val chars = cvvPattern.toCharArray()
                for (i in chars.indices) {
                    if (chars[i].equals('x', ignoreCase = true)) {
                        chars[i] = ('0' + random.nextInt(10))
                    }
                }
                String(chars)
            } else if (cvvPattern.isEmpty()) {
                val len = scheme.cvvLength
                (0 until len).joinToString("") { random.nextInt(10).toString() }
            } else {
                cvvPattern
            }

            results.add("$resolvedPan|$resolvedMm|$resolvedYyyy|$resolvedCvv")
        }

        return results.toList()
    }

    fun normalizeYear(rawYear: String): String {
        val clean = rawYear.trim()
        return if (clean.length == 2 && clean.all { it.isDigit() }) {
            "20$clean"
        } else {
            clean
        }
    }

    /**
     * Validates a single raw line deterministically or via a configured remote HTTP endpoint.
     */
    suspend fun validateLine(
        rawLine: String,
        mode: ValidationMode,
        webhookUrl: String,
        autoResolveWildcards: Boolean,
        customHttpConfig: CustomHttpConfig? = null,
        useCacheAndDedup: Boolean = true
    ): ValidationRecord {
        val startNanos = System.nanoTime()
        val trimmed = rawLine.trim()
        val parts = trimmed.split('|', '/', ':', ';').map { it.trim() }.filter { it.isNotEmpty() }

        if (parts.size < 4) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            return ValidationRecord(
                id = "${System.nanoTime()}_${trimmed.hashCode()}",
                rawLine = trimmed,
                normalizedLine = trimmed,
                pan = parts.getOrNull(0) ?: trimmed,
                month = parts.getOrNull(1) ?: "--",
                year = parts.getOrNull(2) ?: "----",
                cvv = parts.getOrNull(3) ?: "---",
                scheme = detectScheme(parts.getOrNull(0) ?: ""),
                bucket = ResultBucket.UNKNOWN,
                message = "Format incomplete (expected PAN|MM|YYYY|CVV)",
                latencyMs = elapsed
            )
        }

        var pan = parts[0].replace(" ", "").replace("-", "")
        var mm = parts[1]
        var yyyy = normalizeYear(parts[2])
        var cvv = parts[3]

        val hasWildcard = pan.any { it.equals('x', ignoreCase = true) } ||
            mm.any { it.equals('x', ignoreCase = true) } ||
            yyyy.any { it.equals('x', ignoreCase = true) } ||
            cvv.any { it.equals('x', ignoreCase = true) }

        if (hasWildcard) {
            if (autoResolveWildcards) {
                val expanded = expandPatternBatch("$pan|$mm|$yyyy|$cvv", 1).firstOrNull()
                if (expanded != null) {
                    val expParts = expanded.split('|')
                    pan = expParts[0]
                    mm = expParts[1]
                    yyyy = expParts[2]
                    cvv = expParts[3]
                }
            } else {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                return ValidationRecord(
                    id = "${System.nanoTime()}_${trimmed.hashCode()}",
                    rawLine = trimmed,
                    normalizedLine = "$pan|$mm|$yyyy|$cvv",
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = detectScheme(pan),
                    bucket = ResultBucket.UNKNOWN,
                    message = "Contains unexpanded 'x' wildcard pattern",
                    latencyMs = elapsed
                )
            }
        }

        val normalizedLine = "$pan|$mm|$yyyy|$cvv"
        val scheme = detectScheme(pan)

        // Step 1: Numeric syntax check
        if (!pan.all { it.isDigit() } || !mm.all { it.isDigit() } || !yyyy.all { it.isDigit() } || !cvv.all { it.isDigit() }) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            return ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = trimmed,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.UNKNOWN,
                message = "Non-numeric characters in fields",
                latencyMs = elapsed
            )
        }

        // Step 2: Check IIN/BIN scheme & length
        if (scheme == CardScheme.UNKNOWN || pan.length !in scheme.validLengths) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            return ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = trimmed,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.DIE,
                message = if (scheme == CardScheme.UNKNOWN) "Unrecognized BIN/IIN prefix" else "Invalid ${scheme.displayName} length (${pan.length})",
                latencyMs = elapsed
            )
        }

        // Step 3: Luhn Mod-10 check
        if (!verifyLuhn(pan)) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            return ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = trimmed,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm,
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.DIE,
                message = "Luhn Mod-10 checksum failed",
                latencyMs = elapsed
            )
        }

        // Step 4: Expiry month and year check
        val monthInt = mm.toIntOrNull() ?: 0
        val yearInt = yyyy.toIntOrNull() ?: 0
        val cal = Calendar.getInstance()
        val currentYear = cal.get(Calendar.YEAR)
        val currentMonth = cal.get(Calendar.MONTH) + 1 // 1..12

        if (monthInt !in 1..12 || yearInt < currentYear || (yearInt == currentYear && monthInt < currentMonth) || yearInt > currentYear + 25) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            val reason = when {
                monthInt !in 1..12 -> "Invalid month ($mm)"
                yearInt > currentYear + 25 -> "Expiry year out of range ($yyyy)"
                else -> "Expired ($mm/$yyyy)"
            }
            return ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = trimmed,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm.padStart(2, '0'),
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.DIE,
                message = reason,
                latencyMs = elapsed
            )
        }

        // Step 5: CVV length & trivial check
        if (cvv.length != scheme.cvvLength) {
            val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
            return ValidationRecord(
                id = "${System.nanoTime()}_${normalizedLine.hashCode()}",
                rawLine = trimmed,
                normalizedLine = normalizedLine,
                pan = pan,
                month = mm.padStart(2, '0'),
                year = yyyy,
                cvv = cvv,
                scheme = scheme,
                bucket = ResultBucket.DIE,
                message = "Invalid ${scheme.displayName} CVV length (${cvv.length}, expected ${scheme.cvvLength})",
                latencyMs = elapsed
            )
        }

        // Step 6: Dispatch to selected Validation API Engine or return Local Luhn Live
        val paddedMm = mm.padStart(2, '0')
        val canonicalLine = "$pan|$paddedMm|$yyyy|$cvv"
        val timeoutSec = customHttpConfig?.timeoutSeconds ?: 15

        return when (mode) {
            ValidationMode.CHKR_CC -> ValidationApiProviders.executeChkr(
                rawLine = trimmed,
                normalizedLine = canonicalLine,
                pan = pan,
                mm = paddedMm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                timeoutSeconds = timeoutSec,
                startNanos = startNanos
            )

            ValidationMode.PAYATE_MOCK -> ValidationApiProviders.executePayate(
                rawLine = trimmed,
                normalizedLine = canonicalLine,
                pan = pan,
                mm = paddedMm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                timeoutSeconds = timeoutSec,
                startNanos = startNanos
            )

            ValidationMode.MULTI_API_AUTO -> ValidationApiProviders.executeMultiApiFailover(
                rawLine = trimmed,
                normalizedLine = canonicalLine,
                pan = pan,
                mm = paddedMm,
                yyyy = yyyy,
                cvv = cvv,
                scheme = scheme,
                timeoutSeconds = timeoutSec,
                startNanos = startNanos
            )

            ValidationMode.REMOTE_WEBHOOK -> {
                val resolvedConfig = (customHttpConfig ?: CustomHttpConfig(url = webhookUrl)).let { cfg ->
                    if (cfg.url.isBlank() && webhookUrl.isNotBlank()) cfg.copy(url = webhookUrl) else cfg
                }
                if (resolvedConfig.url.isBlank()) {
                    val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos)
                    ValidationRecord(
                        id = "${System.nanoTime()}_${canonicalLine.hashCode()}",
                        rawLine = trimmed,
                        normalizedLine = canonicalLine,
                        pan = pan,
                        month = paddedMm,
                        year = yyyy,
                        cvv = cvv,
                        scheme = scheme,
                        bucket = ResultBucket.UNKNOWN,
                        message = "HTTP endpoint URL not configured in Settings",
                        latencyMs = elapsed
                    )
                } else {
                    CustomHttpClient.executeValidation(
                        rawLine = trimmed,
                        normalizedLine = canonicalLine,
                        pan = pan,
                        mm = paddedMm,
                        yyyy = yyyy,
                        cvv = cvv,
                        scheme = scheme,
                        config = resolvedConfig,
                        startNanos = startNanos,
                        useCacheAndDedup = useCacheAndDedup
                    )
                }
            }

            ValidationMode.LOCAL_DETERMINISTIC -> {
                val elapsed = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos).coerceAtLeast(1L)
                ValidationRecord(
                    id = "${System.nanoTime()}_${canonicalLine.hashCode()}",
                    rawLine = trimmed,
                    normalizedLine = canonicalLine,
                    pan = pan,
                    month = paddedMm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    bucket = ResultBucket.LIVE,
                    message = "${scheme.displayName} • Luhn Valid • Exp $paddedMm/$yyyy",
                    latencyMs = elapsed
                )
            }
        }
    }
}
