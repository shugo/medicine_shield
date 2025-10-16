package net.shugo.medicineshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_notes")
data class DailyNote(
    @PrimaryKey
    val noteDate: String,  // YYYY-MM-DD format (e.g., "2025-10-16")
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
