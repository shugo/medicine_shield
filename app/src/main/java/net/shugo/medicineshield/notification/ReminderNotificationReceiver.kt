package net.shugo.medicineshield.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.repository.MedicationRepository

class ReminderNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(ReminderNotificationScheduler.EXTRA_NOTIFICATION_TIME) ?: return
        val scheduledDate = intent.getStringExtra(ReminderNotificationScheduler.EXTRA_SCHEDULED_DATE) ?: return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Initialize repository
                val database = AppDatabase.getDatabase(context)
                val repository = MedicationRepository(
                    database.medicationDao(),
                    database.medicationTimeDao(),
                    database.medicationIntakeDao(),
                    database.medicationConfigDao(),
                    database.dailyNoteDao()
                )

                // Get unchecked medications at the specified time
                val medications = repository.getUncheckedMedicationNamesAtTime(scheduledDate, time)

                // Show reminder notification if there are any unchecked medications
                if (medications.isNotEmpty()) {
                    val notificationHelper = NotificationHelper(context)
                    val reminderScheduler = ReminderNotificationScheduler.create(context, repository)
                    val notificationId = reminderScheduler.getNotificationIdForTime(time)

                    // Show reminder notification with all unchecked medications
                    notificationHelper.showReminderNotification(
                        medications,
                        time,
                        notificationId,
                        scheduledDate
                    )
                }

            } finally {
                pendingResult.finish()
            }
        }
    }
}
