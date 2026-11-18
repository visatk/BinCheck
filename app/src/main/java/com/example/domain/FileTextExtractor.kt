package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.UUID

enum class SupportedFileType(val label: String) {
    TXT("TXT"),
    HTML("HTML")
}

data class ImportedFilePreview(
    val uriString: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val fileType: SupportedFileType,
    val rawContent: String,
    val allNonEmptyLines: List<String>,
    val delimitedRecordLines: List<String>,
    val stagedFilePath: String? = null,
    val totalLineCount: Int = allNonEmptyLines.size,
    val delimitedLineCount: Int = delimitedRecordLines.size,
    val isLargeStagedFile: Boolean = false
)

object FileTextExtractor {

    private const val IN_MEMORY_MAX_BYTES = 2L * 1024L * 1024L // 2 MB fast inline console threshold
    private const val MAX_FILE_BYTES = LargeFileWorkspace.MAX_SUPPORTED_FILE_BYTES // 260 MB streaming cap
    private const val LARGE_PREVIEW_MAX_LINES = 2_500
    private val DELIMITED_RECORD_REGEX = Regex("""[0-9xX]{6,19}[|/:;][0-9xX]{1,2}[|/:;][0-9xX]{2,4}[|/:;][0-9xX]{3,4}""")

    fun detectFileType(fileName: String, mimeType: String?): SupportedFileType? {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        return when {
            lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerMime.contains("html") -> SupportedFileType.HTML
            lowerName.endsWith(".txt") || lowerName.endsWith(".csv") || lowerName.endsWith(".log") ||
                lowerName.endsWith(".json") || lowerMime.startsWith("text/plain") || lowerMime.contains("json") || lowerMime.contains("csv") -> SupportedFileType.TXT
            lowerMime.startsWith("text/") -> SupportedFileType.TXT
            else -> null
        }
    }

