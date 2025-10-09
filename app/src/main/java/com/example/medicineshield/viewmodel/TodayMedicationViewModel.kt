package com.example.medicineshield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.medicineshield.data.model.TodayMedicationItem
import com.example.medicineshield.data.repository.MedicationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class TodayMedicationViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _todayMedications = MutableStateFlow<List<TodayMedicationItem>>(emptyList())
    val todayMedications: StateFlow<List<TodayMedicationItem>> = _todayMedications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _todayDate = MutableStateFlow("")
    val todayDate: StateFlow<String> = _todayDate.asStateFlow()

    init {
        loadTodayMedications()
        updateTodayDate()
    }

    private fun loadTodayMedications() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getTodayMedications().collect { medications ->
                _todayMedications.value = medications
                _isLoading.value = false
            }
        }
    }

    private fun updateTodayDate() {
        val dateFormat = SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.JAPANESE)
        _todayDate.value = dateFormat.format(Date())
    }

    fun toggleMedicationTaken(medicationId: Long, scheduledTime: String, currentStatus: Boolean) {
        viewModelScope.launch {
            repository.updateIntakeStatus(medicationId, scheduledTime, !currentStatus)
        }
    }

    fun refreshData() {
        updateTodayDate()
        loadTodayMedications()
    }

    /**
     * 時刻でグループ化されたデータを取得
     */
    fun getMedicationsGroupedByTime(): Map<String, List<TodayMedicationItem>> {
        return _todayMedications.value.groupBy { it.scheduledTime }
    }
}
