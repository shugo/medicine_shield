package net.shugo.medicineshield.notification

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository

class ReminderNotificationScheduler(
    private val context: Context,
    private val repository: MedicationRepository,
    private val alarmScheduler: AlarmScheduler,
    private val settingsPreferences: SettingsPreferences,
    private val pendingIntentFactory: PendingIntentFactory = PendingIntentFactoryImpl()
) {

    companion object {
        const val EXTRA_NOTIFICATION_TIME = "notification_time"
        const val EXTRA_SCHEDULED_DATE = "scheduled_date"
        private const val REMINDER_NOTIFICATION_ID_OFFSET = 100000

        /**
         * Factory function to create ReminderNotificationScheduler with default dependencies
         */
        fun create(context: Context, repository: MedicationRepository): ReminderNotificationScheduler {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            val alarmScheduler = AlarmSchedulerImpl(alarmManager)
            val settingsPreferences = SettingsPreferences(context)
            return ReminderNotificationScheduler(
                context,
                repository,
                alarmScheduler,
                settingsPreferences
            )
        }
    }

    /**
     * Generate reminder notification ID from time
     * Uses offset to avoid conflicts with main notifications
     * Example: "09:00" -> 100900, "14:30" -> 101430
     */
    fun getNotificationIdForTime(time: String): Int {
        val baseId = time.replace(":", "").toIntOrNull() ?: 0
        return REMINDER_NOTIFICATION_ID_OFFSET + baseId
    }

    /**
     * Schedule reminder notification for a specific time
     */
    suspend fun scheduleReminderNotification(
        time: String,
        scheduledDate: String
    ) = withContext(Dispatchers.IO) {
        // Check if reminders are enabled
        if (!settingsPreferences.isNotificationsEnabled() || !settingsPreferences.isReminderEnabled()) {
            return@withContext
        }

        // Calculate trigger time (current time + delay)
        val delayMinutes = settingsPreferences.getReminderDelayMinutes()
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)

        // Create intent for reminder notification
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TIME, time)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
        }

        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            notificationId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule using AlarmScheduler
        alarmScheduler.scheduleAlarm(triggerTime, pendingIntent)
    }

    /**
     * Cancel reminder notification for a specific time
     */
    fun cancelReminderNotification(time: String) {
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, ReminderNotificationReceiver::class.java)
        val pendingIntent = pendingIntentFactory.createBroadcast(
            context,
            notificationId,
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        alarmScheduler.cancelAlarm(pendingIntent)
    }
}
