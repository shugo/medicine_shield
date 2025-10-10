package net.shugo.medicineshield.data.repository

import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationTime
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.utils.DateUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.text.SimpleDateFormat
import java.util.*

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationTimeDao: MedicationTimeDao,
    private val medicationIntakeDao: MedicationIntakeDao,
    private val medicationConfigDao: MedicationConfigDao
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
        val currentConfig = medicationConfigDao.getCurrentConfigForMedication(medicationId)
        val configs = if (currentConfig != null) listOf(currentConfig) else emptyList()
        return MedicationWithTimes(medication, currentTimes, configs)
    }

    suspend fun insertMedicationWithTimes(
        name: String,
        cycleType: CycleType,
        cycleValue: String?,
        startDate: Long,
        endDate: Long?,
        times: List<String>
    ): Long {
        val today = DateUtils.normalizeToStartOfDay(System.currentTimeMillis())

        // 1. Medicationを作成
        val medication = Medication(name = name)
        val medicationId = medicationDao.insert(medication)

        // 2. MedicationConfigを作成
        val config = MedicationConfig(
            medicationId = medicationId,
            cycleType = cycleType,
            cycleValue = cycleValue,
            medicationStartDate = startDate,
            medicationEndDate = endDate,
            validFrom = today,
            validTo = null
        )
        medicationConfigDao.insert(config)

        // 3. MedicationTimeを作成
        val medicationTimes = times.map { time ->
            MedicationTime(
                medicationId = medicationId,
                time = time,
                validFrom = today,
                validTo = null
            )
        }
        medicationTimeDao.insertAll(medicationTimes)

        return medicationId
    }

    suspend fun updateMedicationWithTimes(
        medicationId: Long,
        name: String,
        cycleType: CycleType,
        cycleValue: String?,
        startDate: Long,
        endDate: Long?,
        times: List<String>
    ) {
        val today = DateUtils.normalizeToStartOfDay(System.currentTimeMillis())

        // 1. Medicationの名前を更新
        val medication = medicationDao.getMedicationById(medicationId) ?: return
        medicationDao.update(medication.copy(name = name, updatedAt = System.currentTimeMillis()))

        // 2. MedicationConfigの変更をチェック
        val currentConfig = medicationConfigDao.getCurrentConfigForMedication(medicationId)
        if (currentConfig != null) {
            val configChanged = currentConfig.cycleType != cycleType ||
                    currentConfig.cycleValue != cycleValue ||
                    currentConfig.medicationStartDate != startDate ||
                    currentConfig.medicationEndDate != endDate

            if (configChanged) {
                // 既存のConfigを無効化
                medicationConfigDao.update(
                    currentConfig.copy(
                        validTo = today,
                        updatedAt = System.currentTimeMillis()
                    )
                )

                // 新しいConfigを作成
                val newConfig = MedicationConfig(
                    medicationId = medicationId,
                    cycleType = cycleType,
                    cycleValue = cycleValue,
                    medicationStartDate = startDate,
                    medicationEndDate = endDate,
                    validFrom = today,
                    validTo = null
                )
                medicationConfigDao.insert(newConfig)
            }
        } else {
            // 存在しない場合は新規作成
            val newConfig = MedicationConfig(
                medicationId = medicationId,
                cycleType = cycleType,
                cycleValue = cycleValue,
                medicationStartDate = startDate,
                medicationEndDate = endDate,
                validFrom = today,
                validTo = null
            )
            medicationConfigDao.insert(newConfig)
        }

        // 3. MedicationTimeの変更をチェック
        val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medicationId)
        val currentTimeStrings = currentTimes.map { it.time }.toSet()
        val newTimeStrings = times.toSet()

        // 削除する時刻（validToを設定）
        val timesToEnd = currentTimes.filter { it.time !in newTimeStrings }
        timesToEnd.forEach {
            medicationTimeDao.update(
                it.copy(
                    validTo = today,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }

        // 追加する時刻
        val timesToAdd = newTimeStrings.filter { it !in currentTimeStrings }
        val newMedicationTimes = timesToAdd.map { time ->
            MedicationTime(
                medicationId = medicationId,
                time = time,
                validFrom = today,
                validTo = null
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
        val medicationConfigs = medicationConfigDao.getAllConfigsFlow()
        val intakes = medicationIntakeDao.getIntakesByDate(dateString)

        return combine(medications, medicationTimes, medicationConfigs, intakes) { medList, allTimes, allConfigs, intakeList ->
            val dailyItems = mutableListOf<DailyMedicationItem>()
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.scheduledTime}" }

            // dateStringをタイムスタンプに変換
            val targetDate = parseDateString(dateString)

            // 薬IDごとに時刻とConfigをグループ化
            val timesByMedicationId = allTimes.groupBy { it.medicationId }
            val configsByMedicationId = allConfigs.groupBy { it.medicationId }

            for (medication in medList) {
                // MedicationWithTimesオブジェクトを構築
                val medicationWithTimes = MedicationWithTimes(
                    medication = medication,
                    times = timesByMedicationId[medication.id] ?: emptyList(),
                    configs = configsByMedicationId[medication.id] ?: emptyList()
                )

                // 対象日に有効なConfigを取得
                val validConfig = medicationWithTimes.getConfigForDate(targetDate)

                if (validConfig != null && shouldTakeMedication(validConfig, targetDate)) {
                    // 対象日に有効な時刻を取得
                    val validTimes = medicationWithTimes.getTimesForDate(targetDate)

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
     * 指定された日付にその薬を飲むべきかどうかを判定する（Configベース）
     */
    private fun shouldTakeMedication(config: MedicationConfig, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // 日付のみで比較するため、時刻を00:00:00にリセット
        val normalizedTargetDate = DateUtils.normalizeToStartOfDay(targetDate)
        val normalizedStartDate = DateUtils.normalizeToStartOfDay(config.medicationStartDate)
        val normalizedEndDate = config.medicationEndDate?.let { DateUtils.normalizeToStartOfDay(it) }

        // 期間チェック
        if (normalizedTargetDate < normalizedStartDate) return false
        if (normalizedEndDate != null && normalizedTargetDate > normalizedEndDate) return false

        return when (config.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // 曜日チェック (0=日曜, 1=月曜, ..., 6=土曜)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = config.cycleValue?.split(",")?.map { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // N日ごとチェック
                val intervalDays = config.cycleValue?.toIntOrNull() ?: return false
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
     * 古い服用記録を削除（オプション: クリーンアップ用）
     */
    suspend fun cleanupOldIntakes(daysToKeep: Int = 30) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysToKeep)
        val cutoffDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(calendar.time)
        medicationIntakeDao.deleteOldIntakes(cutoffDate)
    }
}
