package net.shugo.medicineshield.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import net.shugo.medicineshield.MainActivity
import net.shugo.medicineshield.R

class NotificationHelper(private val context: Context) {

    companion object {
        private const val CHANNEL_ID = "medication_reminders"
        private const val CHANNEL_NAME = "服薬リマインダー"
        private const val CHANNEL_DESCRIPTION = "薬の服用時刻をお知らせします"
    }

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /**
     * 通知チャネルを作成する（Android 8.0以降）
     */
    fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * 服薬通知を表示する
     *
     * @param medications 通知する薬のリスト
     * @param time 服用時刻 (HH:mm形式)
     * @param notificationId 通知ID
     */
    fun showMedicationNotification(
        medications: List<String>,
        time: String,
        notificationId: Int
    ) {
        if (medications.isEmpty()) return

        // 通知本文を作成
        val message = if (medications.size == 1) {
            "${medications[0]} を服用してください"
        } else {
            "${medications.joinToString("、")} を服用してください"
        }

        // 通知をタップした時のIntent
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 通知を作成
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("服用時刻です ($time)")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(notificationId, notification)
    }

    /**
     * 通知をキャンセルする
     *
     * @param notificationId 通知ID
     */
    fun cancelNotification(notificationId: Int) {
        notificationManager.cancel(notificationId)
    }
}
