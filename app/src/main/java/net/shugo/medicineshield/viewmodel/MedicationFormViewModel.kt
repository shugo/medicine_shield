package net.shugo.medicineshield.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.utils.DateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TimeWithSequence(
    val sequenceNumber: Int,
    val time: String,
    val dose: Double = 1.0
)

data class MedicationFormState(
    val medicationId: Long? = null,
    val name: String = "",
    val times: List<TimeWithSequence> = emptyList(),
    val cycleType: CycleType = CycleType.DAILY,
    val cycleValue: String? = null,  // 曜日リスト or 日数
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val originalStartDate: Long? = null,  // 編集時の元の開始日（変更検出用）
    val isAsNeeded: Boolean = false,  // 頓服薬フラグ
    val defaultDoseText: String = "1.0",  // デフォルト服用量（頓服薬の場合に使用、定時薬の新規時刻追加時のデフォルト値）
    val doseUnit: String? = null,  // 服用量の単位
    val nameError: String? = null,
    val timesError: String? = null,
    val cycleError: String? = null,
    val dateError: String? = null,
    val doseError: String? = null,
    val isSaving: Boolean = false
)

class MedicationFormViewModel(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModel() {

    private val _formState = MutableStateFlow(MedicationFormState())
    val formState: StateFlow<MedicationFormState> = _formState.asStateFlow()

    private var nextSequenceNumber = 1  // 新規追加時のsequenceNumber

    private val notificationScheduler by lazy {
        NotificationScheduler(context, repository)
    }

    fun loadMedication(medicationId: Long) {
        viewModelScope.launch {
            val medicationWithTimes = repository.getMedicationWithTimesById(medicationId)
            medicationWithTimes?.let { mwt ->
                // 現在有効なConfigを取得
                val currentConfig = mwt.config
                val originalStartDate = currentConfig?.medicationStartDate ?: System.currentTimeMillis()

                val timesWithSeq = mwt.times.map {
                    TimeWithSequence(it.sequenceNumber, it.time, it.dose)
                }
                nextSequenceNumber = (mwt.times.maxOfOrNull { it.sequenceNumber } ?: 0) + 1

                _formState.value = _formState.value.copy(
                    medicationId = mwt.medication.id,
                    name = mwt.medication.name,
                    times = timesWithSeq,
                    cycleType = currentConfig?.cycleType ?: CycleType.DAILY,
                    cycleValue = currentConfig?.cycleValue,
                    startDate = originalStartDate,
                    endDate = currentConfig?.medicationEndDate,
                    originalStartDate = originalStartDate,
                    isAsNeeded = currentConfig?.isAsNeeded ?: false,
                    defaultDoseText = String.format("%.1f", currentConfig?.dose ?: 1.0),
                    doseUnit = currentConfig?.doseUnit
                )
            }
        }
    }

    fun updateName(name: String) {
        _formState.value = _formState.value.copy(name = name, nameError = null)
    }

    fun addTime(time: String, dose: Double? = null) {
        val currentTimes = _formState.value.times.toMutableList()
        // doseが指定されていない場合はdefaultDoseTextをパースして使用
        val actualDose = dose ?: _formState.value.defaultDoseText.toDoubleOrNull() ?: 1.0
        currentTimes.add(TimeWithSequence(nextSequenceNumber++, time, actualDose))
        currentTimes.sortBy { it.time }
        _formState.value = _formState.value.copy(times = currentTimes, timesError = null)
    }

    fun updateDefaultDose(doseText: String) {
        _formState.value = _formState.value.copy(defaultDoseText = doseText, doseError = null)
    }

    fun removeTime(index: Int) {
        val currentTimes = _formState.value.times.toMutableList()
        if (index in currentTimes.indices) {
            currentTimes.removeAt(index)
            _formState.value = _formState.value.copy(times = currentTimes)
        }
    }

    fun updateTime(index: Int, newTime: String, newDose: Double? = null) {
        val currentTimes = _formState.value.times.toMutableList()
        if (index in currentTimes.indices) {
            // sequenceNumberは保持したまま時刻と服用量を変更
            currentTimes[index] = currentTimes[index].copy(
                time = newTime,
                dose = newDose ?: currentTimes[index].dose
            )
            currentTimes.sortBy { it.time }
            _formState.value = _formState.value.copy(times = currentTimes, timesError = null)
        }
    }

    fun updateCycleType(cycleType: CycleType) {
        _formState.value = _formState.value.copy(
            cycleType = cycleType,
            cycleValue = null,
            cycleError = null
        )
    }

    fun updateCycleValue(cycleValue: String?) {
        _formState.value = _formState.value.copy(cycleValue = cycleValue, cycleError = null)
    }

    fun updateStartDate(startDate: Long) {
        _formState.value = _formState.value.copy(startDate = startDate, dateError = null)
    }

    fun updateEndDate(endDate: Long?) {
        _formState.value = _formState.value.copy(endDate = endDate, dateError = null)
    }

    fun updateIsAsNeeded(isAsNeeded: Boolean) {
        _formState.value = _formState.value.copy(
            isAsNeeded = isAsNeeded,
            timesError = null
        )
    }

    fun updateDoseUnit(doseUnit: String?) {
        _formState.value = _formState.value.copy(doseUnit = doseUnit)
    }

    fun saveMedication(onSuccess: () -> Unit) {
        if (!validateForm()) {
            return
        }

        _formState.value = _formState.value.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val state = _formState.value
                val defaultDose = state.defaultDoseText.toDoubleOrNull() ?: 1.0

                if (state.medicationId == null) {
                    repository.insertMedicationWithTimes(
                        name = state.name,
                        cycleType = state.cycleType,
                        cycleValue = state.cycleValue,
                        startDate = state.startDate,
                        endDate = state.endDate,
                        timesWithDose = state.times.map { it.time to it.dose },
                        isAsNeeded = state.isAsNeeded,
                        defaultDose = defaultDose,
                        doseUnit = state.doseUnit
                    )
                } else {
                    val timesWithSeqAndDose = state.times.map { Triple(it.sequenceNumber, it.time, it.dose) }
                    repository.updateMedicationWithTimes(
                        medicationId = state.medicationId,
                        name = state.name,
                        cycleType = state.cycleType,
                        cycleValue = state.cycleValue,
                        startDate = state.startDate,
                        endDate = state.endDate,
                        timesWithSequenceAndDose = timesWithSeqAndDose,
                        isAsNeeded = state.isAsNeeded,
                        defaultDose = defaultDose,
                        doseUnit = state.doseUnit
                    )
                }

                // 通知をスケジュール
                state.times.forEach { timeWithSeq ->
                    notificationScheduler.scheduleNextNotificationForTime(timeWithSeq.time)
                }

                onSuccess()
            } finally {
                _formState.value = _formState.value.copy(isSaving = false)
            }
        }
    }

    private fun validateForm(): Boolean {
        val state = _formState.value
        var isValid = true

        if (state.name.isBlank()) {
            _formState.value = _formState.value.copy(nameError = "薬の名前を入力してください")
            isValid = false
        }

        // 頓服の場合は時刻が不要
        if (!state.isAsNeeded && state.times.isEmpty()) {
            _formState.value = _formState.value.copy(timesError = "少なくとも1つの服用時間を設定してください")
            isValid = false
        }

        when (state.cycleType) {
            CycleType.WEEKLY -> {
                if (state.cycleValue.isNullOrBlank()) {
                    _formState.value = _formState.value.copy(cycleError = "少なくとも1つの曜日を選択してください")
                    isValid = false
                }
            }
            CycleType.INTERVAL -> {
                val interval = state.cycleValue?.toIntOrNull()
                if (interval == null || interval < 1) {
                    _formState.value = _formState.value.copy(cycleError = "1以上の日数を入力してください")
                    isValid = false
                }
            }
            CycleType.DAILY -> {
                // No validation needed
            }
        }

        // 服用量のバリデーション
        val dose = state.defaultDoseText.toDoubleOrNull()
        if (dose == null || dose < 0.1 || dose > 999.9) {
            _formState.value = _formState.value.copy(doseError = "服用量は0.1から999.9の範囲で入力してください")
            isValid = false
        }

        if (state.endDate != null && state.endDate < state.startDate) {
            _formState.value = _formState.value.copy(dateError = "終了日は開始日以降に設定してください")
            isValid = false
        }

        // 編集時：開始日が変更されており、かつ今日より前の日付に設定されている場合はエラー
        if (state.medicationId != null && state.originalStartDate != null) {
            val normalizedStartDate = DateUtils.normalizeToStartOfDay(state.startDate)
            val normalizedOriginalStartDate = DateUtils.normalizeToStartOfDay(state.originalStartDate)
            val normalizedToday = DateUtils.normalizeToStartOfDay(System.currentTimeMillis())

            // 開始日が変更されている場合のみチェック
            if (normalizedStartDate != normalizedOriginalStartDate && normalizedStartDate < normalizedToday) {
                _formState.value = _formState.value.copy(dateError = "編集時は開始日を過去の日付に変更できません")
                isValid = false
            }
        }

        return isValid
    }

    fun resetForm() {
        _formState.value = MedicationFormState()
    }
}
