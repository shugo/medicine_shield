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
    val sequenceNumber: Int,  // Sequential number per medicationId (1, 2, 3...)
    val time: String,  // HH:mm format (e.g., "09:00", "14:30")
    val dose: Double = 1.0,  // Dosage (default: 1.0)
    val validFrom: String,  // Date when this record becomes valid (yyyy-MM-dd format)
    val validTo: String = net.shugo.medicineshield.utils.DateUtils.MAX_DATE,  // Date when this record becomes invalid (yyyy-MM-dd format, default=max value)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
