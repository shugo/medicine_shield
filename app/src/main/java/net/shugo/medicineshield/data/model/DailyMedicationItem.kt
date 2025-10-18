package net.shugo.medicineshield.data.model

data class DailyMedicationItem(
    val medicationId: Long,
    val medicationName: String,
    val sequenceNumber: Int,  // MedicationTimeのsequenceNumber（頓服の場合は服用回数の連番）
    val scheduledTime: String,  // 表示用の時刻（HH:mm）（頓服の場合は空文字列）
    val dose: Double = 1.0,  // 服用量
    val doseUnit: String? = null,  // 服用量の単位
    val status: MedicationIntakeStatus,  // 服用状態（未服用・服用済み・キャンセル済み）
    val takenAt: Long? = null,  // 服用時刻（statusがTAKENの場合のみ有効）
    val isAsNeeded: Boolean = false  // 頓服薬フラグ
)
