package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Locale

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
    val delimitedRecordLines: List<String>
)

object FileTextExtractor {

    private const val MAX_FILE_BYTES = 5L * 1024L * 1024L // 5 MB safety cap
    private val DELIMITED_RECORD_REGEX = Regex("""[0-9xX]{6,19}[|/:;][0-9xX]{1,2}[|/:;][0-9xX]{2,4}[|/:;][0-9xX]{3,4}""")

    fun detectFileType(fileName: String, mimeType: String?): SupportedFileType? {
        val lowerName = fileName.lowercase(Locale.ROOT)
        val lowerMime = mimeType?.lowercase(Locale.ROOT).orEmpty()
        return when {
            lowerName.endsWith(".html") || lowerName.endsWith(".htm") || lowerMime.contains("html") -> SupportedFileType.HTML
            lowerName.endsWith(".txt") || lowerName.endsWith(".csv") || lowerName.endsWith(".log") || lowerMime.startsWith("text/plain") -> SupportedFileType.TXT
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
                throw IllegalArgumentException("File exceeds 5 MB limit for in-memory console processing.")
            }

            val rawContent = resolver.openInputStream(uri)?.use { stream ->
                val reader = BufferedReader(InputStreamReader(stream, StandardCharsets.UTF_8))
                val builder = StringBuilder()
                val buffer = CharArray(8192)
                var totalChars = 0
                while (true) {
                    val read = reader.read(buffer)
                    if (read == -1) break
                    totalChars += read
                    if (totalChars > MAX_FILE_BYTES) {
                        throw IllegalArgumentException("File content exceeds 5 MB limit.")
                    }
                    builder.append(buffer, 0, read)
                }
                builder.toString()
            } ?: throw IllegalStateException("Unable to open selected file stream.")

            val actualBytes = if (fileSize >= 0) fileSize else rawContent.toByteArray(StandardCharsets.UTF_8).size.toLong()
            val normalizedLines = extractLines(rawContent, detectedType)
            val delimitedLines = extractDelimitedLines(normalizedLines)

            ImportedFilePreview(
                uriString = uri.toString(),
                fileName = displayName,
                fileSizeBytes = actualBytes,
                fileType = detectedType,
                rawContent = rawContent,
                allNonEmptyLines = normalizedLines,
                delimitedRecordLines = delimitedLines
            )
        }
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
     * Extracts pipe/slash/colon delimited card/pattern records from lines (including inline matches inside HTML/log lines).
     */
    fun extractDelimitedLines(lines: List<String>): List<String> {
        val matches = LinkedHashSet<String>()
        for (line in lines) {
            val found = DELIMITED_RECORD_REGEX.findAll(line)
            var matchedAny = false
            for (match in found) {
                matches.add(match.value)
                matchedAny = true
            }
            if (!matchedAny && (line.contains('|') || line.contains('/'))) {
                val parts = line.split('|', '/', ':', ';').map { it.trim() }.filter { it.isNotEmpty() }
                if (parts.size >= 4 && parts[0].length >= 6) {
                    matches.add(parts.take(4).joinToString("|"))
                }
            }
        }
        return matches.toList()
    }

    /**
     * Strips <script>, <style>, and HTML tags while preserving block boundaries as newlines
     * and decoding common HTML entities.
     */
    fun stripHtmlToText(html: String): String {
        var text = html
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
        return input
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
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }
}
