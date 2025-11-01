package net.shugo.medicineshield.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import net.shugo.medicineshield.data.model.MedicationConfig

@Dao
interface MedicationConfigDao {
    @Query("""
        SELECT * FROM medication_configs
        WHERE medicationId = :medicationId
        AND validTo > strftime('%Y-%m-%d', 'now', 'localtime')
        ORDER BY validFrom DESC
        LIMIT 1
    """)
    suspend fun getCurrentConfigForMedication(medicationId: Long): MedicationConfig?

    @Query("""
        SELECT * FROM medication_configs
        WHERE medicationId = :medicationId
        AND validFrom <= :targetDate
        AND validTo > :targetDate
        ORDER BY validFrom DESC
        LIMIT 1
    """)
    suspend fun getConfigForMedicationOnDate(medicationId: Long, targetDate: Long): MedicationConfig?

    @Query("""
        SELECT * FROM medication_configs
        WHERE validFrom <= :targetDate
        AND validTo > :targetDate
    """)
    fun getAllConfigsFlowOnDate(targetDate: String): Flow<List<MedicationConfig>>

    @Insert
    suspend fun insert(config: MedicationConfig): Long

    @Update
    suspend fun update(config: MedicationConfig)

    @Delete
    suspend fun delete(config: MedicationConfig)

    @Query("""
        SELECT medicationId FROM medication_configs
        GROUP BY medicationId
        HAVING MAX(medicationEndDate) < :cutoffDate
    """)
    suspend fun getMedicationIdsEndedBefore(cutoffDate: String): List<Long>

    @Query("""
        DELETE FROM medication_configs
        WHERE validTo <= :cutoffDate
    """)
    suspend fun deleteOldConfigs(cutoffDate: String)

    @Query("""
        SELECT DISTINCT m.id
        FROM medications m
        LEFT JOIN medication_configs mc ON m.id = mc.medicationId
        WHERE mc.medicationId IS NULL
    """)
    suspend fun getMedicationIdsWithoutConfigs(): List<Long>
}
