package net.shugo.medicineshield.data.repository

import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationTime
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.model.DailyMedicationItem
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

    suspend fun getMedicationWithCurrentTimesById(medicationId: Long): MedicationWithTimes? {
        val medication = medicationDao.getMedicationById(medicationId) ?: return null
        val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medicationId)
        return MedicationWithTimes(medication, currentTimes)
    }

    suspend fun insertMedicationWithTimes(medication: Medication, times: List<String>): Long {
        val medicationId = medicationDao.insert(medication)
        val today = normalizeToStartOfDay(System.currentTimeMillis())
        val medicationTimes = times.map { time ->
            MedicationTime(
                medicationId = medicationId,
                time = time,
                startDate = today,
                endDate = null
            )
        }
        medicationTimeDao.insertAll(medicationTimes)
        return medicationId
    }

    suspend fun updateMedicationWithTimes(medication: Medication, times: List<String>) {
        // 1. 薬情報を更新
        medicationDao.update(medication.copy(updatedAt = System.currentTimeMillis()))

        // 2. 現在有効な時刻を取得（endDate = null）
        val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medication.id)
        val currentTimeStrings = currentTimes.map { it.time }.toSet()
        val newTimeStrings = times.toSet()

        val today = normalizeToStartOfDay(System.currentTimeMillis())

        // 3. 削除する時刻（endDateを設定）
        val timesToEnd = currentTimes.filter { it.time !in newTimeStrings }
        timesToEnd.forEach {
            medicationTimeDao.update(
                it.copy(
                    endDate = today,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 4. 追加する時刻
        val timesToAdd = newTimeStrings.filter { it !in currentTimeStrings }
        val newMedicationTimes = timesToAdd.map { time ->
            MedicationTime(
                medicationId = medication.id,
                time = time,
                startDate = today,
                endDate = null
            )
        }
        if (newMedicationTimes.isNotEmpty()) {
            medicationTimeDao.insertAll(newMedicationTimes)
        }
    }

    suspend fun deleteMedication(medication: Medication) {
        medicationDao.delete(medication)
    }

    suspend fun deleteMedicationById(medicationId: Long) {
        medicationDao.deleteById(medicationId)
    }

    // ========== Daily Medication Functions ==========

    /**
     * 指定された日付の薬リストを取得する
     */
    fun getMedications(dateString: String): Flow<List<DailyMedicationItem>> {
        val medications = medicationDao.getAllMedications()
        val medicationTimes = medicationTimeDao.getAllTimesFlow()
        val intakes = medicationIntakeDao.getIntakesByDate(dateString)

        return combine(medications, medicationTimes, intakes) { medList, allTimes, intakeList ->
            val dailyItems = mutableListOf<DailyMedicationItem>()
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.scheduledTime}" }

            // dateStringをタイムスタンプに変換
            val targetDate = parseDateString(dateString)

            // 薬IDごとに時刻をグループ化
            val timesByMedicationId = allTimes.groupBy { it.medicationId }

            for (medication in medList) {
                if (shouldTakeMedication(medication, targetDate)) {
                    // 対象日に有効な時刻を取得
                    val allTimesForMed = timesByMedicationId[medication.id] ?: emptyList()
                    val validTimes = allTimesForMed.filter { medTime ->
                        medTime.startDate <= targetDate &&
                        (medTime.endDate == null || medTime.endDate > targetDate)
                    }

                    for (medTime in validTimes) {
                        val key = "${medication.id}_${medTime.time}"
                        val intake = intakeMap[key]

                        dailyItems.add(
                            DailyMedicationItem(
                                medicationId = medication.id,
                                medicationName = medication.name,
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
     * 指定された日付にその薬を飲むべきかどうかを判定する
     */
    private fun shouldTakeMedication(medication: Medication, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // 日付のみで比較するため、時刻を00:00:00にリセット
        val normalizedTargetDate = normalizeToStartOfDay(targetDate)
        val normalizedStartDate = normalizeToStartOfDay(medication.startDate)
        val normalizedEndDate = medication.endDate?.let { normalizeToStartOfDay(it) }

        // 期間チェック
        if (normalizedTargetDate < normalizedStartDate) return false
        if (normalizedEndDate != null && normalizedTargetDate > normalizedEndDate) return false

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
                val daysSinceStart = ((normalizedTargetDate - normalizedStartDate) / (1000 * 60 * 60 * 24)).toInt()
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
     * タイムスタンプを日付の開始時刻(00:00:00.000)に正規化
     */
    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
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
