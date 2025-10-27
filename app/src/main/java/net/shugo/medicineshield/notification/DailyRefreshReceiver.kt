package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository

class DailyRefreshReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
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
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val alarmScheduler = AlarmSchedulerImpl(alarmManager)
                val settingsPreferences = SettingsPreferences(context)
                val scheduler = NotificationScheduler(
                    context,
                    repository,
                    alarmScheduler,
                    settingsPreferences
                )
                scheduler.rescheduleAllNotifications()

            } finally {
                pendingResult.finish()
            }
        }
    }
}
