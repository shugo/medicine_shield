package net.shugo.medicineshield.data.model

data class DailyMedicationItem(
    val medicationId: Long,
    val medicationName: String,
    val sequenceNumber: Int,  // MedicationTimeのsequenceNumber
    val scheduledTime: String,  // 表示用の時刻（HH:mm）
    val isTaken: Boolean,
    val takenAt: Long? = null
)
