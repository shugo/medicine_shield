package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
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

        // Get medications for today and tomorrow (2 days is enough since this runs daily at midnight)
        val allTimes = mutableSetOf<String>()
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timeProvider.currentTimeMillis()

        for (i in 0 until 2) {
            val dateString = dateFormatter.format(Date(calendar.timeInMillis))
            val medications = repository.getMedications(dateString).first()
            medications.forEach { med ->
                allTimes.add(med.scheduledTime)
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
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

        // Pre-fetch medications for the next few days to avoid repeated queries
        // Since this reschedules daily at midnight, 3 days should be more than enough
        val medicationsByDate = mutableMapOf<String, List<net.shugo.medicineshield.data.model.DailyMedicationItem>>()
        val tempCalendar = Calendar.getInstance().apply { timeInMillis = calendar.timeInMillis }

        for (i in 0 until 3) {
            val dateString = dateFormatter.format(Date(tempCalendar.timeInMillis))
            medicationsByDate[dateString] = repository.getMedications(dateString).first()
            tempCalendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // その時刻に服用すべき薬があるか最大3日先までチェック
        for (i in 0 until 3) {
            val dateString = dateFormatter.format(Date(calendar.timeInMillis))
            val dailyMedications = medicationsByDate[dateString] ?: emptyList()

            // Filter medications for the specified time
            val medicationsAtTime = dailyMedications.filter { it.scheduledTime == time }

            // Check if there are uncompleted medications
            val hasUncompleted = medicationsAtTime.isNotEmpty() && medicationsAtTime.any { item ->
                item.status != net.shugo.medicineshield.data.model.MedicationIntakeStatus.TAKEN
            }

            if (hasUncompleted) {
                return calendar.timeInMillis
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }

}
