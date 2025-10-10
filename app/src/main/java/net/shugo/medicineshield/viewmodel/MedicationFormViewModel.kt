package net.shugo.medicineshield.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.Medication
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MedicationFormState(
    val medicationId: Long? = null,
    val name: String = "",
    val times: List<String> = emptyList(),
    val cycleType: CycleType = CycleType.DAILY,
    val cycleValue: String? = null,  // 曜日リスト or 日数
    val startDate: Long = System.currentTimeMillis(),
    val endDate: Long? = null,
    val nameError: String? = null,
    val timesError: String? = null,
    val cycleError: String? = null,
    val dateError: String? = null,
    val isSaving: Boolean = false
)

class MedicationFormViewModel(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModel() {

    private val _formState = MutableStateFlow(MedicationFormState())
    val formState: StateFlow<MedicationFormState> = _formState.asStateFlow()

    private val notificationScheduler by lazy {
        NotificationScheduler(context, repository)
    }

    fun loadMedication(medicationId: Long) {
        viewModelScope.launch {
            val medicationWithTimes = repository.getMedicationWithCurrentTimesById(medicationId)
            medicationWithTimes?.let { mwt ->
                _formState.value = _formState.value.copy(
                    medicationId = mwt.medication.id,
                    name = mwt.medication.name,
                    times = mwt.times.map { it.time }.sorted(),
                    cycleType = mwt.medication.cycleType,
                    cycleValue = mwt.medication.cycleValue,
                    startDate = mwt.medication.startDate,
                    endDate = mwt.medication.endDate
                )
            }
        }
    }

    fun updateName(name: String) {
        _formState.value = _formState.value.copy(name = name, nameError = null)
    }

    fun addTime(time: String) {
        val currentTimes = _formState.value.times.toMutableList()
        currentTimes.add(time)
        currentTimes.sort()
        _formState.value = _formState.value.copy(times = currentTimes, timesError = null)
    }

    fun removeTime(index: Int) {
        val currentTimes = _formState.value.times.toMutableList()
        if (index in currentTimes.indices) {
            currentTimes.removeAt(index)
            _formState.value = _formState.value.copy(times = currentTimes)
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

    fun saveMedication(onSuccess: () -> Unit) {
        if (!validateForm()) {
            return
        }

        _formState.value = _formState.value.copy(isSaving = true)

        viewModelScope.launch {
            try {
                val state = _formState.value
                val medication = Medication(
                    id = state.medicationId ?: 0,
                    name = state.name,
                    startDate = state.startDate,
                    endDate = state.endDate,
                    cycleType = state.cycleType,
                    cycleValue = state.cycleValue
                )

                if (state.medicationId == null) {
                    repository.insertMedicationWithTimes(medication, state.times)
                } else {
                    repository.updateMedicationWithTimes(medication, state.times)
                }

                // 通知をスケジュール
                state.times.forEach { time ->
                    notificationScheduler.scheduleNextNotificationForTime(time)
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

        if (state.times.isEmpty()) {
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

        if (state.endDate != null && state.endDate <= state.startDate) {
            _formState.value = _formState.value.copy(dateError = "終了日は開始日より後に設定してください")
            isValid = false
        }

        return isValid
    }

    fun resetForm() {
        _formState.value = MedicationFormState()
    }
}
