package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationWithTimes

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedications(): Flow<List<Medication>>

    /**
     * Get all Medications and their relationships (including history)
     * Note: @Relation retrieves all data, so if you only need currently valid data,
     * filter in the Repository or use individual DAO methods
     */
    @Transaction
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>>

    /**
     * Get Medication and its relationships for specified ID (including history)
     * Note: @Relation retrieves all data, so if you only need currently valid data,
     * filter in the Repository or use individual DAO methods
     */
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