    /**
     * Reads and parses a user-selected .txt or .html URI using Android's ContentResolver.
     */
    suspend fun readAndParseUri(
        context: Context,
        uri: Uri
    ): Result<ImportedFilePreview> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)

            var displayName = "document.txt"
            var fileSize = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.let { if (it.isNotBlank()) displayName = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            val detectedType = detectFileType(displayName, mimeType)
                ?: throw IllegalArgumentException("Unsupported file format. Please select a .txt or .html file.")

            if (fileSize > MAX_FILE_BYTES) {
                throw IllegalArgumentException("File exceeds ${formatFileSize(MAX_FILE_BYTES)} streaming limit.")
            }

            if (fileSize in 0L..IN_MEMORY_MAX_BYTES) {
                val rawContent = resolver.openInputStream(uri)?.use { stream ->
                    val reader = BufferedReader(InputStreamReader(BufferedInputStream(stream, 32 * 1024), StandardCharsets.UTF_8))
                    val builder = StringBuilder()
                    val buffer = CharArray(8192)
                    var totalChars = 0L
                    while (true) {
                        val read = reader.read(buffer)
                        if (read == -1) break
                        totalChars += read
                        if (totalChars <= IN_MEMORY_MAX_BYTES) {
                            builder.append(buffer, 0, read)
                        } else {
                            // Spill to streaming path below
                            break
                        }
                    }
                    if (totalChars <= IN_MEMORY_MAX_BYTES) builder.toString() else null
                }

                if (rawContent != null) {
                    val actualBytes = if (fileSize >= 0) fileSize else rawContent.toByteArray(StandardCharsets.UTF_8).size.toLong()
                    val normalizedLines = extractLines(rawContent, detectedType)
                    val delimitedLines = extractDelimitedLines(normalizedLines)
                    return@runCatching ImportedFilePreview(
                        uriString = uri.toString(),
                        fileName = displayName,
                        fileSizeBytes = actualBytes,
                        fileType = detectedType,
                        rawContent = rawContent,
                        allNonEmptyLines = normalizedLines,
                        delimitedRecordLines = delimitedLines,
                        totalLineCount = normalizedLines.size,
                        delimitedLineCount = delimitedLines.size,
                        isLargeStagedFile = false
                    )
                }
            }

            // Streaming path for 2 MB – 260 MB files: stage to cacheDir without holding full content in heap
            val stageDir = File(context.cacheDir, "staged_imports").apply { mkdirs() }
            stageDir.listFiles()?.forEach { old ->
                if (System.currentTimeMillis() - old.lastModified() > 6L * 60L * 60L * 1000L) {
                    old.delete()
                }
            }
            val stagedFile = File(stageDir, "import_${UUID.randomUUID().toString().take(10)}.txt")
            val previewAll = ArrayList<String>(256)
            val previewDelimited = ArrayList<String>(256)
            var totalLineCount = 0
            var totalDelimitedCount = 0
            var totalBytes = 0L

            resolver.openInputStream(uri)?.use { rawIn ->
                val reader = BufferedReader(InputStreamReader(BufferedInputStream(rawIn, 64 * 1024), StandardCharsets.UTF_8), 64 * 1024)
                BufferedOutputStream(FileOutputStream(stagedFile), 64 * 1024).use { out ->
                    val nl = byteArrayOf('\n'.code.toByte())
                    var inScript = false
                    while (true) {
                        val rawLine = reader.readLine() ?: break
                        val linesOut = if (detectedType == SupportedFileType.HTML) {
                            val pair = stripHtmlStreamingLine(rawLine, inScript)
                            inScript = pair.second
                            pair.first
                        } else {
                            val trimmed = rawLine.trim()
                            if (trimmed.isNotEmpty()) listOf(trimmed) else emptyList()
                        }

                        for (cleanLine in linesOut) {
                            val bytes = cleanLine.toByteArray(StandardCharsets.UTF_8)
                            totalBytes += bytes.size + 1L
                            if (totalBytes > MAX_FILE_BYTES) {
                                stagedFile.delete()
                                throw IllegalArgumentException("File exceeds ${formatFileSize(MAX_FILE_BYTES)} streaming limit.")
                            }
                            out.write(bytes)
                            out.write(nl)
                            totalLineCount++
                            if (previewAll.size < LARGE_PREVIEW_MAX_LINES) {
                                previewAll.add(cleanLine)
                            }
                            for (record in extractDelimitedRecordsFromLine(cleanLine)) {
                                totalDelimitedCount++
                                if (previewDelimited.size < LARGE_PREVIEW_MAX_LINES) {
                                    previewDelimited.add(record)
                                }
                            }
                        }
                    }
                    out.flush()
                }
            } ?: throw IllegalStateException("Unable to open selected file stream.")

            ImportedFilePreview(
                uriString = uri.toString(),
                fileName = displayName,
                fileSizeBytes = if (fileSize >= 0L) fileSize else totalBytes,
                fileType = detectedType,
                rawContent = "",
                allNonEmptyLines = previewAll,
                delimitedRecordLines = previewDelimited,
                stagedFilePath = stagedFile.absolutePath,
                totalLineCount = totalLineCount,
                delimitedLineCount = totalDelimitedCount,
                isLargeStagedFile = true
            )
        }
    }

    private fun stripHtmlStreamingLine(rawLine: String, initialInScript: Boolean): Pair<List<String>, Boolean> {
        var inIgnored = initialInScript
        var work = rawLine
        if (inIgnored) {
            val endMatch = Regex("(?i)</(?:script|style|head)>").find(work) ?: return emptyList<String>() to true
            work = work.substring(endMatch.range.last + 1)
            inIgnored = false
        }
        work = work
            .replace(Regex("(?is)<!--.*?-->"), "\n")
            .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), "\n")
        val openTag = Regex("(?is)<(script|style|head)[^>]*>").find(work)
        if (openTag != null) {
            work = work.substring(0, openTag.range.first)
            inIgnored = true
        }
        if (work.isBlank()) return emptyList<String>() to inIgnored
        return stripHtmlToText(work).lines().map { it.trim() }.filter { it.isNotEmpty() } to inIgnored
    }

    /**
     * Converts raw .txt or .html content into clean, non-empty text lines.
     */
    fun extractLines(rawContent: String, fileType: SupportedFileType): List<String> {
        val plainText = when (fileType) {
            SupportedFileType.TXT -> rawContent
            SupportedFileType.HTML -> stripHtmlToText(rawContent)
        }
        return plainText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Extracts one or more pipe/slash/colon delimited card/pattern records from a single cleaned line,
     * falling back to delimited token normalization for spaced records.
     */
    fun extractDelimitedRecordsFromLine(cleanLine: String): List<String> {
        if (cleanLine.isBlank()) return emptyList()
        val found = DELIMITED_RECORD_REGEX.findAll(cleanLine).map { it.value }.toList()
        if (found.isNotEmpty()) return found
        if (cleanLine.contains('|') || cleanLine.contains('/')) {
            val parts = cleanLine.split('|', '/', ':', ';').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size >= 4 && parts[0].length >= 6) {
                return listOf(parts.take(4).joinToString("|"))
            }
        }
        return emptyList()
    }

    /**
     * Extracts pipe/slash/colon delimited card/pattern records from lines (including inline matches inside HTML/log lines).
     */
    fun extractDelimitedLines(lines: List<String>): List<String> {
        val matches = LinkedHashSet<String>()
        for (line in lines) {
            matches.addAll(extractDelimitedRecordsFromLine(line))
        }
        return matches.toList()
    }

    /**
     * Strips <script>, <style>, and HTML tags while preserving block boundaries as newlines
     * and decoding common HTML entities.
     */
    fun stripHtmlToText(html: String): String {
        var text = html
            .replace(Regex("(?is)<!--.*?-->"), "\n")
            .replace(Regex("(?is)<head[^>]*>.*?</head>"), "\n")
            .replace(Regex("(?is)<script[^>]*>.*?</script>"), "\n")
            .replace(Regex("(?is)<style[^>]*>.*?</style>"), "\n")
            .replace(Regex("(?i)<(br|p|div|li|tr|h[1-6]|pre|code|td|th)[^>]*>"), "\n")
            .replace(Regex("<[^>]+>"), " ")

        text = decodeHtmlEntities(text)

        return text.lines()
            .joinToString("\n") { line ->
                line.replace(Regex("[ \\t\\u00A0]+"), " ").trim()
            }
    }

    private fun decodeHtmlEntities(input: String): String {
        var decoded = input
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&#124;", "|")
            .replace("&#x7C;", "|", ignoreCase = true)
            .replace("&#47;", "/")
            .replace("&#58;", ":")

        decoded = Regex("&#(\\d+);").replace(decoded) { match ->
            val code = match.groupValues[1].toIntOrNull()
            if (code != null && code in 32..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }

        decoded = Regex("&#x([0-9a-fA-F]+);").replace(decoded) { match ->
            val code = match.groupValues[1].toIntOrNull(16)
            if (code != null && code in 32..0x10FFFF) {
                String(Character.toChars(code))
            } else {
                match.value
            }
        }
        return decoded
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Writes UTF-8 text content to a user-chosen SAF document Uri (e.g., from CreateDocument).
     * Returns the number of non-blank lines written.
     */
    suspend fun writeTextToUri(
        context: Context,
        uri: Uri,
        content: String
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bytes = content.toByteArray(StandardCharsets.UTF_8)
            resolver.openOutputStream(uri, "wt")?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: resolver.openOutputStream(uri)?.use { out ->
                out.write(bytes)
                out.flush()
            } ?: throw IllegalStateException("Unable to open destination file for writing.")

            content.lineSequence().count { it.isNotBlank() }
        }
    }
}
