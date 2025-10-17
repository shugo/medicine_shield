package net.shugo.medicineshield.viewmodel

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.repository.MedicationRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.shugo.medicineshield.utils.DateUtils
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

    private val _dailyNote = MutableStateFlow<DailyNote?>(null)
    val dailyNote: StateFlow<DailyNote?> = _dailyNote.asStateFlow()

    private val _scrollToNote = MutableStateFlow(false)
    val scrollToNote: StateFlow<Boolean> = _scrollToNote.asStateFlow()

    private var loadJob: Job? = null
    private var noteLoadJob: Job? = null

    init {
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
        updateDisplayDate()
    }

    private fun loadMedicationsForSelectedDate() {
        // 前回のloadJobをキャンセル
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            _isLoading.value = true
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
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
        loadNoteForSelectedDate()
    }

    fun onNextDay() {
        val newDate = _selectedDate.value.clone() as Calendar
        newDate.add(Calendar.DAY_OF_YEAR, 1)
        _selectedDate.value = newDate
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val newDate = Calendar.getInstance()
        newDate.set(year, month, dayOfMonth)
        _selectedDate.value = newDate
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    fun toggleMedicationTaken(medicationId: Long, sequenceNumber: Int, currentStatus: Boolean) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.updateIntakeStatus(medicationId, sequenceNumber, !currentStatus, dateString)
        }
    }

    /**
     * 頓服薬を追加服用する
     */
    fun addAsNeededMedication(medicationId: Long) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.addAsNeededIntake(medicationId, dateString)
        }
    }

    /**
     * 頓服薬の特定の服用記録を削除
     */
    fun removeAsNeededMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.removeAsNeededIntake(medicationId, sequenceNumber, dateString)
        }
    }

    /**
     * 服用時刻を更新する
     */
    fun updateTakenAt(medicationId: Long, sequenceNumber: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            // 選択された日付の指定時刻にタイムスタンプを設定
            val calendar = _selectedDate.value.clone() as Calendar
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, minute)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            val newTakenAt = calendar.timeInMillis
            repository.updateIntakeTakenAt(medicationId, sequenceNumber, newTakenAt, dateString)
        }
    }

    fun refreshData() {
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    /**
     * 時刻でグループ化されたデータを取得
     */
    fun getMedicationsGroupedByTime(): Map<String, List<DailyMedicationItem>> {
        return _dailyMedications.value.groupBy { it.scheduledTime }
    }

    private fun loadNoteForSelectedDate() {
        // 前回のnoteLoadJobをキャンセル
        noteLoadJob?.cancel()

        noteLoadJob = viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.getDailyNote(dateString).collect { note ->
                _dailyNote.value = note
            }
        }
    }

    /**
     * メモを保存または更新
     */
    fun saveNote(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.saveOrUpdateDailyNote(dateString, content.trim())
        }
    }

    /**
     * メモを削除
     */
    fun deleteNote() {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.deleteDailyNote(dateString)
        }
    }

    /**
     * 前のメモがある日付に移動
     */
    fun navigateToPreviousNote() {
        viewModelScope.launch {
            val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
            val previousNote = repository.getPreviousNote(currentDateString)

            if (previousNote != null) {
                // 日付文字列をCalendarに変換
                val newCalendar = DateUtils.parseIsoDate(previousNote.noteDate)
                if (newCalendar != null) {
                    _selectedDate.value = newCalendar
                    updateDisplayDate()
                    loadMedicationsForSelectedDate()
                    loadNoteForSelectedDate()
                    _scrollToNote.value = true
                }
            }
        }
    }

    /**
     * 次のメモがある日付に移動
     */
    fun navigateToNextNote() {
        viewModelScope.launch {
            val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
            val nextNote = repository.getNextNote(currentDateString)

            if (nextNote != null) {
                // 日付文字列をCalendarに変換
                val newCalendar = DateUtils.parseIsoDate(nextNote.noteDate)
                if (newCalendar != null) {
                    _selectedDate.value = newCalendar
                    updateDisplayDate()
                    loadMedicationsForSelectedDate()
                    loadNoteForSelectedDate()
                    _scrollToNote.value = true
                }
            }
        }
    }

    /**
     * スクロールフラグをリセット
     */
    fun resetScrollToNote() {
        _scrollToNote.value = false
    }

    /**
     * 前のメモが存在するかチェック
     */
    suspend fun hasPreviousNote(): Boolean {
        val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
        return repository.getPreviousNote(currentDateString) != null
    }

    /**
     * 次のメモが存在するかチェック
     */
    suspend fun hasNextNote(): Boolean {
        val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
        return repository.getNextNote(currentDateString) != null
    }

}
