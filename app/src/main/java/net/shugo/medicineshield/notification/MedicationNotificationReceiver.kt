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

class MedicationNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(NotificationScheduler.EXTRA_NOTIFICATION_TIME) ?: return
        val scheduledDate = intent.getStringExtra(NotificationScheduler.EXTRA_SCHEDULED_DATE) ?: return

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

                // 服薬予定日の薬リストを取得
                val items = repository.getMedications(scheduledDate).first()
                val medications = items.filter { it.scheduledTime == time && !it.isTaken }
                    .map { it.medicationName }

                // 通知を表示
                if (medications.isNotEmpty()) {
                    val notificationHelper = NotificationHelper(context)
                    val notificationId = NotificationScheduler(context, repository)
                        .getNotificationIdForTime(time, scheduledDate)
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
}
