package net.shugo.medicineshield.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medication_intakes",
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
        Index("scheduledDate"),
        Index(value = ["medicationId", "scheduledDate", "sequenceNumber"], unique = true)
    ]
)
data class MedicationIntake(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val medicationId: Long,
    val sequenceNumber: Int,  // MedicationTimeのsequenceNumberと紐付け
    val scheduledDate: String,  // YYYY-MM-DD format (e.g., "2025-10-09")
    val takenAt: Long? = null,  // timestamp in milliseconds, null = not taken yet
    val isCanceled: Boolean = false,  // true = user intentionally canceled this intake
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
