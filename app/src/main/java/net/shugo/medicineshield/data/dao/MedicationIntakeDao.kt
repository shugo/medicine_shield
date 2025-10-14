package net.shugo.medicineshield.data.dao

import androidx.room.*
import net.shugo.medicineshield.data.model.MedicationIntake
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationIntakeDao {
    @Insert
    suspend fun insert(intake: MedicationIntake): Long

    @Update
    suspend fun update(intake: MedicationIntake)

    @Delete
    suspend fun delete(intake: MedicationIntake)

    @Query("SELECT * FROM medication_intakes WHERE scheduledDate = :date ORDER BY sequenceNumber ASC")
    fun getIntakesByDate(date: String): Flow<List<MedicationIntake>>

    @Query("SELECT * FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate = :date AND sequenceNumber = :sequenceNumber")
    suspend fun getIntakeByMedicationAndDateTime(
        medicationId: Long,
        date: String,
        sequenceNumber: Int
    ): MedicationIntake?

    @Query("SELECT * FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate = :date ORDER BY sequenceNumber ASC")
    suspend fun getIntakesByMedicationAndDate(
        medicationId: Long,
        date: String
    ): List<MedicationIntake>

    @Query("DELETE FROM medication_intakes WHERE scheduledDate < :date")
    suspend fun deleteOldIntakes(date: String)

    @Query("DELETE FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate >= :fromDate")
    suspend fun deleteIntakesFromDate(medicationId: Long, fromDate: String)
}
