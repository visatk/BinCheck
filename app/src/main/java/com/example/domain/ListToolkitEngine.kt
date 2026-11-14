package com.example.domain

import java.util.Calendar
import java.util.Locale
import kotlin.random.Random

enum class DedupMode(val label: String) {
    NONE("Keep All"),
    BY_PAN("Unique PAN"),
    BY_FULL_RECORD("Exact Tuple")
}

enum class OutputFormat(val label: String, val example: String) {
    PIPE_YYYY("PAN|MM|YYYY|CVV", "4532...|08|2028|113"),
    PIPE_YY("PAN|MM|YY|CVV", "4532...|08|28|113"),
    PIPE_SLASH_EXP("PAN|MM/YY|CVV", "4532...|08/28|113"),
    COLON("PAN:MM:YYYY:CVV", "4532...:08:2028:113"),
    SLASH("PAN/MM/YYYY/CVV", "4532.../08/2028/113"),
    CSV("PAN,MM,YYYY,CVV", "4532...,08,2028,113"),
    PAN_ONLY("PAN Only", "4532250055360003"),
    BIN_ONLY("Unique BIN6", "453225"),
    EMAIL_PASS("email:pass", "alex@domain.com:Pass123!")
}

enum class ListSortOrder(val label: String) {
    KEEP_ORDER("Original"),
    BY_BIN_ASC("PAN / BIN ↑"),
    BY_EXPIRY_DESC("Expiry ↓"),
    BY_SCHEME("Scheme"),
    SHUFFLE("Shuffle")
}

enum class ToolkitPreset(val label: String, val summary: String) {
    FULL_CLEAN("Full Clean", "Extract, dedupe PAN, drop expired & bad Luhn"),
    DEDUPE_FORMAT("Dedupe & Format", "Keep valid syntax, remove dupes & normalize"),
    EXTRACT_PANS("Extract PANs", "Pull clean unique card numbers from messy logs"),
    EXTRACT_BINS("Unique BINs", "Extract unique 6-digit BINs from list"),
    EXTRACT_EMAIL_PASS("Email:Pass", "Extract clean unique email:pass combos from dumps/logs")
}

data class ListToolkitOptions(
    val dedupMode: DedupMode = DedupMode.BY_PAN,
    val removeExpired: Boolean = true,
    val removeInvalidLuhn: Boolean = true,
    val removeInvalidCvvOrLength: Boolean = false,
    val dropIncomplete: Boolean = true,
    val removeUnparseable: Boolean = true,
    val solveWildcards: Boolean = false,
    val outputFormat: OutputFormat = OutputFormat.PIPE_YYYY,
    val selectedSchemes: Set<CardScheme> = emptySet(),
    val includeBinPrefixes: String = "",
    val excludeBinPrefixes: String = "",
    val minExpiryYear: Int? = null,
    val sortOrder: ListSortOrder = ListSortOrder.KEEP_ORDER,
    val limitCount: Int? = null
) {
    companion object {
        fun fromPreset(preset: ToolkitPreset): ListToolkitOptions = when (preset) {
            ToolkitPreset.FULL_CLEAN -> ListToolkitOptions(
                dedupMode = DedupMode.BY_PAN,
                removeExpired = true,
                removeInvalidLuhn = true,
                removeInvalidCvvOrLength = false,
                dropIncomplete = true,
                removeUnparseable = true,
                solveWildcards = false,
                outputFormat = OutputFormat.PIPE_YYYY
            )

            ToolkitPreset.DEDUPE_FORMAT -> ListToolkitOptions(
                dedupMode = DedupMode.BY_FULL_RECORD,
                removeExpired = false,
                removeInvalidLuhn = false,
                removeInvalidCvvOrLength = false,
                dropIncomplete = true,
                removeUnparseable = true,
                solveWildcards = false,
                outputFormat = OutputFormat.PIPE_YYYY
            )

            ToolkitPreset.EXTRACT_PANS -> ListToolkitOptions(
                dedupMode = DedupMode.BY_PAN,
                removeExpired = false,
                removeInvalidLuhn = false,
                removeInvalidCvvOrLength = false,
                dropIncomplete = false,
                removeUnparseable = true,
                solveWildcards = false,
                outputFormat = OutputFormat.PAN_ONLY
            )

            ToolkitPreset.EXTRACT_BINS -> ListToolkitOptions(
                dedupMode = DedupMode.BY_PAN,
                removeExpired = false,
                removeInvalidLuhn = false,
                removeInvalidCvvOrLength = false,
                dropIncomplete = false,
                removeUnparseable = true,
                solveWildcards = false,
                outputFormat = OutputFormat.BIN_ONLY
            )

            ToolkitPreset.EXTRACT_EMAIL_PASS -> ListToolkitOptions(
                dedupMode = DedupMode.BY_FULL_RECORD,
                removeExpired = false,
                removeInvalidLuhn = false,
                removeInvalidCvvOrLength = false,
                dropIncomplete = false,
                removeUnparseable = true,
                solveWildcards = false,
                outputFormat = OutputFormat.EMAIL_PASS
            )
        }
    }
}

