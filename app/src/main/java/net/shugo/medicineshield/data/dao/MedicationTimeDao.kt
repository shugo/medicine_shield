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
     * startDate <= targetDate AND (endDate IS NULL OR endDate > targetDate)
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND startDate <= :targetDate
        AND (endDate IS NULL OR endDate > :targetDate)
        ORDER BY time ASC
    """)
    suspend fun getTimesForMedicationOnDate(medicationId: Long, targetDate: Long): List<MedicationTime>

    /**
     * 現在有効な時刻を取得（endDate = null）
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND endDate IS NULL
        ORDER BY time ASC
    """)
    suspend fun getCurrentTimesForMedication(medicationId: Long): List<MedicationTime>

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
