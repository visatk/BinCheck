package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ValidationHistoryDao {

    @Query("SELECT * FROM validation_history ORDER BY checkedAtMillis DESC, id DESC")
    fun observeAllHistory(): Flow<List<ValidationHistoryEntity>>

    @Query("SELECT * FROM validation_history WHERE isSuccess = :isSuccess ORDER BY checkedAtMillis DESC, id DESC")
    fun observeHistoryBySuccess(isSuccess: Boolean): Flow<List<ValidationHistoryEntity>>

    @Query("SELECT * FROM validation_history WHERE batchId = :batchId ORDER BY id ASC")
    fun observeHistoryByBatchId(batchId: String): Flow<List<ValidationHistoryEntity>>

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItems(items: List<BatchQueueItemEntity>)

    @Query("SELECT * FROM batch_queue_items WHERE batchId = :batchId ORDER BY lineIndex ASC")
    suspend fun getQueueItemsByBatchId(batchId: String): List<BatchQueueItemEntity>

    @Query("DELETE FROM batch_queue_items WHERE batchId = :batchId")
    suspend fun deleteQueueItemsByBatchId(batchId: String)
}