data class ToolkitTransformResult(
    val outputText: String,
    val totalInputLines: Int = 0,
    val outputCount: Int = 0,
    val removedDuplicates: Int = 0,
    val removedExpired: Int = 0,
    val removedInvalidLuhn: Int = 0,
    val removedInvalidLengthOrCvv: Int = 0,
    val removedIncomplete: Int = 0,
    val removedUnparseable: Int = 0,
    val removedByFilter: Int = 0,
    val schemeBreakdown: Map<CardScheme, Int> = emptyMap()
) {
    val totalRemoved: Int
        get() = (totalInputLines - outputCount).coerceAtLeast(0)
}

object ListToolkitEngine {

    private val HTML_TAG_REGEX = Regex("<[^>]+>")
    private val HTML_ENTITY_REGEX = Regex("&(?:nbsp|amp|lt|gt|quot|apos|#39|#58|#124);")

    // Matches PAN (12..19 digits/wildcards possibly grouped with spaces or hyphens) + MM + YY(YY) + optional CVV
    private val CARD_TUPLE_REGEX = Regex(
        """([0-9xX][0-9xX\-\s]{10,22}[0-9xX])\s*[|/:;,\s]+\s*(?:exp\s*[:=]?\s*)?([0-9xX]{1,2})\s*[|/:;\-\s]+\s*([0-9xX]{2,4})(?![0-9xX])(?:\s*[|/:;,\s]+\s*(?:cvv\s*[:=]?\s*)?([0-9xX]{3,4})(?![0-9xX]))?""",
        RegexOption.IGNORE_CASE
    )

    // Matches a standalone PAN token of 12..19 digits/wildcards (optionally dashed/spaced)
    private val PAN_ONLY_REGEX = Regex("""(?<!\d)([0-9xX](?:[0-9xX\-\s]{10,21}[0-9xX]))(?!\d)""")

    // Matches JSON-style email + password fields on the same line
    private val JSON_EMAIL_PASS_REGEX = Regex(
        """"(?:email|mail|user|username)"\s*:\s*"([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})"[^{}\r\n]*?"(?:pass|password|pwd)"\s*:\s*"([^"\r\n]+)"""",
        RegexOption.IGNORE_CASE
    )

    // Matches labeled pairs like `email: foo@bar.com | password: secret` or `user=foo@bar.com pass=secret`
    private val LABELED_EMAIL_PASS_REGEX = Regex(
        """(?:email|e-mail|mail|user|login)\s*[:=]\s*([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})\s*(?:[|;,\s]+|\s+)(?:password|pass|pwd|pw)\s*[:=]\s*([^\s|;,"'<>]+)""",
        RegexOption.IGNORE_CASE
    )

    // Matches direct `email:pass`, `email|pass`, `email;pass`, `email,pass`, or `email\tpass` combos (including inside `url:email:pass` lines)
    private val DIRECT_EMAIL_PASS_REGEX = Regex(
        """([A-Za-z0-9._%+\-]+@[A-Za-z0-9.\-]+\.[A-Za-z]{2,})\s*[:|;\t,]\s*([^\s|;,"'<>]+)"""
    )

    data class ParsedCardEntry(
        val rawLine: String,
        val pan: String,
        val month: String,
        val year: String,
        val cvv: String,
        val scheme: CardScheme,
        val isComplete: Boolean
    )

