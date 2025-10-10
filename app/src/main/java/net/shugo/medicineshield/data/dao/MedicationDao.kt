package net.shugo.medicineshield.data.dao

import androidx.room.*
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationWithTimes
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedications(): Flow<List<Medication>>

    @Transaction
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>>

    @Transaction
    @Query("SELECT * FROM medications WHERE id = :medicationId")
    suspend fun getMedicationWithTimesById(medicationId: Long): MedicationWithTimes?

    @Query("SELECT * FROM medications WHERE id = :medicationId")
    suspend fun getMedicationById(medicationId: Long): Medication?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medication: Medication): Long

    @Update
    suspend fun update(medication: Medication)

    @Delete
    suspend fun delete(medication: Medication)

    @Query("DELETE FROM medications WHERE id = :medicationId")
    suspend fun deleteById(medicationId: Long)
}
