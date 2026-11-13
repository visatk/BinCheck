package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BinCacheDao {

    @Query("SELECT * FROM bin_cache WHERE bin = :bin AND expiresAtMillis > :nowMillis LIMIT 1")
    suspend fun getValidBin(bin: String, nowMillis: Long): BinCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBin(entry: BinCacheEntity)

    @Query("DELETE FROM bin_cache WHERE expiresAtMillis <= :nowMillis")
    suspend fun pruneExpired(nowMillis: Long): Int

    @Query("SELECT COUNT(*) FROM bin_cache WHERE expiresAtMillis > :nowMillis")
    suspend fun countValidBins(nowMillis: Long): Int

    @Query("DELETE FROM bin_cache")
    suspend fun clearAll()
}
