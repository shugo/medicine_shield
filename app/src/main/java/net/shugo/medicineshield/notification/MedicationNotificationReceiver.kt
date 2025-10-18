package net.shugo.medicineshield.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.repository.MedicationRepository
import java.text.SimpleDateFormat
import java.util.*

class MedicationNotificationReceiver : BroadcastReceiver() {
    // 固定フォーマットの日付パース/フォーマット用のSimpleDateFormat
    // Locale.ROOTを使用してロケール依存の動作を避ける
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(NotificationScheduler.EXTRA_NOTIFICATION_TIME) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Repositoryを初期化
                val database = AppDatabase.getDatabase(context)
                val repository = MedicationRepository(
                    database.medicationDao(),
                    database.medicationTimeDao(),
                    database.medicationIntakeDao(),
                    database.medicationConfigDao(),
                    database.dailyNoteDao()
                )

                // 服薬予定日を計算（日をまたいだ場合を考慮）
                val scheduledDate = calculateScheduledDate(time)

                // 服薬予定日の薬リストを取得
                val items = repository.getMedications(scheduledDate).first()
                val medications = items.filter { it.scheduledTime == time && !it.isTaken }
                    .map { it.medicationName }

                // 通知を表示
                if (medications.isNotEmpty()) {
                    val notificationHelper = NotificationHelper(context)
                    val notificationId = NotificationScheduler(context, repository)
                        .getNotificationIdForTime(time)
                    notificationHelper.showMedicationNotification(
                        medications,
                        time,
                        notificationId,
                        scheduledDate
                    )
                }

                // 次回の通知をスケジュール
                val scheduler = NotificationScheduler(context, repository)
                scheduler.scheduleNextNotificationForTime(time)

            } finally {
                pendingResult.finish()
            }
        }
    }

    /**
     * 服薬予定日を計算（日をまたいだ場合を考慮）
     *
     * 通知がトリガーされた時刻が、通知予定時刻より前の場合は昨日の予定とみなす
     * （例：深夜0時過ぎに前日23:00の通知をタップした場合）
     *
     * @param scheduledTime 服薬予定時刻 (HH:mm形式)
     * @return 服薬予定日 (yyyy-MM-dd形式)
     */
    private fun calculateScheduledDate(scheduledTime: String): String {
        val now = Calendar.getInstance()
        val currentHour = now.get(Calendar.HOUR_OF_DAY)
        val currentMinute = now.get(Calendar.MINUTE)

        // 通知時刻をパース
        val timeParts = scheduledTime.split(":")
        if (timeParts.size != 2) {
            // パースできない場合は今日の日付を返す
            return dateFormatter.format(now.time)
        }

        val scheduledHour = timeParts[0].toIntOrNull() ?: return dateFormatter.format(now.time)
        val scheduledMinute = timeParts[1].toIntOrNull() ?: return dateFormatter.format(now.time)

        // 現在時刻と通知予定時刻を分単位で比較
        val currentTimeInMinutes = currentHour * 60 + currentMinute
        val scheduledTimeInMinutes = scheduledHour * 60 + scheduledMinute

        // 現在時刻が通知予定時刻より前の場合、日をまたいだとみなして昨日の日付を返す
        if (currentTimeInMinutes < scheduledTimeInMinutes) {
            now.add(Calendar.DAY_OF_YEAR, -1)
        }

        return dateFormatter.format(now.time)
    }
}
