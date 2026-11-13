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
        dao.insertQueueItems(items)
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
            ValidationHistoryEntity(
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
        )
    }

    suspend fun deleteEntry(id: Long) = dao.deleteHistoryById(id)

    suspend fun clearAllHistory() = dao.clearAllHistory()
}
