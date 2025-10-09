package net.shugo.medicineshield.data.model

data class DailyMedicationItem(
    val medicationId: Long,
    val medicationName: String,
    val scheduledTime: String,
    val isTaken: Boolean,
    val takenAt: Long? = null
)
