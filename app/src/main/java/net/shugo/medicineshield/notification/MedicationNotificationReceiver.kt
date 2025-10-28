package net.shugo.medicineshield.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.data.repository.MedicationRepository

class MedicationNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(NotificationScheduler.EXTRA_NOTIFICATION_TIME) ?: return
        val scheduledDate = intent.getStringExtra(NotificationScheduler.EXTRA_SCHEDULED_DATE) ?: return

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

                // Get medications for the scheduled date
                val items = repository.getMedications(scheduledDate).first()
                val medications = items.filter {
                    it.scheduledTime == time && it.status != MedicationIntakeStatus.TAKEN
                }.map { it.medicationName }

                // Create NotificationScheduler
                val scheduler = NotificationScheduler.create(context, repository)

                // Show notification
                if (medications.isNotEmpty()) {
                    val notificationHelper = NotificationHelper(context)
                    val notificationId = scheduler.getNotificationIdForTime(time)
                    notificationHelper.showMedicationNotification(
                        medications,
                        time,
                        notificationId,
                        scheduledDate
                    )
                }

                // Schedule next notification
                scheduler.scheduleNextNotificationForTime(time)

            } finally {
                pendingResult.finish()
            }
        }
    }
}
