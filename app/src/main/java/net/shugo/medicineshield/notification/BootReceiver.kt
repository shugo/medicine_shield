package net.shugo.medicineshield.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.repository.MedicationRepository

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

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

                // 通知チャネルを作成
                val notificationHelper = NotificationHelper(context)
                notificationHelper.createNotificationChannel()

                // すべての通知を再スケジュール
                val scheduler = NotificationScheduler(context, repository)
                scheduler.rescheduleAllNotifications()

            } finally {
                pendingResult.finish()
            }
        }
    }
}
