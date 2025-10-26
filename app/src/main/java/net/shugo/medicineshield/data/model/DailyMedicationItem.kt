package net.shugo.medicineshield.data.model

data class DailyMedicationItem(
    val medicationId: Long,
    val medicationName: String,
    val sequenceNumber: Int,  // MedicationTime's sequenceNumber (for PRN, sequential number of intakes)
    val scheduledTime: String,  // Display time (HH:mm) (empty string for PRN)
    val dose: Double = 1.0,  // Dosage
    val doseUnit: String? = null,  // Dosage unit
    val status: MedicationIntakeStatus,  // Intake status (unchecked, taken, canceled)
    val takenAt: String? = null,  // Taken time HH:mm format (valid only when status is TAKEN)
    val isAsNeeded: Boolean = false  // PRN medication flag
)
