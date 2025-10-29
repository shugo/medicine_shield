package net.shugo.medicineshield.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import net.shugo.medicineshield.MainActivity
import net.shugo.medicineshield.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "medication_reminders"
        private const val REMINDER_CHANNEL_ID = "medication_reminder_notifications"
        const val EXTRA_SCHEDULED_DATE = "scheduled_date"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * Create notification channel (Android 8.0 and above)
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // Main notification channel
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_description)
                enableVibration(true)
                enableLights(true)
                setSound(soundUri, null)
            }
            notificationManager.createNotificationChannel(channel)

            // Reminder notification channel
            val reminderChannel = NotificationChannel(
                REMINDER_CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.reminder_channel_description)
                enableVibration(true)
                enableLights(true)
                setSound(soundUri, null)
            }
            notificationManager.createNotificationChannel(reminderChannel)
        }
    }

    /**
     * Show medication notification
     *
     * @param medications List of medications to notify
     * @param time Intake time (HH:mm format)
     * @param notificationId Notification ID
     * @param scheduledDate Scheduled intake date (yyyy-MM-dd format)
     */
    fun showMedicationNotification(
        medications: List<String>,
        time: String,
        notificationId: Int,
        scheduledDate: String
    ) {
        if (medications.isEmpty()) return

        // Create notification message
        val message = if (medications.size == 1) {
            context.getString(R.string.notification_message_single, medications[0])
        } else {
            val separator = context.getString(R.string.medication_list_separator)
            context.getString(R.string.notification_message_multiple, medications.joinToString(separator))
        }

        // Intent when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.notification_title, time))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Show reminder notification
     *
     * @param medications List of medication names to remind
     * @param time Original scheduled time (HH:mm format)
     * @param notificationId Notification ID
     * @param scheduledDate Scheduled intake date (yyyy-MM-dd format)
     */
    fun showReminderNotification(
        medications: List<String>,
        time: String,
        notificationId: Int,
        scheduledDate: String
    ) {
        if (medications.isEmpty()) return

        // Intent when notification is tapped
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Create notification message
        val separator = context.getString(R.string.medication_list_separator)
        val medicationList = medications.joinToString(separator)
        val message = context.getString(R.string.reminder_notification_message, medicationList)

        // Create notification
        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notification = NotificationCompat.Builder(context, REMINDER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentTitle(context.getString(R.string.reminder_notification_title, time))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * Cancel notification
     *
     * @param notificationId Notification ID
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
