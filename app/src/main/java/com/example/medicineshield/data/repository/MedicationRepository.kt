package com.example.medicineshield.data.repository

import com.example.medicineshield.data.dao.MedicationDao
import com.example.medicineshield.data.dao.MedicationIntakeDao
import com.example.medicineshield.data.dao.MedicationTimeDao
import com.example.medicineshield.data.model.CycleType
import com.example.medicineshield.data.model.Medication
import com.example.medicineshield.data.model.MedicationIntake
import com.example.medicineshield.data.model.MedicationTime
import com.example.medicineshield.data.model.MedicationWithTimes
import com.example.medicineshield.data.model.DailyMedicationItem
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

    // ========== Daily Medication Functions ==========

    /**
     * 今日の薬リストを取得する（後方互換性のため残す）
     */
    fun getTodayMedications(): Flow<List<DailyMedicationItem>> {
        return getDailyMedications()
    }

    /**
     * 今日の薬リストを取得する
     */
    fun getDailyMedications(): Flow<List<DailyMedicationItem>> {
        return getMedicationsForDate(getCurrentDateString())
    }

    /**
     * 指定された日付の薬リストを取得する
     */
    fun getMedicationsForDate(dateString: String): Flow<List<DailyMedicationItem>> {
        val medications = medicationDao.getAllMedicationsWithTimes()
        val intakes = medicationIntakeDao.getIntakesByDate(dateString)

        return combine(medications, intakes) { medList, intakeList ->
            val dailyItems = mutableListOf<DailyMedicationItem>()
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.scheduledTime}" }

            // dateStringをタイムスタンプに変換
            val targetDate = parseDateString(dateString)

            for (medWithTimes in medList) {
                if (shouldTakeMedicationOnDate(medWithTimes.medication, targetDate)) {
                    for (medTime in medWithTimes.times) {
                        val key = "${medWithTimes.medication.id}_${medTime.time}"
                        val intake = intakeMap[key]

                        dailyItems.add(
                            DailyMedicationItem(
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

            dailyItems.sortedBy { it.scheduledTime }
        }
    }

    /**
     * 服用記録を更新する（チェック/チェック解除）
     */
    suspend fun updateIntakeStatus(
        medicationId: Long,
        scheduledTime: String,
        isTaken: Boolean,
        scheduledDate: String = getCurrentDateString()
    ) {
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, scheduledTime
        )

        if (isTaken) {
            if (existingIntake == null) {
                // 新規作成
                medicationIntakeDao.insert(
                    MedicationIntake(
                        medicationId = medicationId,
                        scheduledTime = scheduledTime,
                        scheduledDate = scheduledDate,
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
     * 今日その薬を飲むべきかどうかを判定する（後方互換性のため残す）
     */
    private fun shouldTakeMedicationToday(medication: Medication): Boolean {
        val calendar = Calendar.getInstance()
        return shouldTakeMedicationOnDate(medication, calendar.timeInMillis)
    }

    /**
     * 指定された日付にその薬を飲むべきかどうかを判定する
     */
    private fun shouldTakeMedicationOnDate(medication: Medication, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // 期間チェック
        if (targetDate < medication.startDate) return false
        if (medication.endDate != null && targetDate > medication.endDate) return false

        return when (medication.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // 曜日チェック (0=日曜, 1=月曜, ..., 6=土曜)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = medication.cycleValue?.split(",")?.map { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // N日ごとチェック
                val intervalDays = medication.cycleValue?.toIntOrNull() ?: return false
                val daysSinceStart = ((targetDate - medication.startDate) / (1000 * 60 * 60 * 24)).toInt()
                daysSinceStart % intervalDays == 0
            }
        }
    }

    /**
     * 現在の日付を YYYY-MM-DD 形式で取得
     */
    private fun getCurrentDateString(): String {
        return getDateString(System.currentTimeMillis())
    }

    /**
     * 指定されたタイムスタンプを YYYY-MM-DD 形式に変換
     */
    private fun getDateString(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    /**
     * YYYY-MM-DD 形式の文字列をタイムスタンプに変換
     */
    private fun parseDateString(dateString: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return try {
            sdf.parse(dateString)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
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
