package net.shugo.medicineshield.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import net.shugo.medicineshield.data.dao.DailyNoteDao
import net.shugo.medicineshield.data.dao.MedicationConfigDao
import net.shugo.medicineshield.data.dao.MedicationDao
import net.shugo.medicineshield.data.dao.MedicationIntakeDao
import net.shugo.medicineshield.data.dao.MedicationTimeDao
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.data.model.MedicationIntake
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.model.MedicationTime
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MedicationRepository(
    private val medicationDao: MedicationDao,
    private val medicationTimeDao: MedicationTimeDao,
    private val medicationIntakeDao: MedicationIntakeDao,
    private val medicationConfigDao: MedicationConfigDao,
    private val dailyNoteDao: DailyNoteDao
) {
    /**
     * Retrieve all Medications and their relationships (only currently valid ones)
     *
     * Note: Due to Room's @Relation constraints, we cannot completely filter at the SQL level.
     * Instead, we retrieve all data from the DAO and filter in memory using the Flow map operator.
     * Since deleted records (validTo != null) are usually few, the performance impact is limited.
     */
    fun getAllMedicationsWithTimes(): Flow<List<MedicationWithTimes>> {
        return medicationDao.getAllMedicationsWithTimes().map { list ->
            list.map { mwt ->
                MedicationWithTimes(
                    medication = mwt.medication,
                    times = mwt.getCurrentTimes(),
                    configs = mwt.configs.filter { it.validTo == DateUtils.MAX_DATE }
                )
            }
        }
    }

    /**
     * Retrieve the Medication and its relationships with a specified ID (only currently valid ones)
     *
     * Note: Due to Room's @Relation constraints, we cannot completely filter at the SQL level.
     * Instead, we retrieve all data from the DAO and filter in memory.
     */
    suspend fun getMedicationWithTimesById(medicationId: Long): MedicationWithTimes? {
        val mwt = medicationDao.getMedicationWithTimesById(medicationId) ?: return null
        return MedicationWithTimes(
            medication = mwt.medication,
            times = mwt.getCurrentTimes(),
            configs = mwt.configs.filter { it.validTo == DateUtils.MAX_DATE }
        )
    }

    suspend fun insertMedicationWithTimes(
        name: String,
        cycleType: CycleType,
        cycleValue: String?,
        startDate: Long,
        endDate: Long?,
        timesWithDose: List<Pair<String, Double>>,  // (time, dose) pair
        isAsNeeded: Boolean = false,  // PRN (as-needed) medication flag
        defaultDose: Double = 1.0,  // Default dose (saved in MedicationConfig, also used as default value for MedicationTime)
        doseUnit: String? = null  // Dosage unit
    ): Long {
        // 1. Medicationを作成
        val medication = Medication(name = name)
        val medicationId = medicationDao.insert(medication)

        // 2. Long型の日付をString型（yyyy-MM-dd）に変換
        val startDateString = formatDateString(startDate)
        val endDateString = endDate?.let { formatDateString(it) } ?: DateUtils.MAX_DATE

        // 3. Create MedicationConfig
        // For initial registration, validFrom = MIN_DATE (valid for all past dates)
        val config = MedicationConfig(
            medicationId = medicationId,
            cycleType = cycleType,
            cycleValue = cycleValue,
            medicationStartDate = startDateString,
            medicationEndDate = endDateString,
            isAsNeeded = isAsNeeded,
            dose = defaultDose,
            doseUnit = doseUnit,
            validFrom = DateUtils.MIN_DATE,
            validTo = DateUtils.MAX_DATE
        )
        medicationConfigDao.insert(config)

        // 4. Create MedicationTime (assign sequenceNumber starting from 1)
        // For initial registration, validFrom = MIN_DATE (valid for all past dates)
        // For PRN medication, time can be empty
        if (timesWithDose.isNotEmpty()) {
            val medicationTimes = timesWithDose.mapIndexed { index, (time, dose) ->
                MedicationTime(
                    medicationId = medicationId,
                    sequenceNumber = index + 1,
                    time = time,
                    dose = dose,
                    validFrom = DateUtils.MIN_DATE,
                    validTo = DateUtils.MAX_DATE
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
        timesWithSequenceAndDose: List<Triple<Int, String, Double>>,  // (sequenceNumber, time, dose) triple
        isAsNeeded: Boolean = false,  // PRN medication flag
        defaultDose: Double = 1.0,  // Default dose (saved in MedicationConfig)
        doseUnit: String? = null  // Dosage unit
    ) {
        val now = System.currentTimeMillis()
        val today = formatDateString(now)  // yyyy-MM-dd形式の今日の日付

        // Long型の日付をString型に変換
        val startDateString = formatDateString(startDate)
        val endDateString = endDate?.let { formatDateString(it) } ?: DateUtils.MAX_DATE

        // 1. Medicationの名前を更新
        val medication = medicationDao.getMedicationById(medicationId) ?: return
        medicationDao.update(medication.copy(name = name, updatedAt = System.currentTimeMillis()))

        // 2. MedicationConfigの変更をチェック
        val currentConfig = medicationConfigDao.getCurrentConfigForMedication(medicationId)
        if (currentConfig != null) {
            val configChanged = currentConfig.cycleType != cycleType ||
                    currentConfig.cycleValue != cycleValue ||
                    currentConfig.medicationStartDate != startDateString ||
                    currentConfig.medicationEndDate != endDateString ||
                    currentConfig.isAsNeeded != isAsNeeded ||
                    currentConfig.dose != defaultDose ||
                    currentConfig.doseUnit != doseUnit

            // If isAsNeeded is changed, delete intake history from today onwards
            if (currentConfig.isAsNeeded != isAsNeeded) {
                medicationIntakeDao.deleteIntakesFromDate(medicationId, today)

                // If isAsNeeded is changed to true (scheduled→PRN), disable currently valid MedicationTimes
                if (isAsNeeded) {
                    val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medicationId)
                    currentTimes.forEach { time ->
                        medicationTimeDao.update(
                            time.copy(
                                validTo = today,
                                updatedAt = now
                            )
                        )
                    }
                }
            }

            if (configChanged) {
                if (currentConfig.validFrom == today) {
                    // If validFrom is today, delete the old Config as it's not used
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
                    medicationStartDate = startDateString,
                    medicationEndDate = endDateString,
                    isAsNeeded = isAsNeeded,
                    dose = defaultDose,
                    doseUnit = doseUnit,
                    validFrom = today,
                    validTo = DateUtils.MAX_DATE
                )
                medicationConfigDao.insert(newConfig)
            }
        } else {
            // If not found, create new (as first Config)
            // By setting validFrom = MIN_DATE, it's valid for all past dates
            val newConfig = MedicationConfig(
                medicationId = medicationId,
                cycleType = cycleType,
                cycleValue = cycleValue,
                medicationStartDate = startDateString,
                medicationEndDate = endDateString,
                isAsNeeded = isAsNeeded,
                dose = defaultDose,
                doseUnit = doseUnit,
                validFrom = DateUtils.MIN_DATE,
                validTo = DateUtils.MAX_DATE
            )
            medicationConfigDao.insert(newConfig)
        }

        // For PRN medication, MedicationTime management is not needed, so exit here
        if (isAsNeeded) {
            return
        }

        // 3. MedicationTimeの変更をチェック
        val currentTimes = medicationTimeDao.getCurrentTimesForMedication(medicationId)
        val currentMap = currentTimes.associateBy { it.sequenceNumber }
        val newMap = timesWithSequenceAndDose.associate { (seq, time, dose) -> seq to Pair(time, dose) }

        // Times to delete (sequenceNumber not in new list)
        val timesToEnd = currentTimes.filter { it.sequenceNumber !in newMap }
        timesToEnd.forEach { oldTime ->
            // 今日のMedicationIntakeが存在するかチェック
            val todayIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
                medicationId, today, oldTime.sequenceNumber
            )

            // If Intake exists, next day; if not, today
            val validToDate = if (todayIntake != null) {
                // Calculate next day's date
                val todayTimestamp = parseDateString(today)
                if (todayTimestamp == null) {
                    today
                } else {
                    val tomorrowTimestamp = todayTimestamp + (24 * 60 * 60 * 1000)
                    formatDateString(tomorrowTimestamp)
                }
            } else {
                today
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
                        validTo = DateUtils.MAX_DATE
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
                        validTo = DateUtils.MAX_DATE
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
     * Retrieve the list of medications for the specified date
     */
    fun getMedications(dateString: String): Flow<List<DailyMedicationItem>> {
        val medications = medicationDao.getAllMedications()
        val medicationTimes = medicationTimeDao.getAllTimesFlowOnDate(dateString)
        val medicationConfigs = medicationConfigDao.getAllConfigsFlowOnDate(dateString)
        val intakes = medicationIntakeDao.getIntakesByDate(dateString)

        return combine(medications, medicationTimes, medicationConfigs, intakes) { medList, allTimes, allConfigs, intakeList ->
            val dailyItems = mutableListOf<DailyMedicationItem>()
            val intakeMap = intakeList.associateBy { "${it.medicationId}_${it.sequenceNumber}" }

            // Convert dateString to timestamp
            val targetDate = parseDateString(dateString) ?: return@combine emptyList<DailyMedicationItem>()

            // Group times and Configs by medication ID
            val timesByMedicationId = allTimes.groupBy { it.medicationId }
            val configsByMedicationId = allConfigs.groupBy { it.medicationId }

            for (medication in medList) {
                // MedicationWithTimesオブジェクトを構築
                val medicationWithTimes = MedicationWithTimes(
                    medication = medication,
                    times = timesByMedicationId[medication.id]?.sortedBy { it.time } ?: emptyList(),
                    configs = configsByMedicationId[medication.id] ?: emptyList()
                )

                // Retrieve valid Config for target date
                val validConfig = medicationWithTimes.config

                if (validConfig != null && shouldTakeMedication(validConfig, targetDate)) {
                    if (validConfig.isAsNeeded) {
                        // PRN medication processing
                        // Retrieve all taken records for that day
                        val takenIntakes = intakeList.filter {
                            it.medicationId == medication.id && it.takenAt != null
                        }.sortedBy { it.sequenceNumber }

                        // Display taken records
                        for (intake in takenIntakes) {
                            // Determine intake status
                            val status = when {
                                intake.isCanceled -> MedicationIntakeStatus.CANCELED
                                intake.takenAt != null -> MedicationIntakeStatus.TAKEN
                                else -> MedicationIntakeStatus.UNCHECKED
                            }

                            dailyItems.add(
                                DailyMedicationItem(
                                    medicationId = medication.id,
                                    medicationName = medication.name,
                                    sequenceNumber = intake.sequenceNumber,
                                    scheduledTime = "",  // PRN has no time
                                    dose = validConfig.dose,  // MedicationConfig.doseを使用
                                    doseUnit = validConfig.doseUnit,
                                    status = status,
                                    takenAt = intake.takenAt,
                                    isAsNeeded = true
                                )
                            )
                        }

                        // Add one unchecked slot (for next intake)
                        val nextSequenceNumber = (takenIntakes.maxOfOrNull { it.sequenceNumber } ?: 0) + 1
                        dailyItems.add(
                            DailyMedicationItem(
                                medicationId = medication.id,
                                medicationName = medication.name,
                                sequenceNumber = nextSequenceNumber,
                                scheduledTime = "",  // PRN has no time
                                dose = validConfig.dose,  // MedicationConfig.doseを使用
                                doseUnit = validConfig.doseUnit,
                                status = MedicationIntakeStatus.UNCHECKED,
                                takenAt = null,
                                isAsNeeded = true
                            )
                        )
                    } else {
                        // Scheduled medication processing (existing logic)
                        // Retrieve valid times for target date
                        val validTimes = medicationWithTimes.times

                        for (medTime in validTimes) {
                            val key = "${medication.id}_${medTime.sequenceNumber}"
                            val intake = intakeMap[key]

                            // 服用状態を判定
                            val status = when {
                                intake?.isCanceled == true -> MedicationIntakeStatus.CANCELED
                                intake?.takenAt != null -> MedicationIntakeStatus.TAKEN
                                else -> MedicationIntakeStatus.UNCHECKED
                            }

                            dailyItems.add(
                                DailyMedicationItem(
                                    medicationId = medication.id,
                                    medicationName = medication.name,
                                    sequenceNumber = medTime.sequenceNumber,
                                    scheduledTime = medTime.time,
                                    dose = medTime.dose,
                                    doseUnit = validConfig.doseUnit,
                                    status = status,
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
                        takenAt = getCurrentTimeString()
                    )
                )
            } else {
                // 更新
                medicationIntakeDao.update(
                    existingIntake.copy(
                        takenAt = getCurrentTimeString(),
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
     * Determine whether the medication should be taken on the specified date (Config-based)
     */
    private fun shouldTakeMedication(config: MedicationConfig, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // targetDateをyyyy-MM-dd形式の文字列に変換
        val targetDateString = formatDateString(targetDate)

        // 期間チェック（String型での比較、yyyy-MM-ddは辞書順で比較可能）
        if (targetDateString < config.medicationStartDate) return false
        if (targetDateString > config.medicationEndDate) return false

        // 頓服薬チェック
        if (config.isAsNeeded) return true

        return when (config.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // Day of week check (0=Sunday, 1=Monday, ..., 6=Saturday)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = config.cycleValue?.split(",")?.map { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // Every N days check
                val intervalDays = config.cycleValue?.toIntOrNull() ?: return false
                val startDateTimestamp = parseDateString(config.medicationStartDate)
                val targetTimestamp = parseDateString(targetDateString)
                if (startDateTimestamp == null || targetTimestamp == null) {
                    false
                } else {
                    val daysSinceStart =
                        ((targetTimestamp - startDateTimestamp) / (1000 * 60 * 60 * 24)).toInt()
                    daysSinceStart % intervalDays == 0
                }
            }
        }
    }

    /**
     * 現在の日付を YYYY-MM-DD 形式で取得
     */
    private fun getCurrentDateString(): String {
        return formatDateString(System.currentTimeMillis())
    }

    /**
     * Long型のタイムスタンプをyyyy-MM-dd形式の文字列に変換
     */
    private fun formatDateString(timestamp: Long): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(Date(timestamp))
    }

    /**
     * YYYY-MM-DD 形式の文字列をタイムスタンプに変換
     */
    private fun parseDateString(dateString: String): Long? {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return try {
            dateFormatter.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }

    /**
     * 現在の時刻を HH:mm 形式で取得
     */
    private fun getCurrentTimeString(): String {
        val timeFormatter = SimpleDateFormat("HH:mm", Locale.ROOT)
        return timeFormatter.format(Date(System.currentTimeMillis()))
    }

    /**
     * Add intake record for PRN medication
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
                takenAt = getCurrentTimeString()
            )
        )
    }

    /**
     * Delete specific intake record for PRN medication (uncheck)
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
     * Update intake time
     */
    suspend fun updateIntakeTakenAt(
        medicationId: Long,
        sequenceNumber: Int,
        newTakenAt: String,  // HH:mm format
        scheduledDate: String = getCurrentDateString()
    ) {
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, sequenceNumber
        )
        if (existingIntake != null) {
            medicationIntakeDao.update(
                existingIntake.copy(
                    takenAt = newTakenAt,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Delete old intake records (optional: for cleanup)
     */
    suspend fun cleanupOldIntakes(daysToKeep: Int = 30) {
        val calendar = Calendar.getInstance()
        calendar.add(Calendar.DAY_OF_YEAR, -daysToKeep)
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val cutoffDate = dateFormatter.format(calendar.time)
        medicationIntakeDao.deleteOldIntakes(cutoffDate)
    }

    // ========== Daily Note Functions ==========

    /**
     * Save or update note for specified date
     */
    suspend fun saveOrUpdateDailyNote(date: String, content: String) {
        val existingNote = dailyNoteDao.getNoteByDateSync(date)

        if (existingNote != null) {
            // 既存のメモを更新（createdAtは保持）
            val updatedNote = existingNote.copy(
                content = content,
                updatedAt = System.currentTimeMillis()
            )
            dailyNoteDao.update(updatedNote)
        } else {
            // 新規作成
            val newNote = DailyNote(
                noteDate = date,
                content = content,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
            dailyNoteDao.insert(newNote)
        }
    }

    /**
     * Delete note for specified date
     */
    suspend fun deleteDailyNote(date: String) {
        dailyNoteDao.delete(date)
    }

    /**
     * Retrieve note for specified date (Flow)
     */
    fun getDailyNote(date: String): Flow<DailyNote?> {
        return dailyNoteDao.getNoteByDate(date)
    }

    /**
     * Retrieve note before specified date
     */
    suspend fun getPreviousNote(currentDate: String): DailyNote? {
        return dailyNoteDao.getPreviousNote(currentDate)
    }

    /**
     * Retrieve note after specified date
     */
    suspend fun getNextNote(currentDate: String): DailyNote? {
        return dailyNoteDao.getNextNote(currentDate)
    }

    // ========== Cancel/Uncancel Medication Functions ==========

    /**
     * Cancel intake
     */
    suspend fun cancelIntake(
        medicationId: Long,
        sequenceNumber: Int,
        scheduledDate: String = getCurrentDateString()
    ) {
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, sequenceNumber
        )

        if (existingIntake == null) {
            // 新規作成（キャンセル状態）
            medicationIntakeDao.insert(
                MedicationIntake(
                    medicationId = medicationId,
                    sequenceNumber = sequenceNumber,
                    scheduledDate = scheduledDate,
                    takenAt = null,
                    isCanceled = true
                )
            )
        } else {
            // 更新（キャンセル状態、服用記録はクリア）
            medicationIntakeDao.update(
                existingIntake.copy(
                    takenAt = null,
                    isCanceled = true,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Cancel the cancellation of intake (delete MedicationIntake record to return to unchecked state)
     */
    suspend fun uncancelIntake(
        medicationId: Long,
        sequenceNumber: Int,
        scheduledDate: String = getCurrentDateString()
    ) {
        val existingIntake = medicationIntakeDao.getIntakeByMedicationAndDateTime(
            medicationId, scheduledDate, sequenceNumber
        )

        if (existingIntake != null) {
            // キャンセルを取り消す際は、レコード自体を削除して未服用状態に戻す
            medicationIntakeDao.delete(existingIntake)
        }
    }
}
