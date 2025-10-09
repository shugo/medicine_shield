package com.example.medicineshield.data.model

data class TodayMedicationItem(
    val medicationId: Long,
    val medicationName: String,
    val scheduledTime: String,
    val isTaken: Boolean,
    val takenAt: Long? = null
)
