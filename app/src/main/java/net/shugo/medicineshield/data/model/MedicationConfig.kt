package net.shugo.medicineshield.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_configs",
    foreignKeys = [
        ForeignKey(
            entity = Medication::class,
            parentColumns = ["id"],
            childColumns = ["medicationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("medicationId"),
        Index(value = ["medicationId", "validFrom", "validTo"])
    ]
)
data class MedicationConfig(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val cycleType: CycleType,
    val cycleValue: String? = null,  // 曜日リスト (e.g., "0,2,4") or 日数 (e.g., "3")
    val medicationStartDate: String,  // 実際の服用開始日（yyyy-MM-dd形式、INTERVAL計算の基準日）
    val medicationEndDate: String? = null,  // 実際の服用終了日（yyyy-MM-dd形式）
    val isAsNeeded: Boolean = false,  // 頓服薬フラグ（true=頓服、false=定時薬）
    val dose: Double = 1.0,  // 服用量（頓服薬の場合はこの値を使用、定時薬の場合はMedicationTimeのデフォルト値）
    val doseUnit: String? = null,  // 服用量の単位（例: "錠", "mg", "mL"）
    val validFrom: String,  // この設定レコードが有効になった日付（yyyy-MM-dd形式、履歴管理用）
    val validTo: String? = null,  // この設定レコードが無効になった日付（yyyy-MM-dd形式、null=現在も有効）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_DOSE = 0.1
        const val MAX_DOSE = 999.9
    }
}
