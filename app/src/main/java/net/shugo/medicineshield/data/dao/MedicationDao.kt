package net.shugo.medicineshield.data.dao

import androidx.room.*
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationWithTimes
import kotlinx.coroutines.flow.Flow

@Dao
interface MedicationDao {
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedications(): Flow<List<Medication>>

    @Query("SELECT COUNT(*) FROM medications")
    fun getMedicationCount(): Flow<Int>

    /**
     * すべてのMedicationとそのリレーションを取得（履歴含む）
     * 注意: @Relationは全データを取得するため、現在有効なデータのみが必要な場合は
     * Repositoryでフィルタリングするか、個別のDAOメソッドを使用してください
     */
    @Transaction
    @Query("SELECT * FROM medications ORDER BY createdAt DESC")
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>>

    /**
     * 指定IDのMedicationとそのリレーションを取得（履歴含む）
     * 注意: @Relationは全データを取得するため、現在有効なデータのみが必要な場合は
     * Repositoryでフィルタリングするか、個別のDAOメソッドを使用してください
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
