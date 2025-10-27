package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotificationScheduler(
    private val context: Context,
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val settingsPreferences: SettingsPreferences,
    private val timeProvider: TimeProvider = SystemTimeProvider(),
    private val dateFormatter: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT),
    private val pendingIntentFactory: PendingIntentFactory = PendingIntentFactoryImpl()
) {

    companion object {
        const val EXTRA_NOTIFICATION_TIME = "notification_time"
        const val EXTRA_SCHEDULED_DATE = "scheduled_date"
        private const val DAILY_REFRESH_REQUEST_CODE = 999999

        /**
         * Factory function to create NotificationScheduler with default dependencies
         */
        fun create(context: Context, repository: MedicationRepository): NotificationScheduler {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val alarmScheduler = AlarmSchedulerImpl(alarmManager)
            val settingsPreferences = SettingsPreferences(context)
            return NotificationScheduler(
                context,
                repository,
                alarmScheduler,
                settingsPreferences
            )
        }
    }

    /**
     * Generate notification ID from time
     * Example: "09:00" -> 900, "14:30" -> 1430
     */
    fun getNotificationIdForTime(time: String): Int {
        return time.replace(":", "").toIntOrNull() ?: 0
    }

    /**
     * Reschedule all notifications (on reboot, at midnight)
     */
    suspend fun rescheduleAllNotifications() = withContext(Dispatchers.IO) {
        // Do nothing if notifications are disabled
        if (!settingsPreferences.isNotificationsEnabled()) {
            return@withContext
        }

        // Get all medications
        val medications = repository.getAllMedicationsWithTimes().first()

        // Collect all times (only currently valid ones)
        val allTimes = mutableSetOf<String>()
        medications.forEach { med ->
            med.times.forEach { time ->
                allTimes.add(time.time)
            }
        }

        // Schedule next notification for each time
        allTimes.forEach { time ->
            scheduleNextNotificationForTime(time)
        }

        // Set up rescheduling job at midnight
        scheduleDailyRefreshJob()
    }

    /**
     * Schedule next notification for specific time
     */
    suspend fun scheduleNextNotificationForTime(time: String) = withContext(Dispatchers.IO) {
        val nextDateTime = calculateNextNotificationDateTime(time)
        if (nextDateTime == null) {
            // Cancel notification if no matching next date/time
            cancelNotificationForTime(time)
            return@withContext
        }

        // Get list of medications that should be taken at that time
        val medications = getMedicationsForTime(time, nextDateTime)
        if (medications.isEmpty()) {
            // Cancel notification if no medications
            cancelNotificationForTime(time)
            return@withContext
        }

        // Calculate scheduled date (from when notification is triggered)
        val scheduledDate = dateFormatter.format(Date(nextDateTime))

        // Schedule notification
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, MedicationNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TIME, time)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
        }

        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule notification using AlarmScheduler
        alarmScheduler.scheduleAlarm(nextDateTime, pendingIntent)
    }

    /**
     * Cancel notification for a specific time
     */
    fun cancelNotificationForTime(time: String) {
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, MedicationNotificationReceiver::class.java)
        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmScheduler.cancelAlarm(pendingIntent)
    }

    /**
     * Schedule daily refresh job at midnight
     */
    fun scheduleDailyRefreshJob() {
        val calendar = Calendar.getInstance().apply {
            // Set to next midnight if current time is past 00:00:00.000
            if (get(Calendar.HOUR_OF_DAY) > 0 ||
                get(Calendar.MINUTE) > 0 ||
                get(Calendar.SECOND) > 0 ||
                get(Calendar.MILLISECOND) > 0) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, DailyRefreshReceiver::class.java)
        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            DAILY_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmScheduler.scheduleAlarm(calendar.timeInMillis, pendingIntent)
    }

    /**
     * Cancel daily refresh job at midnight
     */
    fun cancelDailyRefreshJob() {
        val intent = Intent(context, DailyRefreshReceiver::class.java)
        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            DAILY_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmScheduler.cancelAlarm(pendingIntent)
    }

    /**
     * Calculate next notification date/time for specified time
     * @return Timestamp of next notification (null if no match)
     */
    private suspend fun calculateNextNotificationDateTime(time: String): Long? {
        val now = timeProvider.currentTimeMillis()
        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
        }

        // 今日の指定時刻
        val timeParts = time.split(":")
        if (timeParts.size != 2) return null

        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // 今日の指定時刻が既に過ぎている場合は明日から
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // その時刻に服用すべき薬があるか最大7日先までチェック
        // 毎日深夜0時に再スケジュールされるため、7日で十分
        for (i in 0 until 7) {
            val medications = getMedicationsForTime(time, calendar.timeInMillis)
            if (medications.isNotEmpty()) {
                // Check if there are any uncompleted medications for this time
                val hasUncompleted = hasUncompletedMedications(time, calendar.timeInMillis)
                if (hasUncompleted) {
                    return calendar.timeInMillis
                }
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }

    /**
     * Check if there are any uncompleted medications for the specified time and date
     * @param time Time string (HH:mm format)
     * @param dateTime Target date/time in milliseconds
     * @return true if there are uncompleted medications, false otherwise
     */
    private suspend fun hasUncompletedMedications(time: String, dateTime: Long): Boolean {
        val dateString = dateFormatter.format(Date(dateTime))
        val dailyMedications = repository.getMedications(dateString).first()

        // Filter medications for the specified time
        val medicationsAtTime = dailyMedications.filter { it.scheduledTime == time }

        // If no medications found at this time, assume uncompleted (no intake records yet)
        if (medicationsAtTime.isEmpty()) {
            return true
        }

        // Check if any are not taken
        return medicationsAtTime.any { item ->
            item.status != net.shugo.medicineshield.data.model.MedicationIntakeStatus.TAKEN
        }
    }

    /**
     * 指定時刻・指定日に服用すべき薬のリストを取得
     */
    private suspend fun getMedicationsForTime(time: String, dateTime: Long): List<MedicationWithTimes> {
        val medications = repository.getAllMedicationsWithTimes().first()

        return medications.filter { medWithTimes ->
            // この薬がその時刻を持っているか
            val times = medWithTimes.times
            val hasTime = times.any { it.time == time }
            if (!hasTime) return@filter false

            val config = medWithTimes.config

            // その日に服用すべきか判定
            config?.let { shouldTakeMedication(it, dateTime) } ?: false
        }
    }

    /**
     * 指定日にその薬を服用すべきかどうか判定（Configベース）
     */
    private fun shouldTakeMedication(config: net.shugo.medicineshield.data.model.MedicationConfig, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // targetDateをyyyy-MM-dd形式の文字列に変換
        val targetDateString = dateFormatter.format(Date(targetDate))

        // 期間チェック（String型での比較、yyyy-MM-ddは辞書順で比較可能）
        if (targetDateString < config.medicationStartDate) return false
        if (targetDateString > config.medicationEndDate) return false

        // Do not schedule notifications for PRN medications
        if (config.isAsNeeded) return false

        return when (config.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // Day of week check (0=Sunday, 1=Monday, ..., 6=Saturday)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = config.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // Every N days check
                val intervalDays = config.cycleValue?.toIntOrNull() ?: return false
                val startDateTimestamp = dateFormatter.parse(config.medicationStartDate)?.time ?: return false
                val daysSinceStart = ((targetDate - startDateTimestamp) / (1000 * 60 * 60 * 24)).toInt()
                daysSinceStart % intervalDays == 0
            }
        }
    }
}
