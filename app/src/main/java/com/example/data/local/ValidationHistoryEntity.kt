package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "validation_history",
    indices = [
        Index(value = ["batchId"]),
        Index(value = ["isSuccess"]),
        Index(value = ["checkedAtMillis"])
    ]
)
data class ValidationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val batchId: String,
    val sourceName: String,
    val rawLine: String,
    val normalizedLine: String,
    val pan: String,
    val month: String,
    val year: String,
    val cvv: String,
    val scheme: String,
    val status: String, // LIVE, DIE, UNKNOWN
    val isSuccess: Boolean,
    val message: String,
    val latencyMs: Long,
    val endpointUsed: String,
    val checkedAtMillis: Long = System.currentTimeMillis()
)
