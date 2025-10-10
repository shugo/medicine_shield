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
        Index(value = ["medicationId", "startDate", "endDate"])
    ]
)
data class MedicationTime(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val time: String,  // HH:mm format (e.g., "09:00", "14:30")
    val startDate: Long,  // この時刻が有効になった日付（タイムスタンプ）
    val endDate: Long? = null,  // この時刻が無効になった日付（null=現在も有効）
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
