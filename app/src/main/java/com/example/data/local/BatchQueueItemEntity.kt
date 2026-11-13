package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "batch_queue_items",
    indices = [Index(value = ["batchId"])]
)
data class BatchQueueItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: String,
    val lineIndex: Int,
    val rawLine: String,
    val sourceName: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)
