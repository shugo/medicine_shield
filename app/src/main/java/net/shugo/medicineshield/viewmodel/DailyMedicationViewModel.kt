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
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationHelper
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.notification.ReminderNotificationScheduler
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

    private val _isToday = MutableStateFlow(false)
    val isToday: StateFlow<Boolean> = _isToday.asStateFlow()

    private val _dailyNote = MutableStateFlow<DailyNote?>(null)
    val dailyNote: StateFlow<DailyNote?> = _dailyNote.asStateFlow()

    private val _scrollToNote = MutableStateFlow(false)
    val scrollToNote: StateFlow<Boolean> = _scrollToNote.asStateFlow()

    private var loadJob: Job? = null
    private var noteLoadJob: Job? = null

    // Data source loading completion flags
    private var medicationsLoaded = false
    private var noteLoaded = false

    // For notification management
    private val notificationHelper = NotificationHelper(application.applicationContext)
    private val notificationScheduler by lazy {
        NotificationScheduler.create(application.applicationContext, repository)
    }
    private val reminderScheduler by lazy {
        ReminderNotificationScheduler.create(application.applicationContext, repository)
    }

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
        // Cancel previous loadJob
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

        // Get latest locale from current Configuration of Application
        val appContext = getApplication<Application>()
        val currentLocale = appContext.resources.configuration.locales[0]
        val pattern = DateFormat.getBestDateTimePattern(currentLocale, "MMMdEEE")
        val dateFormat = SimpleDateFormat(pattern, currentLocale)

        // Check if today
        val today = Calendar.getInstance()
        _isToday.value = calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)

        _displayDateText.value = dateFormat.format(calendar.time)
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
     * Reset loading flags when date changes
     */
    private fun resetLoadingFlags() {
        _isLoading.value = true
        medicationsLoaded = false
        noteLoaded = false
        // Do not reset countLoaded (medication count does not depend on date)
    }

    fun toggleMedicationTaken(medicationId: Long, sequenceNumber: Int, currentStatus: Boolean) {
        viewModelScope.launch {
            // Check if notifications should be dismissed only when marking as taken
            val willBeMarkedAsTaken = !currentStatus

            // Get information of corresponding medication (for notification check)
            val medication = _dailyMedications.value.find {
                it.medicationId == medicationId && it.sequenceNumber == sequenceNumber
            }

            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.updateIntakeStatus(medicationId, sequenceNumber, !currentStatus, dateString)

            // If marked as taken, check notification and reminder for that time
            if (willBeMarkedAsTaken && medication != null && !medication.isAsNeeded) {
                checkAndDismissNotificationIfComplete(medication.scheduledTime, medicationId, sequenceNumber)
            }
        }
    }

    /**
     * Dismiss notification and cancel reminder if all medications at specified time are complete
     *
     * @param time Intake time (HH:mm format)
     * @param justChangedMedId ID of medication just changed (for optimistic update)
     * @param justChangedSeqNum Sequence number of medication just changed (for optimistic update)
     */
    private fun checkAndDismissNotificationIfComplete(
        time: String,
        justChangedMedId: Long,
        justChangedSeqNum: Int
    ) {
        // Get all scheduled medications at that time (excluding PRN)
        val allMedsAtTime = _dailyMedications.value.filter {
            it.scheduledTime == time && !it.isAsNeeded
        }

        if (allMedsAtTime.isEmpty()) return

        // Check if all medications are taken or canceled
        val allComplete = allMedsAtTime.all { med ->
            if (med.medicationId == justChangedMedId && med.sequenceNumber == justChangedSeqNum) {
                true // Assume just changed medication is taken or canceled
            } else {
                med.status == MedicationIntakeStatus.TAKEN ||
                med.status == MedicationIntakeStatus.CANCELED
            }
        }

        if (allComplete) {
            // Dismiss notification and cancel reminder for this time
            val notificationId = notificationScheduler.getNotificationIdForTime(time)
            notificationHelper.cancelNotification(notificationId)
            reminderScheduler.cancelReminderNotification(time)
        }
    }

    /**
     * Cancel medication intake
     */
    fun cancelMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            // Get information of corresponding medication
            val medication = _dailyMedications.value.find {
                it.medicationId == medicationId && it.sequenceNumber == sequenceNumber
            }

            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.cancelIntake(medicationId, sequenceNumber, dateString)

            // Check if notification and reminder should be canceled when medication is canceled
            if (medication != null && !medication.isAsNeeded) {
                checkAndDismissNotificationIfComplete(medication.scheduledTime, medicationId, sequenceNumber)
            }
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
            // Create HH:mm format string
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
        // Cancel previous noteLoadJob
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
                // Convert date string to Calendar
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
                // Convert date string to Calendar
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
     * Reset scroll flag
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
