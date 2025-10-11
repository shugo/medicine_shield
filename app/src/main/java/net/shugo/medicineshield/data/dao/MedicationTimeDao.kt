package net.shugo.medicineshield.data.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.MedicationTime

@Dao
interface MedicationTimeDao {
    @Query("SELECT * FROM medication_times WHERE medicationId = :medicationId")
    suspend fun getTimesForMedication(medicationId: Long): List<MedicationTime>

    /**
     * 全ての時刻を監視（Flowで変更を検知）
     */
    @Query("SELECT * FROM medication_times")
    fun getAllTimesFlow(): Flow<List<MedicationTime>>

    /**
     * 指定された日付に有効な時刻を取得
     * validFrom <= targetDate AND (validTo IS NULL OR validTo > targetDate)
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND validFrom <= :targetDate
        AND (validTo IS NULL OR validTo > :targetDate)
        ORDER BY sequenceNumber ASC
    """)
    suspend fun getTimesForMedicationOnDate(medicationId: Long, targetDate: Long): List<MedicationTime>

    /**
     * 現在有効な時刻を取得（validTo = null）
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND validTo IS NULL
        ORDER BY sequenceNumber ASC
    """)
    suspend fun getCurrentTimesForMedication(medicationId: Long): List<MedicationTime>

    /**
     * 指定されたmedicationの最大sequenceNumberを取得
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
}
