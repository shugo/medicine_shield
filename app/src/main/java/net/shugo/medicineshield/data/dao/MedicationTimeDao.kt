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
     * 全ての時刻を監視（Flowで変更を検知）
     */
    @Query("SELECT * FROM medication_times")
    fun getAllTimesFlow(): Flow<List<MedicationTime>>

    /**
     * 現在有効な時刻を取得（validTo > today）
     */
    @Query("""
        SELECT * FROM medication_times
        WHERE medicationId = :medicationId
        AND validTo > strftime('%Y-%m-%d', 'now')
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
