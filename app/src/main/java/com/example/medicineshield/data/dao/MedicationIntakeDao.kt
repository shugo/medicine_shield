package com.example.medicineshield.data.dao

import androidx.room.*
import com.example.medicineshield.data.model.MedicationIntake
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationIntakeDao {
    @Insert
    suspend fun insert(intake: MedicationIntake): Long

    @Update
    suspend fun update(intake: MedicationIntake)

    @Delete
    suspend fun delete(intake: MedicationIntake)

    @Query("SELECT * FROM medication_intakes WHERE scheduledDate = :date ORDER BY scheduledTime ASC")
    fun getIntakesByDate(date: String): Flow<List<MedicationIntake>>

    @Query("SELECT * FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate = :date AND scheduledTime = :time")
    suspend fun getIntakeByMedicationAndDateTime(
        medicationId: Long,
        date: String,
        time: String
    ): MedicationIntake?

    @Query("DELETE FROM medication_intakes WHERE scheduledDate < :date")
    suspend fun deleteOldIntakes(date: String)
}