    data class EmailPassEntry(
        val email: String,
        val password: String,
        val domain: String
    ) {
        val formatted: String
            get() = "$email:$password"
    }

    fun transform(
        rawInput: String,
        options: ListToolkitOptions,
        referenceYear: Int? = null,
        referenceMonth: Int? = null,
        random: Random = Random.Default
    ): ToolkitTransformResult {
        val nonBlankLines = rawInput.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (nonBlankLines.isEmpty()) {
            return ToolkitTransformResult(outputText = "")
        }

        if (options.outputFormat == OutputFormat.EMAIL_PASS) {
            return transformEmailPass(
                nonBlankLines = nonBlankLines,
                options = options,
                random = random
            )
        }

        val cal = Calendar.getInstance()
        val currentYear = referenceYear ?: cal.get(Calendar.YEAR)
        val currentMonth = referenceMonth ?: (cal.get(Calendar.MONTH) + 1)

        val includePrefixes = parsePrefixes(options.includeBinPrefixes)
        val excludePrefixes = parsePrefixes(options.excludeBinPrefixes)

        val seenKeys = HashSet<String>()
        val keptEntries = ArrayList<ParsedCardEntry>(nonBlankLines.size)
        val passthroughRawLines = ArrayList<String>()

        var removedDuplicates = 0
        var removedExpired = 0
        var removedInvalidLuhn = 0
        var removedInvalidLengthOrCvv = 0
        var removedIncomplete = 0
        var removedUnparseable = 0
        var removedByFilter = 0

        for (rawLine in nonBlankLines) {
            var entry = extractCardEntry(rawLine)
            if (entry == null) {
                if (options.removeUnparseable) {
                    removedUnparseable++
                } else {
                    if (options.dedupMode == DedupMode.BY_FULL_RECORD && !seenKeys.add("RAW:$rawLine")) {
                        removedDuplicates++
                    } else {
                        passthroughRawLines.add(rawLine)
                    }
                }
                continue
            }

            if (options.dropIncomplete && !entry.isComplete) {
                removedIncomplete++
                continue
            }

            if (options.solveWildcards && entry.containsWildcard()) {
                val solved = solveEntryWildcards(entry, random)
                if (solved != null) {
                    entry = solved
                }
            }

            val hasWildcardPan = entry.pan.any { it.equals('x', ignoreCase = true) }

            // Length / CVV validation
            if (options.removeInvalidCvvOrLength) {
                if (!hasWildcardPan && entry.pan.length !in entry.scheme.validLengths) {
                    removedInvalidLengthOrCvv++
                    continue
                }
                if (entry.cvv.isNotEmpty() &&
                    entry.cvv.none { it.equals('x', ignoreCase = true) } &&
                    entry.cvv.length != entry.scheme.cvvLength
                ) {
                    removedInvalidLengthOrCvv++
                    continue
                }
            }

            // Luhn check
            if (options.removeInvalidLuhn && !hasWildcardPan) {
                if (!CardValidatorEngine.verifyLuhn(entry.pan)) {
                    removedInvalidLuhn++
                    continue
                }
            }

            // Expiry check
            if (options.removeExpired && entry.month.isNotEmpty() && entry.year.isNotEmpty()) {
                val mmHasWildcard = entry.month.any { it.equals('x', ignoreCase = true) }
                val yyHasWildcard = entry.year.any { it.equals('x', ignoreCase = true) }
                if (!mmHasWildcard && !yyHasWildcard) {
                    val m = entry.month.toIntOrNull() ?: 0
                    val y = entry.year.toIntOrNull() ?: 0
                    val isExpired = m !in 1..12 ||
                        y < currentYear ||
                        (y == currentYear && m < currentMonth) ||
                        y > currentYear + 30
                    if (isExpired) {
                        removedExpired++
                        continue
                    }
                }
            }

            // Scheme filter
            if (options.selectedSchemes.isNotEmpty() && entry.scheme !in options.selectedSchemes) {
                removedByFilter++
                continue
            }

            // BIN prefix include / exclude filters
            val panDigitsPrefix = entry.pan.filter { it.isDigit() }
            if (includePrefixes.isNotEmpty() && includePrefixes.none { panDigitsPrefix.startsWith(it) }) {
                removedByFilter++
                continue
            }
            if (excludePrefixes.isNotEmpty() && excludePrefixes.any { panDigitsPrefix.startsWith(it) }) {
                removedByFilter++
                continue
            }

            // Minimum expiry year filter
            if (options.minExpiryYear != null) {
                val entryYear = entry.year.toIntOrNull()
                if (entryYear == null || entryYear < options.minExpiryYear) {
                    removedByFilter++
                    continue
                }
            }

            // Deduplication
            val dedupKey = when {
                options.outputFormat == OutputFormat.BIN_ONLY -> entry.pan.take(6)
                options.dedupMode == DedupMode.BY_PAN -> entry.pan
                options.dedupMode == DedupMode.BY_FULL_RECORD ->
                    "${entry.pan}|${entry.month}|${entry.year}|${entry.cvv}"
                else -> null
            }

            if (dedupKey != null && !seenKeys.add(dedupKey)) {
                removedDuplicates++
                continue
            }

            keptEntries.add(entry)
        }

        val sortedEntries = when (options.sortOrder) {
            ListSortOrder.KEEP_ORDER -> keptEntries
            ListSortOrder.BY_BIN_ASC -> keptEntries.sortedBy { it.pan }
            ListSortOrder.BY_EXPIRY_DESC -> keptEntries.sortedByDescending { entry ->
                val y = entry.year.toIntOrNull() ?: 0
                val m = entry.month.toIntOrNull() ?: 0
                y * 100 + m
            }
            ListSortOrder.BY_SCHEME -> keptEntries.sortedWith(
                compareBy<ParsedCardEntry> { it.scheme.displayName }.thenBy { it.pan }
            )
            ListSortOrder.SHUFFLE -> keptEntries.shuffled(random)
        }

        val limitedEntries = if (options.limitCount != null && options.limitCount > 0) {
            sortedEntries.take(options.limitCount)
        } else {
            sortedEntries
        }

        val schemeCounts = linkedMapOf<CardScheme, Int>()
        for (entry in limitedEntries) {
            schemeCounts[entry.scheme] = (schemeCounts[entry.scheme] ?: 0) + 1
        }

        val formattedLines = ArrayList<String>(limitedEntries.size + passthroughRawLines.size)
        for (entry in limitedEntries) {
            formattedLines.add(formatEntry(entry, options.outputFormat))
        }
        if (!options.removeUnparseable && passthroughRawLines.isNotEmpty()) {
            val remainingSlots = options.limitCount?.let { (it - formattedLines.size).coerceAtLeast(0) }
            val extra = if (remainingSlots != null) passthroughRawLines.take(remainingSlots) else passthroughRawLines
            formattedLines.addAll(extra)
        }

        return ToolkitTransformResult(
            outputText = formattedLines.joinToString("\n"),
            totalInputLines = nonBlankLines.size,
            outputCount = formattedLines.size,
            removedDuplicates = removedDuplicates,
            removedExpired = removedExpired,
            removedInvalidLuhn = removedInvalidLuhn,
            removedInvalidLengthOrCvv = removedInvalidLengthOrCvv,
            removedIncomplete = removedIncomplete,
            removedUnparseable = removedUnparseable,
            removedByFilter = removedByFilter,
            schemeBreakdown = schemeCounts
        )
    }

