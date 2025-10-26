package net.shugo.medicineshield.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import net.shugo.medicineshield.utils.DateUtils

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
    val cycleValue: String? = null,  // Day of week list (e.g., "0,2,4") or days (e.g., "3")
    val medicationStartDate: String,  // Actual medication start date (yyyy-MM-dd format, baseline for INTERVAL calculation)
    val medicationEndDate: String = DateUtils.MAX_DATE,  // Actual medication end date (yyyy-MM-dd format, default=max value)
    val isAsNeeded: Boolean = false,  // PRN medication flag (true=PRN, false=scheduled)
    val dose: Double = 1.0,  // Dosage (use this value for PRN, default value for MedicationTime for scheduled)
    val doseUnit: String? = null,  // Dosage unit (e.g., "tablet", "mg", "mL")
    val validFrom: String,  // Date when this config record becomes valid (yyyy-MM-dd format, for history management)
    val validTo: String = DateUtils.MAX_DATE,  // Date when this config record becomes invalid (yyyy-MM-dd format, default=max value)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MIN_DOSE = 0.1
        const val MAX_DOSE = 999.9
    }
}
