package net.shugo.medicineshield.viewmodel

import android.app.Application
import android.text.format.DateFormat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationHelper
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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

    // 各データソースの読み込み完了フラグ
    private var medicationsLoaded = false
    private var noteLoaded = false

    // 通知管理用
    private val notificationHelper = NotificationHelper(application.applicationContext)
    private val notificationScheduler = NotificationScheduler(application.applicationContext, repository)

    init {
        _isLoading.value = true
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
        updateDisplayDate()
    }

    /**
     * Set initial date from notification
     * Only move to that date if launched from notification
     *
     * @param dateString Date string (yyyy-MM-dd format)
     */
    fun setDateFromNotification(dateString: String) {
        val calendar = DateUtils.parseIsoDate(dateString)
        if (calendar != null) {
            _selectedDate.value = calendar
            resetLoadingFlags()
            updateDisplayDate()
            loadMedicationsForSelectedDate()
            loadNoteForSelectedDate()
        }
    }

    private fun loadMedicationsForSelectedDate() {
        // 前回のloadJobをキャンセル
        loadJob?.cancel()

        loadJob = viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.getMedications(dateString).collect { medications ->
                _dailyMedications.value = medications
                medicationsLoaded = true
                updateLoadingState()
            }
        }
    }

    private fun updateDisplayDate() {
        val calendar = _selectedDate.value

        // Applicationの現在のConfigurationから最新のロケールを取得
        val appContext = getApplication<Application>()
        val currentLocale = appContext.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(currentLocale, "yMdE")
        val dateFormat = SimpleDateFormat(pattern, currentLocale)

        // 今日かどうかチェック
        val today = Calendar.getInstance()
        val isToday = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                      calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        val dateText = dateFormat.format(calendar.time)
        _displayDateText.value = if (isToday) {
            appContext.getString(R.string.today_with_date, dateText)
        } else {
            dateText
        }
    }

    fun onPreviousDay() {
        val newDate = _selectedDate.value.clone() as Calendar
        newDate.add(Calendar.DAY_OF_YEAR, -1)
        _selectedDate.value = newDate
        resetLoadingFlags()
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    fun onNextDay() {
        val newDate = _selectedDate.value.clone() as Calendar
        newDate.add(Calendar.DAY_OF_YEAR, 1)
        _selectedDate.value = newDate
        resetLoadingFlags()
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val newDate = Calendar.getInstance()
        newDate.set(year, month, dayOfMonth)
        _selectedDate.value = newDate
        resetLoadingFlags()
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    /**
     * 日付変更時にローディングフラグをリセット
     */
    private fun resetLoadingFlags() {
        _isLoading.value = true
        medicationsLoaded = false
        noteLoaded = false
        // countLoadedはリセットしない（薬の件数は日付に依存しないため）
    }

    fun toggleMedicationTaken(medicationId: Long, sequenceNumber: Int, currentStatus: Boolean) {
        viewModelScope.launch {
            // Check if notifications should be dismissed only when marking as taken
            val willBeMarkedAsTaken = !currentStatus

            // 該当する薬の情報を取得（通知チェック用）
            val medication = _dailyMedications.value.find {
                it.medicationId == medicationId && it.sequenceNumber == sequenceNumber
            }

            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.updateIntakeStatus(medicationId, sequenceNumber, !currentStatus, dateString)

            // 服用済みにマークした場合、その時刻の通知をチェック
            if (willBeMarkedAsTaken && medication != null && !medication.isAsNeeded) {
                checkAndDismissNotificationIfComplete(medication.scheduledTime, medicationId, sequenceNumber)
            }
        }
    }

    /**
     * Dismiss notification if all medications at specified time are taken
     *
     * @param time Intake time (HH:mm format)
     * @param justToggledMedId ID of medication just toggled (for optimistic update)
     * @param justToggledSeqNum Sequence number of medication just toggled (for optimistic update)
     */
    private fun checkAndDismissNotificationIfComplete(
        time: String,
        justToggledMedId: Long,
        justToggledSeqNum: Int
    ) {
        // Get all scheduled medications at that time (excluding PRN)
        val allMedsAtTime = _dailyMedications.value.filter {
            it.scheduledTime == time && !it.isAsNeeded
        }

        // Check if all medications are taken (optimistic update: assume just toggled medication is taken)
        val allTaken = allMedsAtTime.all { med ->
            if (med.medicationId == justToggledMedId && med.sequenceNumber == justToggledSeqNum) {
                true // Assume just toggled medication is taken
            } else {
                med.status == MedicationIntakeStatus.TAKEN
            }
        }

        if (allMedsAtTime.isNotEmpty() && allTaken) {
            // 通知IDを計算して通知を消す
            val notificationId = notificationScheduler.getNotificationIdForTime(time)
            notificationHelper.cancelNotification(notificationId)
        }
    }

    /**
     * Cancel medication intake
     */
    fun cancelMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.cancelIntake(medicationId, sequenceNumber, dateString)
        }
    }

    /**
     * Undo cancellation of medication intake
     */
    fun uncancelMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.uncancelIntake(medicationId, sequenceNumber, dateString)
        }
    }

    /**
     * Add additional PRN medication intake
     */
    fun addAsNeededMedication(medicationId: Long) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.addAsNeededIntake(medicationId, dateString)
        }
    }

    /**
     * Delete specific PRN medication intake record
     */
    fun removeAsNeededMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.removeAsNeededIntake(medicationId, sequenceNumber, dateString)
        }
    }

    /**
     * Update taken time
     */
    fun updateTakenAt(medicationId: Long, sequenceNumber: Int, hour: Int, minute: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            // HH:mm形式の文字列を作成
            val newTakenAt = String.format(Locale.ROOT, "%02d:%02d", hour, minute)
            repository.updateIntakeTakenAt(medicationId, sequenceNumber, newTakenAt, dateString)
        }
    }

    fun refreshData() {
        resetLoadingFlags()
        updateDisplayDate()
        loadMedicationsForSelectedDate()
        loadNoteForSelectedDate()
    }

    /**
     * Get data grouped by time
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
                noteLoaded = true
                updateLoadingState()
            }
        }
    }

    /**
     * Check if all data sources are loaded and update loading state
     */
    private fun updateLoadingState() {
        if (medicationsLoaded && noteLoaded) {
            _isLoading.value = false
        }
    }

    /**
     * Save or update note
     */
    fun saveNote(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.saveOrUpdateDailyNote(dateString, content.trim())
        }
    }

    /**
     * Delete note
     */
    fun deleteNote() {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.deleteDailyNote(dateString)
        }
    }

    /**
     * Navigate to previous date with note
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
                    resetLoadingFlags()
                    updateDisplayDate()
                    loadMedicationsForSelectedDate()
                    loadNoteForSelectedDate()
                    _scrollToNote.value = true
                }
            }
        }
    }

    /**
     * Navigate to next date with note
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
                    resetLoadingFlags()
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
     * Check if previous note exists
     */
    suspend fun hasPreviousNote(): Boolean {
        val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
        return repository.getPreviousNote(currentDateString) != null
    }

    /**
     * Check if next note exists
     */
    suspend fun hasNextNote(): Boolean {
        val currentDateString = DateUtils.formatIsoDate(_selectedDate.value)
        return repository.getNextNote(currentDateString) != null
    }

}
