package com.example.domain

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale
import java.util.PriorityQueue
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Streaming disk-backed file workspace & transform engine engineered for 100–200+ MB lists.
 *
 * Uses sparse byte-offset checkpoint arrays ([PAGE_SIZE_LINES] = 200 lines per slot -> ~140 KB RAM
 * for a 3,500,000-line / 200 MB file), [RandomAccessFile] O(1) page windows, per-page in-memory edit
 * overlays, primitive 64-bit hash deduplication, and external k-way merge sorting on disk so huge
 * files open, edit, search, transform, split, and save smoothly without OutOfMemoryError.
 */
object LargeFileWorkspace {

    const val INLINE_EDITOR_THRESHOLD_BYTES: Long = 1024L * 1024L // 1 MB -> Disk-Stream Mode above this
    const val INLINE_EDITOR_THRESHOLD_LINES: Int = 6_000
    const val MAX_SUPPORTED_FILE_BYTES: Long = 260L * 1024L * 1024L // 260 MB ceiling
    const val PAGE_SIZE_LINES: Int = 200
    const val MAX_SAFE_CONSOLE_LINES: Int = 5_000
    const val MAX_SAFE_CLIPBOARD_BYTES: Int = 350 * 1024 // Stay well under 1 MB Binder limit
    private const val STREAM_BUFFER_BYTES: Int = 64 * 1024
    private const val EXTERNAL_SORT_RUN_LINES: Int = 45_000
    private const val MAX_DEDUP_TABLE_ENTRIES: Int = 4_000_000

    enum class BulkEditType(val label: String) {
        FIND_REPLACE("Find & Replace"),
        PREFIX_SUFFIX("Add Prefix / Suffix"),
        TRIM_DROP_EMPTY("Trim & Drop Empty"),
        CASE_LOWER("lowercase All"),
        CASE_UPPER("UPPERCASE All"),
        NORMALIZE_DELIMITER("Normalize Separator"),
        EXTRACT_COLUMNS("Extract Columns"),
        KEEP_MATCHING("Keep Matching Lines"),
        REMOVE_MATCHING("Remove Matching Lines"),
        SORT_ASC("Sort A → Z")
    }

    data class BulkEditSpec(
        val type: BulkEditType,
        val findQuery: String = "",
        val replaceWith: String = "",
        val useRegex: Boolean = false,
        val ignoreCase: Boolean = true,
        val prefix: String = "",
        val suffix: String = "",
        val targetDelimiter: String = "|",
        val columnIndices: String = "1,2" // 1-based comma-separated columns
    )

    data class SearchHit(
        val globalLineIndex: Int, // 0-based
        val pageIndex: Int,
        val lineInPage: Int,
        val preview: String
    )

    data class SearchScanResult(
        val query: String,
        val totalMatches: Int,
        val hits: List<SearchHit>,
        val durationMs: Long
    )

    data class BulkEditOutcome(
        val updatedSession: LargeFileSession,
        val modifiedOrRemovedCount: Int,
        val durationMs: Long,
        val summaryMessage: String
    )

    data class LargeFileSession(
        val sessionId: String,
        val workspaceDir: File,
        val sourceFile: File,
        val fileName: String,
        val fileSizeBytes: Long,
        val totalLines: Int,
        val pageSize: Int = PAGE_SIZE_LINES,
        val pageOffsets: LongArray,
        val editedPages: Map<Int, List<String>> = emptyMap(),
        val outputFile: File? = null,
        val outputSizeBytes: Long = 0L,
        val outputTotalLines: Int = 0,
        val outputPageOffsets: LongArray = LongArray(0)
    ) {
        val totalPages: Int
            get() = pageOffsets.size.coerceAtLeast(1)

        val totalOutputPages: Int
            get() = outputPageOffsets.size.coerceAtLeast(1)

        val hasUnsavedPageEdits: Boolean
            get() = editedPages.isNotEmpty()

        val activeTotalLines: Int
            get() {
                if (editedPages.isEmpty()) return totalLines
                var delta = 0
                for ((pageIdx, lines) in editedPages) {
                    val originalCount = if (pageIdx < 0 || pageIdx >= totalPages) {
                        0
                    } else {
                        minOf(pageSize, totalLines - pageIdx * pageSize).coerceAtLeast(0)
                    }
                    delta += (lines.size - originalCount)
                }
                return (totalLines + delta).coerceAtLeast(0)
            }
    }

    fun workspaceRoot(cacheDir: File): File =
        File(cacheDir, "toolkit_workspace").apply { mkdirs() }

    /**
     * Sweeps abandoned workspace session directories older than [maxAgeMs].
     */
    fun pruneOldWorkspaces(cacheDir: File, maxAgeMs: Long = 12L * 60L * 60L * 1000L) {
        runCatching {
            val root = File(cacheDir, "toolkit_workspace")
            if (!root.exists()) return
            val cutoff = System.currentTimeMillis() - maxAgeMs
            root.listFiles()?.forEach { child ->
                if (child.lastModified() < cutoff) {
                    child.deleteRecursively()
                }
            }
        }
    }

    fun cleanupSession(session: LargeFileSession?) {
        if (session == null) return
        runCatching { session.workspaceDir.deleteRecursively() }
    }

