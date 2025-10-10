package net.shugo.medicineshield.data.dao

import androidx.room.Dao
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
        AND validTo IS NULL
        ORDER BY validFrom DESC
        LIMIT 1
    """)
    suspend fun getCurrentConfigForMedication(medicationId: Long): MedicationConfig?

    @Query("""
        SELECT * FROM medication_configs
        WHERE medicationId = :medicationId
        AND validFrom <= :targetDate
        AND (validTo IS NULL OR validTo > :targetDate)
        ORDER BY validFrom DESC
        LIMIT 1
    """)
    suspend fun getConfigForMedicationOnDate(medicationId: Long, targetDate: Long): MedicationConfig?

    @Query("SELECT * FROM medication_configs")
    fun getAllConfigsFlow(): Flow<List<MedicationConfig>>

    @Insert
    suspend fun insert(config: MedicationConfig): Long

    @Update
    suspend fun update(config: MedicationConfig)
}