    private fun transformEmailPass(
        nonBlankLines: List<String>,
        options: ListToolkitOptions,
        random: Random
    ): ToolkitTransformResult {
        val includeTokens = parseGenericTokens(options.includeBinPrefixes)
        val excludeTokens = parseGenericTokens(options.excludeBinPrefixes)

        val seenKeys = HashSet<String>()
        val keptCombos = ArrayList<EmailPassEntry>(nonBlankLines.size)

        var removedDuplicates = 0
        var removedUnparseable = 0
        var removedByFilter = 0

        for (rawLine in nonBlankLines) {
            val extracted = extractEmailPassEntries(rawLine)
            if (extracted.isEmpty()) {
                removedUnparseable++
                continue
            }

            for (combo in extracted) {
                val lowerEmail = combo.email.lowercase(Locale.US)
                val lowerDomain = combo.domain.lowercase(Locale.US)

                if (includeTokens.isNotEmpty() && includeTokens.none { token ->
                        lowerEmail.contains(token) || lowerDomain.contains(token)
                    }
                ) {
                    removedByFilter++
                    continue
                }

                if (excludeTokens.isNotEmpty() && excludeTokens.any { token ->
                        lowerEmail.contains(token) || lowerDomain.contains(token)
                    }
                ) {
                    removedByFilter++
                    continue
                }

                val dedupKey = when (options.dedupMode) {
                    DedupMode.BY_PAN -> lowerEmail
                    DedupMode.BY_FULL_RECORD -> "$lowerEmail:${combo.password}"
                    DedupMode.NONE -> null
                }

                if (dedupKey != null && !seenKeys.add(dedupKey)) {
                    removedDuplicates++
                    continue
                }

                keptCombos.add(combo)
            }
        }

        val sortedCombos = when (options.sortOrder) {
            ListSortOrder.KEEP_ORDER -> keptCombos
            ListSortOrder.BY_BIN_ASC -> keptCombos.sortedBy { it.email.lowercase(Locale.US) }
            ListSortOrder.BY_EXPIRY_DESC -> keptCombos.sortedWith(
                compareByDescending<EmailPassEntry> { it.password.length }.thenBy { it.email.lowercase(Locale.US) }
            )
            ListSortOrder.BY_SCHEME -> keptCombos.sortedWith(
                compareBy<EmailPassEntry> { it.domain.lowercase(Locale.US) }.thenBy { it.email.lowercase(Locale.US) }
            )
            ListSortOrder.SHUFFLE -> keptCombos.shuffled(random)
        }

        val limitedCombos = if (options.limitCount != null && options.limitCount > 0) {
            sortedCombos.take(options.limitCount)
        } else {
            sortedCombos
        }

        return ToolkitTransformResult(
            outputText = limitedCombos.joinToString("\n") { it.formatted },
            totalInputLines = nonBlankLines.size,
            outputCount = limitedCombos.size,
            removedDuplicates = removedDuplicates,
            removedUnparseable = removedUnparseable,
            removedByFilter = removedByFilter
        )
    }

