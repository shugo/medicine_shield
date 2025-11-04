package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.MedicationTime

@Dao
interface MedicationTimeDao {
    @Query("SELECT * FROM medication_times WHERE medicationId = :medicationId")
    suspend fun getTimesForMedication(medicationId: Long): List<MedicationTime>

    /**
     * Monitor all times (detect changes via Flow)
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE validFrom <= :targetDate
        AND validTo > :targetDate
    """)
    fun getAllTimesFlowOnDate(targetDate: String): Flow<List<MedicationTime>>

    /**
     * Monitor times at specific date and time (detect changes via Flow)
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE validFrom <= :targetDate
        AND validTo > :targetDate
        AND time = :targetTime
    """)
    fun getAllTimesFlowAtDateTime(targetDate: String, targetTime: String): Flow<List<MedicationTime>>

    /**
     * Get currently valid times (validTo > today)
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND validTo > strftime('%Y-%m-%d', 'now', 'localtime')
        ORDER BY sequenceNumber ASC
    """)
    suspend fun getCurrentTimesForMedication(medicationId: Long): List<MedicationTime>

    /**
     * Get the maximum sequenceNumber for the specified medication
     */
    @Query("SELECT MAX(sequenceNumber) FROM medication_times WHERE medicationId = :medicationId")
    suspend fun getMaxSequenceNumber(medicationId: Long): Int?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(medicationTime: MedicationTime): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(medicationTimes: List<MedicationTime>)

    @Update
    suspend fun update(medicationTime: MedicationTime)

    @Delete
    suspend fun delete(medicationTime: MedicationTime)

    @Query("DELETE FROM medication_times WHERE medicationId = :medicationId")
    suspend fun deleteAllForMedication(medicationId: Long)

    @Query("""
        DELETE FROM medication_times
        WHERE validTo <= :cutoffDate
    """)
    suspend fun deleteOldTimes(cutoffDate: String)
}
