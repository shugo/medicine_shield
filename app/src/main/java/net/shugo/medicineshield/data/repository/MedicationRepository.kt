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
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.*

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationTimeDao: MedicationTimeDao,
    private val medicationIntakeDao: MedicationIntakeDao,
    private val medicationConfigDao: MedicationConfigDao
) {
    /**
     * すべてのMedicationとそのリレーションを取得（現在有効なもののみ）
     *
     * 注意: Roomの@Relationの制約により、完全にSQLレベルでフィルタリングすることができないため、
     * DAOから全データを取得後、Flowのmap演算子を使ってメモリ上でフィルタリングしています。
     * 削除されたレコード（validTo != null）は通常少数なので、パフォーマンスへの影響は限定的です。
     */
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>> {
        return medicationDao.getAllMedicationsWithTimes().map { list ->
            list.map { mwt ->
                MedicationWithTimes(
                    medication = mwt.medication,
                    times = mwt.getCurrentTimes(),
                    configs = mwt.configs.filter { it.validTo == null }
                )
            }
        }
    }

    /**
     * 指定IDのMedicationとそのリレーションを取得（現在有効なもののみ）
     *
     * 注意: Roomの@Relationの制約により、完全にSQLレベルでフィルタリングすることができないため、
     * DAOから全データを取得後、メモリ上でフィルタリングしています。
     */
    suspend fun getMedicationWithTimesById(medicationId: Long): MedicationWithTimes? {
        val mwt = medicationDao.getMedicationWithTimesById(medicationId) ?: return null
        return MedicationWithTimes(
            medication = mwt.medication,
            times = mwt.getCurrentTimes(),
            configs = mwt.configs.filter { it.validTo == null }
        )
    }

    suspend fun insertMedicationWithTimes(
        name: String,
        cycleType: CycleType,
        cycleValue: String?,
        startDate: Long,
        endDate: Long?,
        timesWithDose: List<Pair<String, Double>>,  // (time, dose)のペア
        isAsNeeded: Boolean = false  // 頓服薬フラグ
    ): Long {
        // 1. Medicationを作成
        val medication = Medication(name = name)
        val medicationId = medicationDao.insert(medication)

        // 2. MedicationConfigを作成
        // 初期登録時はvalidFrom = 0（過去すべての日付で有効）
        val config = MedicationConfig(
            medicationId = medicationId,
            cycleType = cycleType,
            cycleValue = cycleValue,
            medicationStartDate = startDate,
            medicationEndDate = endDate,
            isAsNeeded = isAsNeeded,
            validFrom = 0,
            validTo = null
        )
        medicationConfigDao.insert(config)

        // 3. MedicationTimeを作成（sequenceNumberを1から割り当て）
        // 初期登録時はvalidFrom = 0（過去すべての日付で有効）
        // 頓服の場合は時刻が空でもOK
        if (timesWithDose.isNotEmpty()) {
            val medicationTimes = timesWithDose.mapIndexed { index, (time, dose) ->
                MedicationTime(
                    medicationId = medicationId,
                    sequenceNumber = index + 1,
                    time = time,
                    dose = dose,
                    validFrom = 0,
                    validTo = null
                )
            }
            medicationTimeDao.insertAll(medicationTimes)
        }

        return medicationId
    }

    suspend fun updateMedicationWithTimes(
        medicationId: Long,
        name: String,
        cycleType: CycleType,
        cycleValue: String?,
        startDate: Long,
        endDate: Long?,
        timesWithSequenceAndDose: List<Triple<Int, String, Double>>,  // (sequenceNumber, time, dose)のトリプル
        isAsNeeded: Boolean = false  // 頓服薬フラグ
    ) {
        val now = System.currentTimeMillis()
        val today = DateUtils.normalizeToStartOfDay(now)

        // 1. Medicationの名前を更新
        val medication = medicationDao.getMedicationById(medicationId) ?: return
        medicationDao.update(medication.copy(name = name, updatedAt = System.currentTimeMillis()))

        // 2. MedicationConfigの変更をチェック
        val currentConfig = medicationConfigDao.getCurrentConfigForMedication(medicationId)
        if (currentConfig != null) {
            val configChanged = currentConfig.cycleType != cycleType ||
                    currentConfig.cycleValue != cycleValue ||
                    currentConfig.medicationStartDate != startDate ||
                    currentConfig.medicationEndDate != endDate ||
                    currentConfig.isAsNeeded != isAsNeeded

            // isAsNeededが変更された場合、今日以降の服用履歴を削除
            if (currentConfig.isAsNeeded != isAsNeeded) {
                val todayString = getDateString(today)
                medicationIntakeDao.deleteIntakesFromDate(medicationId, todayString)
            }

            if (configChanged) {
                if (currentConfig.validFrom == today) {
                    // validFromが今日の場合、古いConfigは使用されないので削除
                    medicationConfigDao.delete(currentConfig)
                } else {
                    // validFromが今日より前の場合、既存のConfigを無効化
                    medicationConfigDao.update(
                        currentConfig.copy(
                            validTo = today,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }

                // 新しいConfigを作成
                val newConfig = MedicationConfig(
                    medicationId = medicationId,
                    cycleType = cycleType,
                    cycleValue = cycleValue,
                    medicationStartDate = startDate,
                    medicationEndDate = endDate,
                    isAsNeeded = isAsNeeded,
                    validFrom = today,
                    validTo = null
                )
                medicationConfigDao.insert(newConfig)
            }
        } else {
            // 存在しない場合は新規作成（最初のConfigとして）
            // validFrom = 0にすることで過去すべての日付で有効
            val newConfig = MedicationConfig(
                medicationId = medicationId,
                cycleType = cycleType,
                cycleValue = cycleValue,
                medicationStartDate = startDate,
                medicationEndDate = endDate,
                isAsNeeded = isAsNeeded,
                validFrom = 0,
                validTo = null
            )
            medicationConfigDao.insert(newConfig)
        }

        // 3. MedicationTimeの変更をチェック
        val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medicationId)
        val currentMap = currentTimes.associateBy { it.sequenceNumber }
        val newMap = timesWithSequenceAndDose.associate { (seq, time, dose) -> seq to Pair(time, dose) }

        // 削除する時刻（sequenceNumberが新しいリストにない）
        val timesToEnd = currentTimes.filter { it.sequenceNumber !in newMap }
        val todayString = getDateString(today)
        timesToEnd.forEach { oldTime ->
            // 今日のMedicationIntakeが存在するかチェック
            val todayIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
                medicationId, todayString, oldTime.sequenceNumber
            )

            // Intakeが存在する場合は翌日の00:00:00、存在しない場合は今日の00:00:00
            val validToDate = if (todayIntake != null) {
                today + (24 * 60 * 60 * 1000)  // 翌日の00:00:00
            } else {
                today  // 今日の00:00:00
            }

            medicationTimeDao.update(
                oldTime.copy(
                    validTo = validToDate,
                    updatedAt = now
                )
            )
        }

        // 追加または更新する時刻
        timesWithSequenceAndDose.forEach { (sequenceNumber, newTime, newDose) ->
            val currentTime = currentMap[sequenceNumber]

            if (currentTime == null) {
                // 新規追加
                medicationTimeDao.insert(
                    MedicationTime(
                        medicationId = medicationId,
                        sequenceNumber = sequenceNumber,
                        time = newTime,
                        dose = newDose,
                        validFrom = today,  // 今日から有効
                        validTo = null
                    )
                )
            } else if (currentTime.time != newTime || currentTime.dose != newDose) {
                // 時刻または服用量が変更された場合
                if (currentTime.validFrom == today) {
                    // validFromが今日の場合、古いレコードは使用されないので削除
                    medicationTimeDao.delete(currentTime)
                } else {
                    // validFromが今日より前の場合、古いレコードを無効化
                    medicationTimeDao.update(
                        currentTime.copy(
                            validTo = today,
                            updatedAt = now
                        )
                    )
                }

                // 新しいレコードを作成（同じsequenceNumber）
                medicationTimeDao.insert(
                    MedicationTime(
                        medicationId = medicationId,
                        sequenceNumber = sequenceNumber,
                        time = newTime,
                        dose = newDose,
                        validFrom = today,  // 今日から有効
                        validTo = null
                    )
                )
            }
            // else: 変更なし
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
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.sequenceNumber}" }

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

                if (validConfig != null) {
                    if (validConfig.isAsNeeded) {
                        // 頓服薬の処理
                        // その日に服用済みのレコードをすべて取得
                        val takenIntakes = intakeList.filter {
                            it.medicationId == medication.id && it.takenAt != null
                        }.sortedBy { it.sequenceNumber }

                        // 服用済みのレコードを表示
                        for (intake in takenIntakes) {
                            dailyItems.add(
                                DailyMedicationItem(
                                    medicationId = medication.id,
                                    medicationName = medication.name,
                                    sequenceNumber = intake.sequenceNumber,
                                    scheduledTime = "",  // 頓服は時刻なし
                                    dose = validConfig.let {
                                        // MedicationTimeがあればそのdoseを使用、なければデフォルト1.0
                                        val validTimes = medicationWithTimes.getTimesForDate(targetDate)
                                        validTimes.firstOrNull()?.dose ?: 1.0
                                    },
                                    isTaken = true,
                                    takenAt = intake.takenAt,
                                    isAsNeeded = true
                                )
                            )
                        }

                        // 未服用の枠を1つ追加（次の服用用）
                        val nextSequenceNumber = (takenIntakes.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
                        dailyItems.add(
                            DailyMedicationItem(
                                medicationId = medication.id,
                                medicationName = medication.name,
                                sequenceNumber = nextSequenceNumber,
                                scheduledTime = "",  // 頓服は時刻なし
                                dose = validConfig.let {
                                    val validTimes = medicationWithTimes.getTimesForDate(targetDate)
                                    validTimes.firstOrNull()?.dose ?: 1.0
                                },
                                isTaken = false,
                                takenAt = null,
                                isAsNeeded = true
                            )
                        )
                    } else if (shouldTakeMedication(validConfig, targetDate)) {
                        // 定時薬の処理（既存のロジック）
                        // 対象日に有効な時刻を取得
                        val validTimes = medicationWithTimes.getTimesForDate(targetDate)

                        for (medTime in validTimes) {
                            val key = "${medication.id}_${medTime.sequenceNumber}"
                            val intake = intakeMap[key]

                            dailyItems.add(
                                DailyMedicationItem(
                                    medicationId = medication.id,
                                    medicationName = medication.name,
                                    sequenceNumber = medTime.sequenceNumber,
                                    scheduledTime = medTime.time,
                                    dose = medTime.dose,
                                    isTaken = intake?.takenAt != null,
                                    takenAt = intake?.takenAt,
                                    isAsNeeded = false
                                )
                            )
                        }
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
        sequenceNumber: Int,
        isTaken: Boolean,
        scheduledDate: String = getCurrentDateString()
    ) {
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, sequenceNumber
        )

        if (isTaken) {
            if (existingIntake == null) {
                // 新規作成
                medicationIntakeDao.insert(
                    MedicationIntake(
                        medicationId = medicationId,
                        sequenceNumber = sequenceNumber,
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
     * 頓服薬の服用記録を追加
     */
    suspend fun addAsNeededIntake(
        medicationId: Long,
        scheduledDate: String = getCurrentDateString()
    ) {
        // その日の最大sequenceNumberを取得
        val existingIntakes = medicationIntakeDao.getIntakesByMedicationAndDate(medicationId, scheduledDate)
        val nextSequenceNumber = (existingIntakes.maxOfOrNull { it.sequenceNumber } ?: 0) + 1

        // 新しい服用記録を作成
        medicationIntakeDao.insert(
            MedicationIntake(
                medicationId = medicationId,
                sequenceNumber = nextSequenceNumber,
                scheduledDate = scheduledDate,
                takenAt = System.currentTimeMillis()
            )
        )
    }

    /**
     * 頓服薬の特定の服用記録を削除（チェックを外す）
     */
    suspend fun removeAsNeededIntake(
        medicationId: Long,
        sequenceNumber: Int,
        scheduledDate: String = getCurrentDateString()
    ) {
        val intake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, sequenceNumber
        )
        if (intake != null) {
            medicationIntakeDao.delete(intake)
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