    /**
     * Extracts clean `email:pass` pairs from messy logs, JSON lines, HTML dumps, `url:email:pass`,
     * or pipe/colon/semicolon/comma/tab delimited lines.
     */
    fun extractEmailPassEntries(rawLine: String): List<EmailPassEntry> {
        val stripped = stripHtmlAndNoise(rawLine)
        if (stripped.isBlank() || !stripped.contains('@')) return emptyList()

        val results = ArrayList<EmailPassEntry>(2)

        // 1. Check JSON key/value format
        for (match in JSON_EMAIL_PASS_REGEX.findAll(stripped)) {
            val email = sanitizeEmail(match.groupValues[1])
            val pass = sanitizePassword(match.groupValues[2])
            createEmailPassEntry(email, pass)?.let { results.add(it) }
        }
        if (results.isNotEmpty()) return results

        // 2. Check labeled format (e.g. `email=a@b.com password=xyz` or `user: a@b.com | pass: xyz`)
        for (match in LABELED_EMAIL_PASS_REGEX.findAll(stripped)) {
            val email = sanitizeEmail(match.groupValues[1])
            val pass = sanitizePassword(match.groupValues[2])
            createEmailPassEntry(email, pass)?.let { results.add(it) }
        }
        if (results.isNotEmpty()) return results

        // 3. Direct `email:pass` / `email|pass` / `url:email:pass`
        for (match in DIRECT_EMAIL_PASS_REGEX.findAll(stripped)) {
            val email = sanitizeEmail(match.groupValues[1])
            val pass = sanitizePassword(match.groupValues[2])
            createEmailPassEntry(email, pass)?.let { results.add(it) }
        }

        return results
    }

    private fun createEmailPassEntry(email: String, password: String): EmailPassEntry? {
        if (email.isEmpty() || password.isEmpty()) return null
        val atIdx = email.indexOf('@')
        if (atIdx <= 0 || atIdx >= email.lastIndex) return null
        val domain = email.substring(atIdx + 1)
        if (!domain.contains('.') || domain.startsWith('.') || domain.endsWith('.')) return null
        // Reject passwords that are just label keywords or empty quotes
        val lowerPass = password.lowercase(Locale.US)
        if (lowerPass == "password" || lowerPass == "pass" || lowerPass == "null" || lowerPass == "none") {
            return null
        }
        return EmailPassEntry(
            email = email,
            password = password,
            domain = domain
        )
    }

