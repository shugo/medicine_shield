package net.shugo.medicineshield.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_times",
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
data class MedicationTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val sequenceNumber: Int,  // medicationId毎の連番（1, 2, 3...）
    val time: String,  // HH:mm format (e.g., "09:00", "14:30")
    val dose: Double = 1.0,  // 服用量（デフォルト: 1.0）
    val validFrom: Long,  // このレコードが有効になった日付（タイムスタンプ）
    val validTo: Long? = null,  // このレコードが無効になった日付（null=現在も有効）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
