package com.example.data.local

import com.example.domain.ResultBucket
import com.example.domain.ValidationRecord
import kotlinx.coroutines.flow.Flow

enum class HistoryFilter {
    ALL,
    SUCCESS,
    FAILURE
}

class ValidationHistoryRepository(private val dao: ValidationHistoryDao) {

    fun observeHistory(filter: HistoryFilter): Flow<List<ValidationHistoryEntity>> {
        return when (filter) {
            HistoryFilter.ALL -> dao.observeAllHistory()
            HistoryFilter.SUCCESS -> dao.observeHistoryBySuccess(true)
            HistoryFilter.FAILURE -> dao.observeHistoryBySuccess(false)
        }
    }

    fun observePagedHistory(
        filter: HistoryFilter,
        query: String,
        limit: Int
    ): Flow<List<ValidationHistoryEntity>> {
        return dao.observePagedHistory(
            filterSuccess = filter.toSqlFlag(),
            query = query.trim(),
            limit = limit.coerceAtLeast(1)
        )
    }

    fun observeFilteredCount(filter: HistoryFilter, query: String): Flow<Int> {
        return dao.observeFilteredCount(
            filterSuccess = filter.toSqlFlag(),
            query = query.trim()
        )
    }

    suspend fun getHistoryPage(
        filter: HistoryFilter,
        query: String,
        limit: Int,
        offset: Int
    ): List<ValidationHistoryEntity> {
        return dao.getHistoryPage(
            filterSuccess = filter.toSqlFlag(),
            query = query.trim(),
            limit = limit.coerceAtLeast(1),
            offset = offset.coerceAtLeast(0)
        )
    }

    fun observeBatchResults(batchId: String): Flow<List<ValidationHistoryEntity>> {
        return dao.observeHistoryByBatchId(batchId)
    }

    val totalHistoryCount: Flow<Int> = dao.observeTotalCount()
    val successHistoryCount: Flow<Int> = dao.observeSuccessCount()
    val failureHistoryCount: Flow<Int> = dao.observeFailureCount()

    suspend fun enqueueBatch(batchId: String, lines: List<String>, sourceName: String) {
        val items = lines.mapIndexed { idx, line ->
            BatchQueueItemEntity(
                batchId = batchId,
                lineIndex = idx,
                rawLine = line,
                sourceName = sourceName
            )
        }
        dao.insertQueueItemsChunked(items, chunkSize = 500)
    }

    suspend fun getQueuedBatch(batchId: String): List<BatchQueueItemEntity> {
        return dao.getQueueItemsByBatchId(batchId)
    }

    suspend fun clearQueuedBatch(batchId: String) {
        dao.deleteQueueItemsByBatchId(batchId)
    }

    suspend fun recordResult(
        batchId: String,
        sourceName: String,
        record: ValidationRecord,
        endpointUsed: String
    ) {
        dao.insertHistory(
            toEntity(
                batchId = batchId,
                sourceName = sourceName,
                record = record,
                endpointUsed = endpointUsed
            )
        )
    }

    suspend fun recordResultBatch(entries: List<ValidationHistoryEntity>) {
        if (entries.isEmpty()) return
        entries.chunked(250).forEach { chunk ->
            dao.insertHistoryBatch(chunk)
        }
    }

    fun toEntity(
        batchId: String,
        sourceName: String,
        record: ValidationRecord,
        endpointUsed: String
    ): ValidationHistoryEntity = ValidationHistoryEntity(
        batchId = batchId,
        sourceName = sourceName,
        rawLine = record.rawLine,
        normalizedLine = record.normalizedLine,
        pan = record.pan,
        month = record.month,
        year = record.year,
        cvv = record.cvv,
        scheme = record.scheme.displayName,
        status = record.bucket.name,
        isSuccess = record.bucket == ResultBucket.LIVE,
        message = record.message,
        latencyMs = record.latencyMs,
        endpointUsed = endpointUsed
    )

    suspend fun trimExcessHistory(maxKeep: Int = MAX_HISTORY_ROWS): Int {
        return dao.trimOldHistory(maxKeep.coerceAtLeast(100))
    }

    suspend fun deleteEntry(id: Long) = dao.deleteHistoryById(id)

    suspend fun clearAllHistory() = dao.clearAllHistory()

    private fun HistoryFilter.toSqlFlag(): Int = when (this) {
        HistoryFilter.ALL -> -1
        HistoryFilter.SUCCESS -> 1
        HistoryFilter.FAILURE -> 0
    }

    companion object {
        const val DEFAULT_PAGE_SIZE = 40
        const val MAX_HISTORY_ROWS = 5_000
    }
}
