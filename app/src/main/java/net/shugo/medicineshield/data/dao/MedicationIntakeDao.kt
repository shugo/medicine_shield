package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.MedicationIntake

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
    suspend fun getIntakeByMedicationAndDateAndSeq(
        medicationId: Long,
        date: String,
        sequenceNumber: Int
    ): MedicationIntake?

    @Query("SELECT * FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate = :date ORDER BY sequenceNumber ASC")
    suspend fun getIntakesByMedicationAndDate(
        medicationId: Long,
        date: String
    ): List<MedicationIntake>

    @Query("DELETE FROM medication_intakes WHERE scheduledDate <= :date")
    suspend fun deleteOldIntakes(date: String)

    @Query("DELETE FROM medication_intakes WHERE medicationId = :medicationId AND scheduledDate >= :fromDate")
    suspend fun deleteIntakesFromDate(medicationId: Long, fromDate: String)
}
