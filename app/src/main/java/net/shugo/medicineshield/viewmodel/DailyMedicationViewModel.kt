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
     * 通知からの初期日付を設定する
     * 通知から起動された場合のみ、その日付に移動する
     *
     * @param dateString 日付文字列 (yyyy-MM-dd形式)
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
            // 服用済みにマークする場合のみ、通知を消すかチェック
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
     * 指定時刻の全ての薬が服用済みになった場合、通知を消す
     *
     * @param time 服用時刻 (HH:mm形式)
     * @param justToggledMedId 今トグルした薬のID（楽観的更新用）
     * @param justToggledSeqNum 今トグルした薬のシーケンス番号（楽観的更新用）
     */
    private fun checkAndDismissNotificationIfComplete(
        time: String,
        justToggledMedId: Long,
        justToggledSeqNum: Int
    ) {
        // その時刻の全ての定時薬を取得（頓服薬は除外）
        val allMedsAtTime = _dailyMedications.value.filter {
            it.scheduledTime == time && !it.isAsNeeded
        }

        // 全ての薬が服用済みかチェック（楽観的更新：今トグルした薬は服用済みと仮定）
        val allTaken = allMedsAtTime.all { med ->
            if (med.medicationId == justToggledMedId && med.sequenceNumber == justToggledSeqNum) {
                true // 今トグルした薬は服用済みと仮定
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
     * 服用をキャンセルする
     */
    fun cancelMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.cancelIntake(medicationId, sequenceNumber, dateString)
        }
    }

    /**
     * 服用のキャンセルを取り消す
     */
    fun uncancelMedication(medicationId: Long, sequenceNumber: Int) {
        viewModelScope.launch {
            val dateString = DateUtils.formatIsoDate(_selectedDate.value)
            repository.uncancelIntake(medicationId, sequenceNumber, dateString)
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
            // HH:mm形式の文字列を作成
            val newTakenAt = String.format("%02d:%02d", hour, minute)
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
                noteLoaded = true
                updateLoadingState()
            }
        }
    }

    /**
     * すべてのデータソースが読み込まれたかチェックし、ローディング状態を更新
     */
    private fun updateLoadingState() {
        if (medicationsLoaded && noteLoaded) {
            _isLoading.value = false
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