    /**
     * Opens any SAF [uri] (up to 260 MB) by streaming directly into disk cache while building a compact
     * sparse byte-offset index (~140 KB RAM for 200 MB). Never holds the full file in heap.
     */
    suspend fun ingestUri(
        context: Context,
        uri: Uri,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<LargeFileSession> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri)
            var displayName = "list_file.txt"
            var reportedSize = -1L

            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        cursor.getString(nameIdx)?.takeIf { it.isNotBlank() }?.let { displayName = it }
                    }
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) {
                        reportedSize = cursor.getLong(sizeIdx)
                    }
                }
            }

            if (reportedSize > MAX_SUPPORTED_FILE_BYTES) {
                throw IllegalArgumentException(
                    "File size (${FileTextExtractor.formatFileSize(reportedSize)}) exceeds the 260 MB streaming workspace limit."
                )
            }

            val fileType = FileTextExtractor.detectFileType(displayName, mimeType) ?: SupportedFileType.TXT
            val sessionId = UUID.randomUUID().toString().replace("-", "").take(12)
            val dir = File(workspaceRoot(context.cacheDir), "s_$sessionId").apply { mkdirs() }
            val activeFile = File(dir, "active.txt")

            var totalLines = 0
            var totalBytesWritten = 0L
            val pageOffsetsList = ArrayList<Long>(2048)

            onProgress(0.05f, "Streaming $displayName to workspace...")

            resolver.openInputStream(uri)?.use { rawIn ->
                val bufferedIn = BufferedInputStream(rawIn, STREAM_BUFFER_BYTES)
                val reader = BufferedReader(InputStreamReader(bufferedIn, StandardCharsets.UTF_8), STREAM_BUFFER_BYTES)
                BufferedOutputStream(FileOutputStream(activeFile), STREAM_BUFFER_BYTES).use { out ->
                    var inScriptOrStyle = false
                    var bytesReadApprox = 0L
                    val newlineBytes = byteArrayOf('\n'.code.toByte())

                    while (true) {
                        coroutineContext.ensureActive()
                        val rawLine = reader.readLine() ?: break
                        bytesReadApprox += rawLine.length + 1L
                        if (bytesReadApprox > MAX_SUPPORTED_FILE_BYTES) {
                            throw IllegalArgumentException("Stream exceeded 260 MB maximum workspace size.")
                        }

                        val normalizedLines: List<String> = if (fileType == SupportedFileType.HTML) {
                            val pair = stripHtmlLineStreaming(rawLine, inScriptOrStyle)
                            inScriptOrStyle = pair.second
                            pair.first
                        } else {
                            listOf(rawLine)
                        }

                        for (line in normalizedLines) {
                            if (totalLines % PAGE_SIZE_LINES == 0) {
                                pageOffsetsList.add(totalBytesWritten)
                            }
                            val lineBytes = line.toByteArray(StandardCharsets.UTF_8)
                            out.write(lineBytes)
                            out.write(newlineBytes)
                            totalBytesWritten += lineBytes.size + 1L
                            totalLines++
                        }

                        if (totalLines % 8192 == 0) {
                            val pct = if (reportedSize > 0L) {
                                (bytesReadApprox.toFloat() / reportedSize.toFloat()).coerceIn(0.05f, 0.96f)
                            } else {
                                0.5f
                            }
                            onProgress(pct, "Indexed $totalLines lines (${FileTextExtractor.formatFileSize(totalBytesWritten)})")
                        }
                    }
                    out.flush()
                }
            } ?: throw IllegalStateException("Unable to open selected file stream.")

            if (pageOffsetsList.isEmpty()) {
                pageOffsetsList.add(0L)
            }

            onProgress(1f, "Ready • $totalLines lines")

            LargeFileSession(
                sessionId = sessionId,
                workspaceDir = dir,
                sourceFile = activeFile,
                fileName = displayName,
                fileSizeBytes = activeFile.length().coerceAtLeast(totalBytesWritten),
                totalLines = totalLines,
                pageSize = PAGE_SIZE_LINES,
                pageOffsets = pageOffsetsList.toLongArray()
            )
        }
    }

    /**
     * Creates a disk-backed [LargeFileSession] from an existing [File] or raw text (for fast JVM/Robolectric
     * testing and promoting large console batches cleanly into disk-stream mode).
     */
    suspend fun ingestFileOrText(
        cacheDir: File,
        fileName: String,
        rawText: String? = null,
        existingFile: File? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<LargeFileSession> = withContext(Dispatchers.IO) {
        runCatching {
            val sessionId = UUID.randomUUID().toString().replace("-", "").take(12)
            val dir = File(workspaceRoot(cacheDir), "s_$sessionId").apply { mkdirs() }
            val activeFile = File(dir, "active.txt")

            val (linesCount, bytesWritten, offsets) = if (existingFile != null) {
                copyAndIndexFile(existingFile, activeFile, onProgress)
            } else {
                writeStringAndIndex(rawText.orEmpty(), activeFile, onProgress)
            }

            LargeFileSession(
                sessionId = sessionId,
                workspaceDir = dir,
                sourceFile = activeFile,
                fileName = fileName,
                fileSizeBytes = bytesWritten,
                totalLines = linesCount,
                pageSize = PAGE_SIZE_LINES,
                pageOffsets = offsets
            )
        }
    }

    /**
     * O(1) seek-and-read window for any page (200 lines at a time) inside a 100–200 MB file.
     * Transparently serves uncommitted per-page edits when [overlayEdits] contains [pageIndex].
     */
    suspend fun readPage(
        file: File,
        pageOffsets: LongArray,
        totalLines: Int,
        pageIndex: Int,
        pageSize: Int = PAGE_SIZE_LINES,
        overlayEdits: Map<Int, List<String>>? = null
    ): List<String> = withContext(Dispatchers.IO) {
        overlayEdits?.get(pageIndex)?.let { return@withContext it }
        if (totalLines <= 0 || !file.exists() || pageIndex < 0 || pageIndex >= pageOffsets.size) {
            return@withContext emptyList()
        }
        val startOffset = pageOffsets[pageIndex]
        val expectedCount = minOf(pageSize, totalLines - pageIndex * pageSize).coerceAtLeast(0)
        val lines = ArrayList<String>(expectedCount)
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(startOffset)
            // Buffered sequential read starting from exact seek byte offset
            val bis = BufferedInputStream(object : java.io.InputStream() {
                override fun read(): Int = raf.read()
                override fun read(b: ByteArray, off: Int, len: Int): Int = raf.read(b, off, len)
            }, STREAM_BUFFER_BYTES)
            val reader = BufferedReader(InputStreamReader(bis, StandardCharsets.UTF_8), 16 * 1024)
            var count = 0
            while (count < expectedCount) {
                val line = reader.readLine() ?: break
                lines.add(line)
                count++
            }
        }
        lines
    }

    /**
     * Searches the entire disk-backed file (respecting any unsaved [LargeFileSession.editedPages])
     * with plain-text or regex matching and returns clickable page+line jump hits.
     */
    suspend fun searchAcrossFile(
        session: LargeFileSession,
        query: String,
        useRegex: Boolean = false,
        ignoreCase: Boolean = true,
        maxHits: Int = 300,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): SearchScanResult = withContext(Dispatchers.Default) {
        val startNs = System.nanoTime()
        if (query.isEmpty() || session.totalLines == 0) {
            return@withContext SearchScanResult(query = query, totalMatches = 0, hits = emptyList(), durationMs = 0L)
        }
        val compiledRegex = if (useRegex) {
            val opts = if (ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
            runCatching { Regex(query, opts) }.getOrNull()
        } else null
        if (useRegex && compiledRegex == null) {
            return@withContext SearchScanResult(query = query, totalMatches = 0, hits = emptyList(), durationMs = 0L)
        }

        val hits = ArrayList<SearchHit>(minOf(maxHits, 64))
        var totalMatches = 0
        var lineIndex = 0

        forEachActiveLine(session) { pageIdx, lineInPage, lineText ->
            coroutineContext.ensureActive()
            val matched = if (compiledRegex != null) {
                compiledRegex.containsMatchIn(lineText)
            } else {
                lineText.contains(query, ignoreCase = ignoreCase)
            }
            if (matched) {
                totalMatches++
                if (hits.size < maxHits) {
                    hits.add(
                        SearchHit(
                            globalLineIndex = lineIndex,
                            pageIndex = pageIdx,
                            lineInPage = lineInPage,
                            preview = lineText.take(180)
                        )
                    )
                }
            }
            lineIndex++
            if (lineIndex % 8192 == 0 && session.totalLines > 0) {
                onProgress(
                    (lineIndex.toFloat() / session.totalLines.toFloat()).coerceIn(0.02f, 0.98f),
                    "Searched $lineIndex / ${session.totalLines} lines • $totalMatches hit(s)"
                )
            }
        }

        val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
        onProgress(1f, "Found $totalMatches match(es) in ${elapsedMs}ms")
        SearchScanResult(query = query, totalMatches = totalMatches, hits = hits, durationMs = elapsedMs)
    }

    /**
     * Streams a whole-file editing operation across a 100–200 MB [session] (merging any per-page edits),
     * writes atomically to a compacted workspace file, swaps the index, and returns updated [LargeFileSession].
     */
    suspend fun applyBulkEdit(
        session: LargeFileSession,
        spec: BulkEditSpec,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<BulkEditOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            val startNs = System.nanoTime()
            val nextActive = File(session.workspaceDir, "active_next.txt")
            val findRegex = if (spec.useRegex && spec.findQuery.isNotEmpty()) {
                val opts = if (spec.ignoreCase) setOf(RegexOption.IGNORE_CASE) else emptySet()
                try {
                    Regex(spec.findQuery, opts)
                } catch (e: Exception) {
                    throw IllegalArgumentException("Invalid regex pattern: ${e.message ?: spec.findQuery}", e)
                }
            } else null

            val parsedColumns = spec.columnIndices.split(',', ' ', ';', '|')
                .mapNotNull { it.trim().toIntOrNull()?.minus(1) }
                .filter { it >= 0 }

            var affectedCount = 0
            var totalOutLines = 0
            var totalOutBytes = 0L
            val pageOffsetsList = ArrayList<Long>(2048)

            if (spec.type == BulkEditType.SORT_ASC) {
                // External merge sort on active stream (supports 200 MB!)
                val stagedPlain = File(session.workspaceDir, "sort_in.txt")
                BufferedOutputStream(FileOutputStream(stagedPlain), STREAM_BUFFER_BYTES).use { out ->
                    val nl = byteArrayOf('\n'.code.toByte())
                    forEachActiveLine(session) { _, _, line ->
                        coroutineContext.ensureActive()
                        out.write(line.toByteArray(StandardCharsets.UTF_8))
                        out.write(nl)
                    }
                    out.flush()
                }
                val (sortedLines, sortedBytes, sortedOffsets) = externalSortFile(
                    inputFile = stagedPlain,
                    outputFile = nextActive,
                    workDir = session.workspaceDir,
                    keySelector = { it.lowercase(Locale.US) },
                    descending = false,
                    onProgress = onProgress
                )
                stagedPlain.delete()
                totalOutLines = sortedLines
                totalOutBytes = sortedBytes
                sortedOffsets.forEach { pageOffsetsList.add(it) }
                affectedCount = sortedLines
            } else {
                BufferedOutputStream(FileOutputStream(nextActive), STREAM_BUFFER_BYTES).use { out ->
                    val nl = byteArrayOf('\n'.code.toByte())
                    var processed = 0
                    forEachActiveLine(session) { _, _, rawLine ->
                        coroutineContext.ensureActive()
                        processed++

                        var keep = true
                        var transformed = rawLine

                        when (spec.type) {
                            BulkEditType.FIND_REPLACE -> {
                                if (spec.findQuery.isNotEmpty()) {
                                    transformed = if (findRegex != null) {
                                        val replaced = ListToolkitEngine.safeRegexReplace(findRegex, rawLine, spec.replaceWith)
                                        if (replaced != rawLine) affectedCount++
                                        replaced
                                    } else {
                                        val replaced = rawLine.replace(spec.findQuery, spec.replaceWith, ignoreCase = spec.ignoreCase)
                                        if (replaced != rawLine) affectedCount++
                                        replaced
                                    }
                                }
                            }
                            BulkEditType.PREFIX_SUFFIX -> {
                                if (rawLine.isNotBlank()) {
                                    transformed = "${spec.prefix}$rawLine${spec.suffix}"
                                    affectedCount++
                                }
                            }
                            BulkEditType.TRIM_DROP_EMPTY -> {
                                val trimmed = rawLine.trim()
                                if (trimmed.isEmpty()) {
                                    keep = false
                                    affectedCount++
                                } else {
                                    if (trimmed != rawLine) affectedCount++
                                    transformed = trimmed
                                }
                            }
                            BulkEditType.CASE_LOWER -> {
                                transformed = rawLine.lowercase(Locale.US)
                                if (transformed != rawLine) affectedCount++
                            }
                            BulkEditType.CASE_UPPER -> {
                                transformed = rawLine.uppercase(Locale.US)
                                if (transformed != rawLine) affectedCount++
                            }
                            BulkEditType.NORMALIZE_DELIMITER -> {
                                val delim = spec.targetDelimiter.ifEmpty { "|" }
                                val parts = rawLine.split('|', ':', ';', '/', '\t').map { it.trim() }
                                val joined = parts.joinToString(delim)
                                if (joined != rawLine) affectedCount++
                                transformed = joined
                            }
                            BulkEditType.EXTRACT_COLUMNS -> {
                                val parts = rawLine.split('|', ':', ';', '/', ',', '\t').map { it.trim() }
                                val sep = spec.targetDelimiter.ifEmpty { "|" }
                                transformed = if (parsedColumns.isEmpty()) {
                                    rawLine
                                } else {
                                    parsedColumns.mapNotNull { idx -> parts.getOrNull(idx) }.joinToString(sep)
                                }
                                if (transformed.isEmpty()) {
                                    keep = false
                                }
                                affectedCount++
                            }
                            BulkEditType.KEEP_MATCHING -> {
                                val matches = if (findRegex != null) {
                                    findRegex.containsMatchIn(rawLine)
                                } else {
                                    spec.findQuery.isEmpty() || rawLine.contains(spec.findQuery, ignoreCase = spec.ignoreCase)
                                }
                                if (!matches) {
                                    keep = false
                                    affectedCount++
                                }
                            }
                            BulkEditType.REMOVE_MATCHING -> {
                                val matches = if (findRegex != null) {
                                    findRegex.containsMatchIn(rawLine)
                                } else {
                                    spec.findQuery.isNotEmpty() && rawLine.contains(spec.findQuery, ignoreCase = spec.ignoreCase)
                                }
                                if (matches) {
                                    keep = false
                                    affectedCount++
                                }
                            }
                            BulkEditType.SORT_ASC -> Unit
                        }

                        if (keep) {
                            if (totalOutLines % PAGE_SIZE_LINES == 0) {
                                pageOffsetsList.add(totalOutBytes)
                            }
                            val bytes = transformed.toByteArray(StandardCharsets.UTF_8)
                            out.write(bytes)
                            out.write(nl)
                            totalOutBytes += bytes.size + 1L
                            totalOutLines++
                        }

                        if (processed % 8192 == 0 && session.totalLines > 0) {
                            onProgress(
                                (processed.toFloat() / session.totalLines.toFloat()).coerceIn(0.05f, 0.96f),
                                "Processed $processed / ${session.totalLines} lines..."
                            )
                        }
                    }
                    out.flush()
                }
            }

            if (pageOffsetsList.isEmpty()) pageOffsetsList.add(0L)

            if (session.sourceFile.exists()) {
                session.sourceFile.delete()
            }
            if (!nextActive.renameTo(session.sourceFile)) {
                nextActive.copyTo(session.sourceFile, overwrite = true)
                nextActive.delete()
            }

            val updated = session.copy(
                fileSizeBytes = session.sourceFile.length().coerceAtLeast(totalOutBytes),
                totalLines = totalOutLines,
                pageOffsets = pageOffsetsList.toLongArray(),
                editedPages = emptyMap()
            )
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(1L)
            val summary = "${spec.type.label}: $totalOutLines line(s) active ($affectedCount modified/pruned) in ${elapsedMs}ms"
            onProgress(1f, summary)
            BulkEditOutcome(
                updatedSession = updated,
                modifiedOrRemovedCount = affectedCount,
                durationMs = elapsedMs,
                summaryMessage = summary
            )
        }
    }

    /**
     * Flushes any in-memory per-page edits in [LargeFileSession.editedPages] to disk cleanly.
     */
    suspend fun commitPageEditsToDisk(
        session: LargeFileSession,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<LargeFileSession> =
        applyBulkEdit(session, BulkEditSpec(type = BulkEditType.FIND_REPLACE, findQuery = ""), onProgress)
            .map { it.updatedSession }

    /**
     * Streams the entire active [LargeFileSession] (including unsaved [editedPages]) through
     * [ListToolkitEngine]'s exact validation, extraction, dedup, filtering, formatting, and sorting
     * pipeline using bounded memory (< 32 MB even for 200 MB inputs).
     */
    suspend fun runStreamingTransform(
        session: LargeFileSession,
        options: ListToolkitOptions,
        referenceYear: Int? = null,
        referenceMonth: Int? = null,
        random: Random = Random.Default,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Pair<LargeFileSession, ToolkitTransformResult> = withContext(Dispatchers.Default) {
        val outFile = File(session.workspaceDir, "output.txt")
        val unsortedFile = if (options.sortOrder == ListSortOrder.KEEP_ORDER) outFile else File(session.workspaceDir, "output_unsorted.txt")

        val compiledFindRegex = ListToolkitEngine.compileFindRegex(options)
        val cal = Calendar.getInstance()
        val currentYear = referenceYear ?: cal.get(Calendar.YEAR)
        val currentMonth = referenceMonth ?: (cal.get(Calendar.MONTH) + 1)

        val isRaw = options.outputFormat == OutputFormat.RAW_LINES
        val isCombo = options.outputFormat == OutputFormat.EMAIL_PASS || options.outputFormat == OutputFormat.URL_USER_PASS
        val includeUsernames = options.outputFormat == OutputFormat.URL_USER_PASS
        val includePrefixes = ListToolkitEngine.parsePrefixesPublic(options.includeBinPrefixes)
        val excludePrefixes = ListToolkitEngine.parsePrefixesPublic(options.excludeBinPrefixes)
        val includeTokens = ListToolkitEngine.parseGenericTokensPublic(options.includeBinPrefixes)
        val excludeTokens = ListToolkitEngine.parseGenericTokensPublic(options.excludeBinPrefixes)

        val dedupHashSet = if (options.dedupMode != DedupMode.NONE) PrimitiveLongSet(1024) else null
        val schemeCounts = linkedMapOf<CardScheme, Int>()

        var totalInputLines = 0
        var keptCount = 0
        var removedDuplicates = 0
        var removedExpired = 0
        var removedInvalidLuhn = 0
        var removedInvalidLengthOrCvv = 0
        var removedIncomplete = 0
        var removedUnparseable = 0
        var removedByFilter = 0

        val sortNeedsExternal = options.sortOrder != ListSortOrder.KEEP_ORDER
        val maxWriteBeforeSort = if (!sortNeedsExternal && options.limitCount != null && options.limitCount > 0) {
            options.limitCount
        } else {
            Int.MAX_VALUE
        }

        val pageOffsetsList = ArrayList<Long>(2048)
        var totalBytesOut = 0L

        BufferedOutputStream(FileOutputStream(unsortedFile), STREAM_BUFFER_BYTES).use { out ->
            val nl = byteArrayOf('\n'.code.toByte())

            forEachActiveLine(session) { _, _, rawOrig ->
                coroutineContext.ensureActive()
                val rawLine = if (options.trimWhitespace) rawOrig.trim() else rawOrig
                if (rawLine.isEmpty()) return@forEachActiveLine
                totalInputLines++

                if (keptCount >= maxWriteBeforeSort) {
                    return@forEachActiveLine
                }

                if (isRaw) {
                    val edited = ListToolkitEngine.applyLineEdits(rawLine, options, compiledFindRegex)
                    if (edited.isEmpty()) {
                        removedUnparseable++
                        return@forEachActiveLine
                    }
                    val lower = edited.lowercase(Locale.US)
                    if (includeTokens.isNotEmpty() && includeTokens.none { lower.contains(it) }) {
                        removedByFilter++
                        return@forEachActiveLine
                    }
                    if (excludeTokens.isNotEmpty() && excludeTokens.any { lower.contains(it) }) {
                        removedByFilter++
                        return@forEachActiveLine
                    }
                    val dedupKey = when (options.dedupMode) {
                        DedupMode.NONE -> null
                        DedupMode.BY_PAN -> edited.substringBefore('|').substringBefore(':').lowercase(Locale.US)
                        DedupMode.BY_FULL_RECORD -> edited
                    }
                    if (dedupKey != null && dedupHashSet != null && dedupHashSet.size < MAX_DEDUP_TABLE_ENTRIES) {
                        if (!dedupHashSet.addHash(stableHash64(dedupKey))) {
                            removedDuplicates++
                            return@forEachActiveLine
                        }
                    }
                    if (!sortNeedsExternal && keptCount % PAGE_SIZE_LINES == 0) {
                        pageOffsetsList.add(totalBytesOut)
                    }
                    val bytes = edited.toByteArray(StandardCharsets.UTF_8)
                    out.write(bytes)
                    out.write(nl)
                    totalBytesOut += bytes.size + 1L
                    keptCount++
                } else if (isCombo) {
                    val extracted = if (includeUsernames) {
                        ListToolkitEngine.extractUserPassEntries(rawLine)
                    } else {
                        ListToolkitEngine.extractEmailPassEntries(rawLine)
                    }
                    if (extracted.isEmpty()) {
                        removedUnparseable++
                        return@forEachActiveLine
                    }
                    for (combo in extracted) {
                        if (keptCount >= maxWriteBeforeSort) break
                        val lowerEmail = combo.email.lowercase(Locale.US)
                        val lowerDomain = combo.domain.lowercase(Locale.US)
                        if (includeTokens.isNotEmpty() && includeTokens.none { lowerEmail.contains(it) || lowerDomain.contains(it) }) {
                            removedByFilter++
                            continue
                        }
                        if (excludeTokens.isNotEmpty() && excludeTokens.any { lowerEmail.contains(it) || lowerDomain.contains(it) }) {
                            removedByFilter++
                            continue
                        }
                        val dedupKey = when (options.dedupMode) {
                            DedupMode.BY_PAN -> lowerEmail
                            DedupMode.BY_FULL_RECORD -> "$lowerEmail:${combo.password}"
                            DedupMode.NONE -> null
                        }
                        if (dedupKey != null && dedupHashSet != null && dedupHashSet.size < MAX_DEDUP_TABLE_ENTRIES) {
                            if (!dedupHashSet.addHash(stableHash64(dedupKey))) {
                                removedDuplicates++
                                continue
                            }
                        }
                        val lineOut = ListToolkitEngine.applyLineEdits(combo.formatted, options, compiledFindRegex)
                        if (!sortNeedsExternal && keptCount % PAGE_SIZE_LINES == 0) {
                            pageOffsetsList.add(totalBytesOut)
                        }
                        val bytes = lineOut.toByteArray(StandardCharsets.UTF_8)
                        out.write(bytes)
                        out.write(nl)
                        totalBytesOut += bytes.size + 1L
                        keptCount++
                    }
                } else {
                    val parsedEntry = ListToolkitEngine.extractCardEntry(rawLine)
                    if (parsedEntry == null) {
                        if (options.removeUnparseable) {
                            removedUnparseable++
                        } else {
                            if (options.dedupMode == DedupMode.BY_FULL_RECORD && dedupHashSet != null &&
                                dedupHashSet.size < MAX_DEDUP_TABLE_ENTRIES && !dedupHashSet.addHash(stableHash64("RAW:$rawLine"))
                            ) {
                                removedDuplicates++
                            } else {
                                val lineOut = ListToolkitEngine.applyLineEdits(rawLine, options, compiledFindRegex)
                                if (!sortNeedsExternal && keptCount % PAGE_SIZE_LINES == 0) {
                                    pageOffsetsList.add(totalBytesOut)
                                }
                                val bytes = lineOut.toByteArray(StandardCharsets.UTF_8)
                                out.write(bytes)
                                out.write(nl)
                                totalBytesOut += bytes.size + 1L
                                keptCount++
                            }
                        }
                        return@forEachActiveLine
                    }
                    val nonNullEntry: ListToolkitEngine.ParsedCardEntry = parsedEntry

                    if (options.dropIncomplete && !nonNullEntry.isComplete) {
                        removedIncomplete++
                        return@forEachActiveLine
                    }

                    val entry: ListToolkitEngine.ParsedCardEntry = if (options.solveWildcards) {
                        val hasWc = nonNullEntry.pan.any { it.equals('x', ignoreCase = true) } ||
                            nonNullEntry.month.any { it.equals('x', ignoreCase = true) } ||
                            nonNullEntry.year.any { it.equals('x', ignoreCase = true) } ||
                            nonNullEntry.cvv.any { it.equals('x', ignoreCase = true) }
                        if (hasWc) {
                            ListToolkitEngine.solveEntryWildcardsPublic(nonNullEntry, random) ?: nonNullEntry
                        } else {
                            nonNullEntry
                        }
                    } else {
                        nonNullEntry
                    }

                    val hasWildcardPan = entry.pan.any { it.equals('x', ignoreCase = true) }

                    if (options.removeInvalidCvvOrLength) {
                        if (!hasWildcardPan && entry.pan.length !in entry.scheme.validLengths) {
                            removedInvalidLengthOrCvv++
                            return@forEachActiveLine
                        }
                        if (entry.cvv.isNotEmpty() &&
                            entry.cvv.none { it.equals('x', ignoreCase = true) } &&
                            entry.cvv.length != entry.scheme.cvvLength
                        ) {
                            removedInvalidLengthOrCvv++
                            return@forEachActiveLine
                        }
                    }

                    if (options.removeInvalidLuhn && !hasWildcardPan) {
                        if (!CardValidatorEngine.verifyLuhn(entry.pan)) {
                            removedInvalidLuhn++
                            return@forEachActiveLine
                        }
                    }

                    if (options.removeExpired && entry.month.isNotEmpty() && entry.year.isNotEmpty()) {
                        val mmWc = entry.month.any { it.equals('x', ignoreCase = true) }
                        val yyWc = entry.year.any { it.equals('x', ignoreCase = true) }
                        if (!mmWc && !yyWc) {
                            val m = entry.month.toIntOrNull() ?: 0
                            val y = entry.year.toIntOrNull() ?: 0
                            val expired = m !in 1..12 || y < currentYear || (y == currentYear && m < currentMonth) || y > currentYear + 30
                            if (expired) {
                                removedExpired++
                                return@forEachActiveLine
                            }
                        }
                    }

                    if (options.selectedSchemes.isNotEmpty() && entry.scheme !in options.selectedSchemes) {
                        removedByFilter++
                        return@forEachActiveLine
                    }

                    val panDigits = entry.pan.filter { it.isDigit() }
                    if (includePrefixes.isNotEmpty() && includePrefixes.none { panDigits.startsWith(it) }) {
                        removedByFilter++
                        return@forEachActiveLine
                    }
                    if (excludePrefixes.isNotEmpty() && excludePrefixes.any { panDigits.startsWith(it) }) {
                        removedByFilter++
                        return@forEachActiveLine
                    }

                    if (options.minExpiryYear != null) {
                        val ey = entry.year.toIntOrNull()
                        if (ey == null || ey < options.minExpiryYear) {
                            removedByFilter++
                            return@forEachActiveLine
                        }
                    }

                    val dedupKey = when {
                        options.outputFormat == OutputFormat.BIN_ONLY -> entry.pan.take(6)
                        options.dedupMode == DedupMode.BY_PAN -> entry.pan
                        options.dedupMode == DedupMode.BY_FULL_RECORD -> "${entry.pan}|${entry.month}|${entry.year}|${entry.cvv}"
                        else -> null
                    }
                    if (dedupKey != null && dedupHashSet != null && dedupHashSet.size < MAX_DEDUP_TABLE_ENTRIES) {
                        if (!dedupHashSet.addHash(stableHash64(dedupKey))) {
                            removedDuplicates++
                            return@forEachActiveLine
                        }
                    }

                    val formattedLine = ListToolkitEngine.applyLineEdits(
                        line = ListToolkitEngine.formatEntry(entry, options.outputFormat),
                        options = options,
                        compiledFindRegex = compiledFindRegex
                    )
                    if (!sortNeedsExternal && keptCount % PAGE_SIZE_LINES == 0) {
                        pageOffsetsList.add(totalBytesOut)
                    }
                    val bytes = formattedLine.toByteArray(StandardCharsets.UTF_8)
                    out.write(bytes)
                    out.write(nl)
                    totalBytesOut += bytes.size + 1L
                    keptCount++
                    schemeCounts[entry.scheme] = (schemeCounts[entry.scheme] ?: 0) + 1
                }

                if (totalInputLines % 8192 == 0 && session.totalLines > 0) {
                    onProgress(
                        (totalInputLines.toFloat() / session.totalLines.toFloat()).coerceIn(0.05f, 0.85f),
                        "Transforming $totalInputLines / ${session.totalLines} lines ($keptCount kept)"
                    )
                }
            }
            out.flush()
        }

        // External K-Way Merge Sort / Partitioned Limit if sortOrder != KEEP_ORDER
        var finalOutCount = keptCount
        var finalOutBytes = totalBytesOut
        if (sortNeedsExternal) {
            onProgress(0.88f, "External disk-sorting $keptCount lines...")
            val (sortedCount, sortedBytes, sortedOffsets) = externalSortFile(
                inputFile = unsortedFile,
                outputFile = outFile,
                workDir = session.workspaceDir,
                keySelector = { line ->
                    when (options.sortOrder) {
                        ListSortOrder.BY_BIN_ASC -> line.lowercase(Locale.US)
                        ListSortOrder.BY_EXPIRY_DESC -> sortExpiryKey(line)
                        ListSortOrder.BY_SCHEME -> sortSchemeKey(line)
                        ListSortOrder.SHUFFLE -> String.format(Locale.US, "%016x", stableHash64("$line:${random.nextLong()}"))
                        ListSortOrder.KEEP_ORDER -> line
                    }
                },
                descending = options.sortOrder == ListSortOrder.BY_EXPIRY_DESC,
                limitCount = options.limitCount,
                onProgress = { pct, label -> onProgress(0.88f + pct * 0.1f, label) }
            )
            unsortedFile.delete()
            finalOutCount = sortedCount
            finalOutBytes = sortedBytes
            pageOffsetsList.clear()
            sortedOffsets.forEach { pageOffsetsList.add(it) }
        }

        if (pageOffsetsList.isEmpty()) pageOffsetsList.add(0L)
        val finalOffsets = pageOffsetsList.toLongArray()

        // Only populate outputText in RAM if safe; otherwise store first page preview for instant UI render!
        val previewLines = readPage(
            file = outFile,
            pageOffsets = finalOffsets,
            totalLines = finalOutCount,
            pageIndex = 0,
            pageSize = if (finalOutCount <= 1_500) finalOutCount.coerceAtLeast(1) else 50
        )

        val updatedSession = session.copy(
            outputFile = outFile,
            outputSizeBytes = outFile.length().coerceAtLeast(finalOutBytes),
            outputTotalLines = finalOutCount,
            outputPageOffsets = finalOffsets
        )

        val result = ToolkitTransformResult(
            outputText = previewLines.joinToString("\n"),
            totalInputLines = totalInputLines,
            outputCount = finalOutCount,
            removedDuplicates = removedDuplicates,
            removedExpired = removedExpired,
            removedInvalidLuhn = removedInvalidLuhn,
            removedInvalidLengthOrCvv = removedInvalidLengthOrCvv,
            removedIncomplete = removedIncomplete,
            removedUnparseable = removedUnparseable,
            removedByFilter = removedByFilter,
            schemeBreakdown = schemeCounts
        )

        onProgress(1f, "Done • $finalOutCount clean line(s)")
        updatedSession to result
    }

    /**
     * Streams either the transformed output file or edited active source file (or a specific line range
     * `[startLineZeroBased..endLineExclusive]`) straight to a user SAF [uri] using 64 KB buffers.
     */
    suspend fun streamExportToUri(
        context: Context,
        session: LargeFileSession,
        useOutputFile: Boolean,
        uri: Uri,
        startLineInclusive: Int = 0,
        endLineExclusive: Int = Int.MAX_VALUE,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val targetOut = resolver.openOutputStream(uri, "wt")
                ?: resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Unable to open destination URI for writing.")

            var linesWritten = 0
            val totalExpected = if (useOutputFile) session.outputTotalLines else session.totalLines
            val nl = byteArrayOf('\n'.code.toByte())

            BufferedOutputStream(targetOut, STREAM_BUFFER_BYTES).use { bos ->
                var currentLine = 0
                if (useOutputFile) {
                    val outFile = session.outputFile
                    if (outFile != null && outFile.exists()) {
                        BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(outFile), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8), STREAM_BUFFER_BYTES).use { reader ->
                            while (true) {
                                coroutineContext.ensureActive()
                                val line = reader.readLine() ?: break
                                if (currentLine in startLineInclusive until endLineExclusive) {
                                    bos.write(line.toByteArray(StandardCharsets.UTF_8))
                                    bos.write(nl)
                                    linesWritten++
                                }
                                currentLine++
                                if (currentLine >= endLineExclusive) break
                                if (currentLine % 10_000 == 0 && totalExpected > 0) {
                                    onProgress((currentLine.toFloat() / totalExpected).coerceIn(0.05f, 0.98f), "Writing $linesWritten lines...")
                                }
                            }
                        }
                    }
                } else {
                    forEachActiveLine(session) { _, _, line ->
                        coroutineContext.ensureActive()
                        if (currentLine in startLineInclusive until endLineExclusive) {
                            bos.write(line.toByteArray(StandardCharsets.UTF_8))
                            bos.write(nl)
                            linesWritten++
                        }
                        currentLine++
                        if (currentLine % 10_000 == 0 && totalExpected > 0) {
                            onProgress((currentLine.toFloat() / totalExpected).coerceIn(0.05f, 0.98f), "Writing $linesWritten lines...")
                        }
                    }
                }
                bos.flush()
            }

            onProgress(1f, "Saved $linesWritten line(s)")
            linesWritten
        }
    }

    /**
     * Streams up to [maxLines] lines from output/source file cleanly for console loading without OOM.
     */
    suspend fun readHeadLines(
        file: File?,
        maxLines: Int = MAX_SAFE_CONSOLE_LINES
    ): List<String> = withContext(Dispatchers.IO) {
        if (file == null || !file.exists()) return@withContext emptyList()
        val list = ArrayList<String>(minOf(maxLines, 256))
        BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(file), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8), 32 * 1024).use { reader ->
            while (list.size < maxLines) {
                val line = reader.readLine() ?: break
                if (line.isNotBlank()) list.add(line)
            }
        }
        list
    }

    /**
     * Reads up to [maxBytes] / [maxLines] from [file] so Android clipboard Binder copies never throw
     * TransactionTooLargeException. Returns Triple(text, copiedLineCount, wasTruncated).
     */
    suspend fun readSliceForClipboard(
        file: File?,
        maxBytes: Int = MAX_SAFE_CLIPBOARD_BYTES,
        maxLines: Int = MAX_SAFE_CONSOLE_LINES
    ): Triple<String, Int, Boolean> = withContext(Dispatchers.IO) {
        if (file == null || !file.exists()) return@withContext Triple("", 0, false)
        val sb = StringBuilder()
        var linesRead = 0
        var truncated = false
        BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(file), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8), 32 * 1024).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) continue
                val addLen = line.length + if (linesRead > 0) 1 else 0
                if (linesRead >= maxLines || sb.length + addLen > maxBytes) {
                    truncated = true
                    break
                }
                if (linesRead > 0) sb.append('\n')
                sb.append(line)
                linesRead++
            }
        }
        Triple(sb.toString(), linesRead, truncated)
    }

    private suspend inline fun forEachActiveLine(
        session: LargeFileSession,
        crossinline consumer: suspend (pageIdx: Int, lineInPage: Int, lineText: String) -> Unit
    ) {
        if (session.totalLines <= 0 || !session.sourceFile.exists()) return
        val totalPages = session.totalPages
        if (session.editedPages.isEmpty()) {
            BufferedReader(
                InputStreamReader(BufferedInputStream(FileInputStream(session.sourceFile), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8),
                STREAM_BUFFER_BYTES
            ).use { reader ->
                var globalIdx = 0
                while (true) {
                    val line = reader.readLine() ?: break
                    val pageIdx = globalIdx / session.pageSize
                    val lineInPage = globalIdx % session.pageSize
                    consumer(pageIdx, lineInPage, line)
                    globalIdx++
                }
            }
        } else {
            for (pageIdx in 0 until totalPages) {
                val pageLines = readPage(
                    file = session.sourceFile,
                    pageOffsets = session.pageOffsets,
                    totalLines = session.totalLines,
                    pageIndex = pageIdx,
                    pageSize = session.pageSize,
                    overlayEdits = session.editedPages
                )
                pageLines.forEachIndexed { lineIdx, lineText ->
                    consumer(pageIdx, lineIdx, lineText)
                }
            }
        }
    }

    private suspend fun externalSortFile(
        inputFile: File,
        outputFile: File,
        workDir: File,
        keySelector: (String) -> String,
        descending: Boolean,
        limitCount: Int? = null,
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): Triple<Int, Long, LongArray> = withContext(Dispatchers.IO) {
        data class IndexedRunLine(val key: String, val line: String)
        val comparator = if (descending) {
            Comparator<IndexedRunLine> { a, b -> b.key.compareTo(a.key) }
        } else {
            Comparator<IndexedRunLine> { a, b -> a.key.compareTo(b.key) }
        }

        val runFiles = ArrayList<File>()
        val chunkBuffer = ArrayList<IndexedRunLine>(minOf(EXTERNAL_SORT_RUN_LINES, 16_384))
        var runSeq = 0

        BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(inputFile), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8), STREAM_BUFFER_BYTES).use { reader ->
            while (true) {
                coroutineContext.ensureActive()
                val line = reader.readLine() ?: break
                chunkBuffer.add(IndexedRunLine(keySelector(line), line))
                if (chunkBuffer.size >= EXTERNAL_SORT_RUN_LINES) {
                    chunkBuffer.sortWith(comparator)
                    val runFile = File(workDir, "sort_run_${runSeq++}.tmp")
                    BufferedWriter(OutputStreamWriter(BufferedOutputStream(FileOutputStream(runFile), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8)).use { bw ->
                        for (item in chunkBuffer) {
                            writeExternalRunLine(bw, item.key, item.line)
                        }
                    }
                    runFiles.add(runFile)
                    chunkBuffer.clear()
                }
            }
        }

        if (chunkBuffer.isNotEmpty() || runFiles.isEmpty()) {
            chunkBuffer.sortWith(comparator)
            if (runFiles.isEmpty()) {
                // Fast single-chunk path without k-way merge overhead!
                val offsets = ArrayList<Long>(64)
                var outBytes = 0L
                var count = 0
                val maxOut = limitCount?.takeIf { it > 0 } ?: Int.MAX_VALUE
                BufferedOutputStream(FileOutputStream(outputFile), STREAM_BUFFER_BYTES).use { out ->
                    val nl = byteArrayOf('\n'.code.toByte())
                    for (item in chunkBuffer) {
                        if (count >= maxOut) break
                        if (count % PAGE_SIZE_LINES == 0) offsets.add(outBytes)
                        val bytes = item.line.toByteArray(StandardCharsets.UTF_8)
                        out.write(bytes)
                        out.write(nl)
                        outBytes += bytes.size + 1L
                        count++
                    }
                    out.flush()
                }
                if (offsets.isEmpty()) offsets.add(0L)
                return@withContext Triple(count, outBytes, offsets.toLongArray())
            } else {
                val runFile = File(workDir, "sort_run_${runSeq++}.tmp")
                BufferedWriter(OutputStreamWriter(BufferedOutputStream(FileOutputStream(runFile), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8)).use { bw ->
                    for (item in chunkBuffer) {
                        writeExternalRunLine(bw, item.key, item.line)
                    }
                }
                runFiles.add(runFile)
                chunkBuffer.clear()
            }
        }

        // K-way merge using min/max PriorityQueue
        data class MergeCursor(val key: String, val line: String, val reader: BufferedReader)
        val pq = PriorityQueue<MergeCursor>(runFiles.size) { a, b ->
            if (descending) b.key.compareTo(a.key) else a.key.compareTo(b.key)
        }
        val openReaders = ArrayList<BufferedReader>(runFiles.size)
        val offsets = ArrayList<Long>(2048)
        var outBytes = 0L
        var count = 0
        val maxOut = limitCount?.takeIf { it > 0 } ?: Int.MAX_VALUE

        try {
            for (rf in runFiles) {
                val br = BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(rf), 16 * 1024), StandardCharsets.UTF_8), 16 * 1024)
                openReaders.add(br)
                val first = br.readLine() ?: continue
                val (k, v) = decodeExternalRunLine(first)
                pq.add(MergeCursor(k, v, br))
            }

            BufferedOutputStream(FileOutputStream(outputFile), STREAM_BUFFER_BYTES).use { out ->
                val nl = byteArrayOf('\n'.code.toByte())
                while (pq.isNotEmpty() && count < maxOut) {
                    coroutineContext.ensureActive()
                    val head = pq.poll() ?: break
                    if (count % PAGE_SIZE_LINES == 0) offsets.add(outBytes)
                    val bytes = head.line.toByteArray(StandardCharsets.UTF_8)
                    out.write(bytes)
                    out.write(nl)
                    outBytes += bytes.size + 1L
                    count++

                    val nextRaw = head.reader.readLine()
                    if (nextRaw != null) {
                        val (k, v) = decodeExternalRunLine(nextRaw)
                        pq.add(MergeCursor(k, v, head.reader))
                    }
                    if (count % 16_384 == 0) {
                        onProgress(0.6f, "Merging sorted runs ($count lines)...")
                    }
                }
                out.flush()
            }
        } finally {
            openReaders.forEach { runCatching { it.close() } }
            runFiles.forEach { runCatching { it.delete() } }
        }

        if (offsets.isEmpty()) offsets.add(0L)
        Triple(count, outBytes, offsets.toLongArray())
    }

    /**
     * Writes an external sort run record using length-prefixed framing (`<keyLen>:<key><line>`)
     * so arbitrary characters (including control bytes) inside keys or lines never collide.
     */
    private fun writeExternalRunLine(bw: BufferedWriter, key: String, line: String) {
        bw.write(key.length.toString())
        bw.write(':'.code)
        bw.write(key)
        bw.write(line)
        bw.newLine()
    }

    private fun decodeExternalRunLine(raw: String): Pair<String, String> {
        val colonIdx = raw.indexOf(':')
        if (colonIdx > 0) {
            val keyLen = raw.substring(0, colonIdx).toIntOrNull()
            val startIdx = colonIdx + 1
            if (keyLen != null && keyLen >= 0 && startIdx + keyLen <= raw.length) {
                return raw.substring(startIdx, startIdx + keyLen) to raw.substring(startIdx + keyLen)
            }
        }
        return raw to raw
    }

    private fun copyAndIndexFile(
        src: File,
        dst: File,
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Long, LongArray> {
        var totalLines = 0
        var totalBytes = 0L
        val offsets = ArrayList<Long>(2048)
        val totalSize = src.length().coerceAtLeast(1L)
        BufferedReader(InputStreamReader(BufferedInputStream(FileInputStream(src), STREAM_BUFFER_BYTES), StandardCharsets.UTF_8), STREAM_BUFFER_BYTES).use { reader ->
            BufferedOutputStream(FileOutputStream(dst), STREAM_BUFFER_BYTES).use { out ->
                val nl = byteArrayOf('\n'.code.toByte())
                while (true) {
                    val line = reader.readLine() ?: break
                    if (totalLines % PAGE_SIZE_LINES == 0) offsets.add(totalBytes)
                    val bytes = line.toByteArray(StandardCharsets.UTF_8)
                    out.write(bytes)
                    out.write(nl)
                    totalBytes += bytes.size + 1L
                    totalLines++
                    if (totalLines % 8192 == 0) {
                        onProgress((totalBytes.toFloat() / totalSize).coerceIn(0.05f, 0.96f), "Indexing $totalLines lines...")
                    }
                }
                out.flush()
            }
        }
        if (offsets.isEmpty()) offsets.add(0L)
        return Triple(totalLines, totalBytes, offsets.toLongArray())
    }

    private fun writeStringAndIndex(
        rawText: String,
        dst: File,
        onProgress: (Float, String) -> Unit
    ): Triple<Int, Long, LongArray> {
        var totalLines = 0
        var totalBytes = 0L
        val offsets = ArrayList<Long>(256)
        BufferedOutputStream(FileOutputStream(dst), STREAM_BUFFER_BYTES).use { out ->
            val nl = byteArrayOf('\n'.code.toByte())
            rawText.lineSequence().forEach { line ->
                if (totalLines % PAGE_SIZE_LINES == 0) offsets.add(totalBytes)
                val bytes = line.toByteArray(StandardCharsets.UTF_8)
                out.write(bytes)
                out.write(nl)
                totalBytes += bytes.size + 1L
                totalLines++
                if (totalLines % 8192 == 0) {
                    onProgress(0.5f, "Indexed $totalLines lines...")
                }
            }
            out.flush()
        }
        if (offsets.isEmpty()) offsets.add(0L)
        return Triple(totalLines, totalBytes, offsets.toLongArray())
    }

    private fun stripHtmlLineStreaming(rawLine: String, initialInScriptOrStyle: Boolean): Pair<List<String>, Boolean> {
        var inIgnored = initialInScriptOrStyle
        var work = rawLine

        if (inIgnored) {
            val endScript = Regex("(?i)</(?:script|style|head)>").find(work)
            if (endScript == null) {
                return emptyList<String>() to true
            }
            work = work.substring(endScript.range.last + 1)
            inIgnored = false
        }

        // Remove inline script/style/head/comment blocks on this line
        work = work
            .replace(Regex("(?is)<!--.*?-->"), "\n")
            .replace(Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>"), "\n")

        val openTag = Regex("(?is)<(script|style|head)[^>]*>").find(work)
        if (openTag != null) {
            work = work.substring(0, openTag.range.first)
            inIgnored = true
        }

        if (work.isBlank()) return emptyList<String>() to inIgnored

        val cleanedText = FileTextExtractor.stripHtmlToText(work)
        val lines = cleanedText.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return lines to inIgnored
    }

    private fun sortExpiryKey(line: String): String {
        val parts = line.split('|', ':', '/', ',')
        val mm = parts.getOrNull(1)?.toIntOrNull()
        val yy = parts.getOrNull(2)?.toIntOrNull()?.let { if (it in 0..99) 2000 + it else it }
        if (mm != null && yy != null && mm in 1..12) {
            return String.format(Locale.US, "%04d%02d", yy, mm)
        }
        return String.format(Locale.US, "%06d", line.length)
    }

    private fun sortSchemeKey(line: String): String {
        val pan = line.substringBefore('|').substringBefore(':').substringBefore('/')
        val scheme = CardValidatorEngine.detectScheme(pan)
        return "${scheme.displayName}:$line"
    }

    /**
     * 64-bit FNV-1a mixed with length & splitmix64 finalizer for low-collision memory-efficient deduping.
     */
    internal fun stableHash64(text: String): Long {
        var h = -0x340d631b7bdddcdbL // 0xcbf29ce484222325UL
        val prime = 0x100000001b3L
        for (i in 0 until text.length) {
            h = (h xor text[i].code.toLong()) * prime
        }
        h = h xor (text.length.toLong() shl 32)
        h = (h xor (h ushr 30)) * -0x40a7b892e31b1a47L // 0xbf58476d1ce4e5b9UL
        h = (h xor (h ushr 27)) * -0x6b2fb644ecceee15L // 0x94d049bb133111ebUL
        return h xor (h ushr 31)
    }

    /**
     * Compact open-addressing primitive [Long] hash set (~8 bytes/entry vs ~96+ bytes per boxed String).
     */
    private class PrimitiveLongSet(initialCapacity: Int = 1024) {
        private var table: LongArray = LongArray(nextPow2(initialCapacity.coerceAtLeast(16)))
        private var mask: Int = table.size - 1
        private var threshold: Int = (table.size * 0.7f).toInt()
        private var hasZero: Boolean = false
        var size: Int = 0
            private set

        fun addHash(key: Long): Boolean {
            if (key == 0L) {
                if (hasZero) return false
                hasZero = true
                size++
                return true
            }
            if (size >= threshold) rehash()
            var idx = mixIndex(key) and mask
            while (true) {
                val existing = table[idx]
                if (existing == 0L) {
                    table[idx] = key
                    size++
                    return true
                }
                if (existing == key) return false
                idx = (idx + 1) and mask
            }
        }

        private fun rehash() {
            val newCap = table.size shl 1
            if (newCap <= 0) return
            val newTable = LongArray(newCap)
            val newMask = newCap - 1
            for (v in table) {
                if (v != 0L) {
                    var idx = mixIndex(v) and newMask
                    while (newTable[idx] != 0L) {
                        idx = (idx + 1) and newMask
                    }
                    newTable[idx] = v
                }
            }
            table = newTable
            mask = newMask
            threshold = (newCap * 0.7f).toInt()
        }

        private fun mixIndex(k: Long): Int {
            val x = (k xor (k ushr 32)).toInt()
            return x xor (x ushr 16)
        }

        private fun nextPow2(n: Int): Int {
            var v = n - 1
            v = v or (v ushr 1)
            v = v or (v ushr 2)
            v = v or (v ushr 4)
            v = v or (v ushr 8)
            v = v or (v ushr 16)
            return (v + 1).coerceAtLeast(16)
        }
    }
}
