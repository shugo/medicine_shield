package com.example.medicineshield.data.repository

import com.example.medicineshield.data.dao.MedicationDao
import com.example.medicineshield.data.dao.MedicationIntakeDao
import com.example.medicineshield.data.dao.MedicationTimeDao
import com.example.medicineshield.data.model.CycleType
import com.example.medicineshield.data.model.Medication
import com.example.medicineshield.data.model.MedicationIntake
import com.example.medicineshield.data.model.MedicationTime
import com.example.medicineshield.data.model.MedicationWithTimes
import com.example.medicineshield.data.model.TodayMedicationItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationTimeDao: MedicationTimeDao,
    private val medicationIntakeDao: MedicationIntakeDao
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

    // ========== Today Medication Functions ==========

    /**
     * 今日の薬リストを取得する
     */
    fun getTodayMedications(): Flow<List<TodayMedicationItem>> {
        val today = getCurrentDateString()
        val medications = medicationDao.getAllMedicationsWithTimes()
        val intakes = medicationIntakeDao.getIntakesByDate(today)

        return combine(medications, intakes) { medList, intakeList ->
            val todayItems = mutableListOf<TodayMedicationItem>()
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.scheduledTime}" }

            for (medWithTimes in medList) {
                if (shouldTakeMedicationToday(medWithTimes.medication)) {
                    for (medTime in medWithTimes.times) {
                        val key = "${medWithTimes.medication.id}_${medTime.time}"
                        val intake = intakeMap[key]

                        todayItems.add(
                            TodayMedicationItem(
                                medicationId = medWithTimes.medication.id,
                                medicationName = medWithTimes.medication.name,
                                scheduledTime = medTime.time,
                                isTaken = intake?.takenAt != null,
                                takenAt = intake?.takenAt
                            )
                        )
                    }
                }
            }

            todayItems.sortedBy { it.scheduledTime }
        }
    }

    /**
     * 服用記録を更新する（チェック/チェック解除）
     */
    suspend fun updateIntakeStatus(medicationId: Long, scheduledTime: String, isTaken: Boolean) {
        val today = getCurrentDateString()
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, today, scheduledTime
        )

        if (isTaken) {
            if (existingIntake == null) {
                // 新規作成
                medicationIntakeDao.insert(
                    MedicationIntake(
                        medicationId = medicationId,
                        scheduledTime = scheduledTime,
                        scheduledDate = today,
                        takenAt = System.currentTimeMillis()
                    )
                )
            } else {
                // 更新
                medicationIntakeDao.update(
                    existingIntake.copy(
                        takenAt = System.currentTimeMillis(),
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        } else {
            // チェック解除
            if (existingIntake != null) {
                medicationIntakeDao.update(
                    existingIntake.copy(
                        takenAt = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }

    /**
     * 今日その薬を飲むべきかどうかを判定する
     */
    private fun shouldTakeMedicationToday(medication: Medication): Boolean {
        val calendar = Calendar.getInstance()
        val today = calendar.timeInMillis

        // 期間チェック
        if (today < medication.startDate) return false
        if (medication.endDate != null && today > medication.endDate) return false

        return when (medication.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // 曜日チェック (0=日曜, 1=月曜, ..., 6=土曜)
                val todayDayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = medication.cycleValue?.split(",")?.map { it.toIntOrNull() } ?: emptyList()
                todayDayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // N日ごとチェック
                val intervalDays = medication.cycleValue?.toIntOrNull() ?: return false
                val daysSinceStart = ((today - medication.startDate) / (1000 * 60 * 60 * 24)).toInt()
                daysSinceStart % intervalDays == 0
            }
        }
    }

    /**
     * 現在の日付を YYYY-MM-DD 形式で取得
     */
    private fun getCurrentDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date())
    }

    /**
     * 古い服用記録を削除（オプション: クリーンアップ用）
     */
    suspend fun cleanupOldIntakes(daysToKeep: Int = 30) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysToKeep)
        val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        medicationIntakeDao.deleteOldIntakes(cutoffDate)
    }
}