    private fun sanitizeEmail(raw: String): String =
        raw.trim().trim('"', '\'', ',', ';', '(', ')', '[', ']', '<', '>')

    private fun sanitizePassword(raw: String): String =
        raw.trim().trim('"', '\'', ',', ';')

    fun extractCardEntry(rawLine: String): ParsedCardEntry? {
        val stripped = stripHtmlAndNoise(rawLine)
        if (stripped.isBlank()) return null

        // 1. Try structured regex extraction across messy text (HTML outputs, CSV, logs, pipe-delimited)
        val tupleMatch = CARD_TUPLE_REGEX.find(stripped)
        if (tupleMatch != null) {
            val rawPan = tupleMatch.groupValues[1].replace("-", "").replace(" ", "").trim()
            if (isValidPanToken(rawPan)) {
                val mm = normalizeMonth(tupleMatch.groupValues[2])
                val yyyy = normalizeYearToken(tupleMatch.groupValues[3])
                val cvv = tupleMatch.groupValues[4].trim()
                val scheme = CardValidatorEngine.detectScheme(rawPan)
                val complete = mm.isNotEmpty() && yyyy.isNotEmpty() && cvv.isNotEmpty()
                return ParsedCardEntry(
                    rawLine = rawLine.trim(),
                    pan = rawPan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    isComplete = complete
                )
            }
        }

        // 2. Fallback: delimited fields with standard tokens (e.g., PAN|MM|YYYY|CVV or PAN|MM|YYYY)
        val tokens = stripped.split('|', ';', ',').map { it.trim() }.filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            val panIndex = tokens.indexOfFirst { token ->
                val compact = token.replace("-", "").replace(" ", "")
                isValidPanToken(compact)
            }
            if (panIndex >= 0) {
                val pan = tokens[panIndex].replace("-", "").replace(" ", "")
                val afterPan = tokens.drop(panIndex + 1)
                var mm = ""
                var yyyy = ""
                var cvv = ""

                if (afterPan.isNotEmpty()) {
                    val first = afterPan[0]
                    if (first.contains('/') || first.contains('-')) {
                        val expParts = first.split('/', '-').map { it.trim() }
                        if (expParts.size >= 2) {
                            mm = normalizeMonth(expParts[0])
                            yyyy = normalizeYearToken(expParts[1])
                            cvv = afterPan.getOrNull(1)?.filter { it.isDigit() || it.equals('x', ignoreCase = true) }.orEmpty()
                        }
                    } else {
                        mm = normalizeMonth(afterPan.getOrNull(0).orEmpty())
                        yyyy = normalizeYearToken(afterPan.getOrNull(1).orEmpty())
                        cvv = afterPan.getOrNull(2)?.filter { it.isDigit() || it.equals('x', ignoreCase = true) }.orEmpty()
                    }
                }

                val scheme = CardValidatorEngine.detectScheme(pan)
                return ParsedCardEntry(
                    rawLine = rawLine.trim(),
                    pan = pan,
                    month = mm,
                    year = yyyy,
                    cvv = cvv,
                    scheme = scheme,
                    isComplete = mm.isNotEmpty() && yyyy.isNotEmpty() && cvv.isNotEmpty()
                )
            }
        }

        // 3. Fallback: standalone PAN token anywhere in line
        val panMatch = PAN_ONLY_REGEX.find(stripped)
        if (panMatch != null) {
            val pan = panMatch.groupValues[1].replace("-", "").replace(" ", "")
            if (isValidPanToken(pan)) {
                return ParsedCardEntry(
                    rawLine = rawLine.trim(),
                    pan = pan,
                    month = "",
                    year = "",
                    cvv = "",
                    scheme = CardValidatorEngine.detectScheme(pan),
                    isComplete = false
                )
            }
        }

