package com.example.medicineshield.data.repository

import com.example.medicineshield.data.dao.MedicationDao
import com.example.medicineshield.data.dao.MedicationTimeDao
import com.example.medicineshield.data.model.Medication
import com.example.medicineshield.data.model.MedicationTime
import com.example.medicineshield.data.model.MedicationWithTimes
import kotlinx.coroutines.flow.Flow

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationTimeDao: MedicationTimeDao
) {
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>> {
        return medicationDao.getAllMedicationsWithTimes()
    }

    suspend fun getMedicationWithTimesById(medicationId: Long): MedicationWithTimes? {
        return medicationDao.getMedicationWithTimesById(medicationId)
    }

    suspend fun insertMedicationWithTimes(medication: Medication, times: List<String>): Long {
        val medicationId = medicationDao.insert(medication)
        val medicationTimes = times.map { time ->
            MedicationTime(medicationId = medicationId, time = time)
        }
        medicationTimeDao.insertAll(medicationTimes)
        return medicationId
    }

    suspend fun updateMedicationWithTimes(medication: Medication, times: List<String>) {
        medicationDao.update(medication.copy(updatedAt = System.currentTimeMillis()))
        medicationTimeDao.deleteAllForMedication(medication.id)
        val medicationTimes = times.map { time ->
            MedicationTime(medicationId = medication.id, time = time)
        }
        medicationTimeDao.insertAll(medicationTimes)
    }

    suspend fun deleteMedication(medication: Medication) {
        medicationDao.delete(medication)
    }

    suspend fun deleteMedicationById(medicationId: Long) {
        medicationDao.deleteById(medicationId)
    }
}
