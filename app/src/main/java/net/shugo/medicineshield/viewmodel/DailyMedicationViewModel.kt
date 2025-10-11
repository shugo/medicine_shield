package net.shugo.medicineshield.viewmodel

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.repository.MedicationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DailyMedicationViewModel(
    application: Application,
    private val repository: MedicationRepository
) : AndroidViewModel(application) {

    private val _dailyMedications = MutableStateFlow<List<DailyMedicationItem>>(emptyList())
    val dailyMedications: StateFlow<List<DailyMedicationItem>> = _dailyMedications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedDate = MutableStateFlow(Calendar.getInstance())
    val selectedDate: StateFlow<Calendar> = _selectedDate.asStateFlow()

    private val _displayDateText = MutableStateFlow("")
    val displayDateText: StateFlow<String> = _displayDateText.asStateFlow()

    private var loadJob: Job? = null

    init {
        loadMedicationsForSelectedDate()
        updateDisplayDate()
    }

    private fun loadMedicationsForSelectedDate() {
        // 前回のloadJobをキャンセル
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _isLoading.value = true
            val dateString = formatDateToString(_selectedDate.value)
            repository.getMedications(dateString).collect { medications ->
                _dailyMedications.value = medications
                _isLoading.value = false
            }
        }
    }

    private fun updateDisplayDate() {
        val calendar = _selectedDate.value

        // ロケールに応じた最適な日付パターンを取得
        val locale = Locale.getDefault()
        val pattern = DateFormat.getBestDateTimePattern(locale, "yMdE")
        val dateFormat = SimpleDateFormat(pattern, locale)

        // 今日かどうかチェック
        val today = Calendar.getInstance()
        val isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                      calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        val dateText = dateFormat.format(calendar.time)
        _displayDateText.value = if (isToday) {
            getApplication<Application>().getString(R.string.today_with_date, dateText)
        } else {
            dateText
        }
    }

    fun onPreviousDay() {
        val newDate = _selectedDate.value.clone() as Calendar
        newDate.add(Calendar.DAY_OF_YEAR, -1)
        _selectedDate.value = newDate
        updateDisplayDate()
        loadMedicationsForSelectedDate()
    }

    fun onNextDay() {
        val newDate = _selectedDate.value.clone() as Calendar
        newDate.add(Calendar.DAY_OF_YEAR, 1)
        _selectedDate.value = newDate
        updateDisplayDate()
        loadMedicationsForSelectedDate()
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val newDate = Calendar.getInstance()
        newDate.set(year, month, dayOfMonth)
        _selectedDate.value = newDate
        updateDisplayDate()
        loadMedicationsForSelectedDate()
    }

    fun toggleMedicationTaken(medicationId: Long, sequenceNumber: Int, currentStatus: Boolean) {
        viewModelScope.launch {
            val dateString = formatDateToString(_selectedDate.value)
            repository.updateIntakeStatus(medicationId, sequenceNumber, !currentStatus, dateString)
        }
    }

    fun refreshData() {
        updateDisplayDate()
        loadMedicationsForSelectedDate()
    }

    /**
     * 時刻でグループ化されたデータを取得
     */
    fun getMedicationsGroupedByTime(): Map<String, List<DailyMedicationItem>> {
        return _dailyMedications.value.groupBy { it.scheduledTime }
    }

    /**
     * CalendarをYYYY-MM-DD形式の文字列に変換
     */
    private fun formatDateToString(calendar: Calendar): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}
