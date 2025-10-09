package com.example.medicineshield.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val startDate: Long,  // timestamp in milliseconds
    val endDate: Long? = null,  // timestamp in milliseconds, nullable
    val cycleType: CycleType,
    val cycleValue: String? = null,  // 曜日リスト (e.g., "0,2,4" for Sun,Tue,Thu) or 日数 (e.g., "3")
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
