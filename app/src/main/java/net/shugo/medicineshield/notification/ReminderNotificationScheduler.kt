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
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_SEQUENCE_NUMBER = "sequence_number"
        private const val REMINDER_REQUEST_CODE_BASE = 100000

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
     * Generate reminder notification ID
     * Uses a different range from main notifications to avoid conflicts
     */
    private fun getReminderNotificationId(medicationId: Long, sequenceNumber: Int): Int {
        return REMINDER_REQUEST_CODE_BASE + (medicationId.toInt() * 1000) + sequenceNumber
    }

    /**
     * Schedule reminder notification for a specific medication intake
     */
    suspend fun scheduleReminderNotification(
        medicationId: Long,
        sequenceNumber: Int,
        scheduledDate: String,
        scheduledTime: String
    ) = withContext(Dispatchers.IO) {
        // Check if reminders are enabled
        if (!settingsPreferences.isNotificationsEnabled() || !settingsPreferences.isReminderEnabled()) {
            return@withContext
        }

        // Calculate trigger time (current time + delay)
        val delayMinutes = settingsPreferences.getReminderDelayMinutes()
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000)

        // Create intent for reminder notification
        val notificationId = getReminderNotificationId(medicationId, sequenceNumber)
        val intent = Intent(context, ReminderNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TIME, scheduledTime)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
            putExtra(EXTRA_MEDICATION_ID, medicationId)
            putExtra(EXTRA_SEQUENCE_NUMBER, sequenceNumber)
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
     * Cancel reminder notification for a specific medication intake
     */
    fun cancelReminderNotification(medicationId: Long, sequenceNumber: Int) {
        val notificationId = getReminderNotificationId(medicationId, sequenceNumber)
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
