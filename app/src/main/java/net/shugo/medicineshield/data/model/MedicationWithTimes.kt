package net.shugo.medicineshield.data.model

import androidx.room.Embedded
import androidx.room.Relation

data class MedicationWithTimes(
    @Embedded val medication: Medication,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val times: List<MedicationTime>,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val configs: List<MedicationConfig> = emptyList()
)
