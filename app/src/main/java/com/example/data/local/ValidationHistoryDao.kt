package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class HistoryCountRow(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int
)

@Dao
interface ValidationHistoryDao {

    @Query("SELECT * FROM validation_history ORDER BY checkedAtMillis DESC, id DESC")
    fun observeAllHistory(): Flow<List<ValidationHistoryEntity>>

    @Query("SELECT * FROM validation_history WHERE isSuccess = :isSuccess ORDER BY checkedAtMillis DESC, id DESC")
    fun observeHistoryBySuccess(isSuccess: Boolean): Flow<List<ValidationHistoryEntity>>

    @Query(
        """
        SELECT * FROM validation_history
        WHERE (:filterSuccess = -1 OR isSuccess = :filterSuccess)
          AND (
            :query = '' OR
            normalizedLine LIKE '%' || :query || '%' OR
            scheme LIKE '%' || :query || '%' OR
            sourceName LIKE '%' || :query || '%' OR
            message LIKE '%' || :query || '%'
          )
        ORDER BY checkedAtMillis DESC, id DESC
        LIMIT :limit
        """
    )
    fun observePagedHistory(
        filterSuccess: Int,
        query: String,
        limit: Int
    ): Flow<List<ValidationHistoryEntity>>

    @Query(
        """
        SELECT * FROM validation_history
        WHERE (:filterSuccess = -1 OR isSuccess = :filterSuccess)
          AND (
            :query = '' OR
            normalizedLine LIKE '%' || :query || '%' OR
            scheme LIKE '%' || :query || '%' OR
            sourceName LIKE '%' || :query || '%' OR
            message LIKE '%' || :query || '%'
          )
        ORDER BY checkedAtMillis DESC, id DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun getHistoryPage(
        filterSuccess: Int,
        query: String,
        limit: Int,
        offset: Int
    ): List<ValidationHistoryEntity>

    @Query(
        """
        SELECT COUNT(*) FROM validation_history
        WHERE (:filterSuccess = -1 OR isSuccess = :filterSuccess)
          AND (
            :query = '' OR
            normalizedLine LIKE '%' || :query || '%' OR
            scheme LIKE '%' || :query || '%' OR
            sourceName LIKE '%' || :query || '%' OR
            message LIKE '%' || :query || '%'
          )
        """
    )
    fun observeFilteredCount(filterSuccess: Int, query: String): Flow<Int>

    @Query("SELECT * FROM validation_history WHERE batchId = :batchId ORDER BY id ASC")
    fun observeHistoryByBatchId(batchId: String): Flow<List<ValidationHistoryEntity>>

    @Query("SELECT MAX(id) FROM validation_history WHERE batchId = :batchId")
    fun observeLatestBatchRowId(batchId: String): Flow<Long?>

    @Query("SELECT * FROM validation_history WHERE batchId = :batchId AND id > :afterId ORDER BY id ASC")
    suspend fun getBatchResultsAfterId(batchId: String, afterId: Long): List<ValidationHistoryEntity>

    @Query(
        """
        SELECT
            COUNT(*) AS totalCount,
            COALESCE(SUM(CASE WHEN isSuccess = 1 THEN 1 ELSE 0 END), 0) AS successCount,
            COALESCE(SUM(CASE WHEN isSuccess = 0 THEN 1 ELSE 0 END), 0) AS failureCount
        FROM validation_history
        """
    )
    fun observeHistorySummary(): Flow<HistoryCountRow>

    @Query("SELECT COUNT(*) FROM validation_history")
    fun observeTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM validation_history WHERE isSuccess = 1")
    fun observeSuccessCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM validation_history WHERE isSuccess = 0")
    fun observeFailureCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(entry: ValidationHistoryEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryBatch(entries: List<ValidationHistoryEntity>)

    @Query("DELETE FROM validation_history WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM validation_history")
    suspend fun clearAllHistory()

    @Query(
        """
        DELETE FROM validation_history
        WHERE id NOT IN (
            SELECT id FROM validation_history
            ORDER BY checkedAtMillis DESC, id DESC
            LIMIT :maxKeep
        )
        """
    )
    suspend fun trimOldHistory(maxKeep: Int): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<BatchQueueItemEntity>)

    @Transaction
    suspend fun insertQueueItemsChunked(items: List<BatchQueueItemEntity>, chunkSize: Int = 500) {
        items.chunked(chunkSize).forEach { chunk ->
            insertQueueItems(chunk)
        }
    }

    @Query("SELECT * FROM batch_queue_items WHERE batchId = :batchId ORDER BY lineIndex ASC")
    suspend fun getQueueItemsByBatchId(batchId: String): List<BatchQueueItemEntity>

    @Query("DELETE FROM batch_queue_items WHERE batchId = :batchId")
    suspend fun deleteQueueItemsByBatchId(batchId: String)
}