        return null
    }

    fun formatEntry(entry: ParsedCardEntry, format: OutputFormat): String {
        val mm = entry.month.ifEmpty { "08" }
        val yyyy = entry.year.ifEmpty { "2028" }
        val yy = if (yyyy.length == 4) yyyy.takeLast(2) else yyyy.padStart(2, '0')
        val cvv = entry.cvv.ifEmpty { "0".repeat(entry.scheme.cvvLength) }

        return when (format) {
            OutputFormat.PIPE_YYYY -> "${entry.pan}|$mm|$yyyy|$cvv"
            OutputFormat.PIPE_YY -> "${entry.pan}|$mm|$yy|$cvv"
            OutputFormat.PIPE_SLASH_EXP -> "${entry.pan}|$mm/$yy|$cvv"
            OutputFormat.COLON -> "${entry.pan}:$mm:$yyyy:$cvv"
            OutputFormat.SLASH -> "${entry.pan}/$mm/$yyyy/$cvv"
            OutputFormat.CSV -> "${entry.pan},$mm,$yyyy,$cvv"
            OutputFormat.PAN_ONLY -> entry.pan
            OutputFormat.BIN_ONLY -> entry.pan.take(6)
            OutputFormat.EMAIL_PASS -> "${entry.pan}:$cvv"
        }
    }

    private fun ParsedCardEntry.containsWildcard(): Boolean =
        pan.any { it.equals('x', ignoreCase = true) } ||
            month.any { it.equals('x', ignoreCase = true) } ||
            year.any { it.equals('x', ignoreCase = true) } ||
            cvv.any { it.equals('x', ignoreCase = true) }

    private fun solveEntryWildcards(entry: ParsedCardEntry, random: Random): ParsedCardEntry? {
        val template = "${entry.pan}|${entry.month.ifEmpty { "xx" }}|${entry.year.ifEmpty { "xxxx" }}|${entry.cvv}"
        val resolved = CardValidatorEngine.expandPatternBatch(template, 1, random).firstOrNull() ?: return null
        val parts = resolved.split('|')
        if (parts.size < 4) return null
        return ParsedCardEntry(
            rawLine = entry.rawLine,
            pan = parts[0],
            month = parts[1],
            year = parts[2],
            cvv = parts[3],
            scheme = CardValidatorEngine.detectScheme(parts[0]),
            isComplete = true
        )
    }

    private fun stripHtmlAndNoise(raw: String): String {
        var cleaned = HTML_TAG_REGEX.replace(raw, " ")
        cleaned = HTML_ENTITY_REGEX.replace(cleaned) { match ->
            when (match.value.lowercase(Locale.US)) {
                "&nbsp;" -> " "
                "&amp;" -> "&"
                "&lt;" -> "<"
                "&gt;" -> ">"
                "&quot;" -> "\""
                "&apos;", "&#39;" -> "'"
                "&#58;" -> ":"
                "&#124;" -> "|"
                else -> " "
            }
        }
        return cleaned.trim()
    }

    private fun isValidPanToken(compact: String): Boolean {
        if (compact.length !in 12..19) return false
        if (!compact.all { it.isDigit() || it.equals('x', ignoreCase = true) }) return false
        val digitCount = compact.count { it.isDigit() }
        return digitCount >= 6
    }

    private fun normalizeMonth(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.any { it.equals('x', ignoreCase = true) }) return clean.lowercase(Locale.US).padStart(2, 'x')
        return if (clean.all { it.isDigit() }) clean.padStart(2, '0') else ""
    }

    private fun normalizeYearToken(raw: String): String {
        val clean = raw.trim()
        if (clean.isEmpty()) return ""
        if (clean.any { it.equals('x', ignoreCase = true) }) return clean.lowercase(Locale.US)
        if (!clean.all { it.isDigit() }) return ""
        return CardValidatorEngine.normalizeYear(clean)
    }

    private fun parsePrefixes(raw: String): List<String> = raw
        .split(',', ' ', '\n', ';', '|')
        .map { token -> token.filter { it.isDigit() } }
        .filter { it.isNotEmpty() }
        .distinct()

    private fun parseGenericTokens(raw: String): List<String> = raw
        .split(',', ' ', '\n', ';', '|')
        .map { it.trim().lowercase(Locale.US) }
        .filter { it.isNotEmpty() }
        .distinct()
}
