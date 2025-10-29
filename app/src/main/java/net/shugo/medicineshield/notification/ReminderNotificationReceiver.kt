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

class ReminderNotificationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val time = intent.getStringExtra(ReminderNotificationScheduler.EXTRA_NOTIFICATION_TIME) ?: return
        val scheduledDate = intent.getStringExtra(ReminderNotificationScheduler.EXTRA_SCHEDULED_DATE) ?: return
        val medicationId = intent.getLongExtra(ReminderNotificationScheduler.EXTRA_MEDICATION_ID, -1L)
        val sequenceNumber = intent.getIntExtra(ReminderNotificationScheduler.EXTRA_SEQUENCE_NUMBER, -1)

        if (medicationId == -1L || sequenceNumber == -1) return

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

                // Find the specific medication
                val medication = items.find {
                    it.medicationId == medicationId &&
                    it.sequenceNumber == sequenceNumber &&
                    it.scheduledTime == time
                }

                // Only show reminder if medication is still UNCHECKED
                if (medication != null && medication.status == MedicationIntakeStatus.UNCHECKED) {
                    val notificationHelper = NotificationHelper(context)
                    val scheduler = NotificationScheduler.create(context, repository)
                    val notificationId = scheduler.getNotificationIdForTime(time)

                    // Show reminder notification
                    notificationHelper.showReminderNotification(
                        medication.medicationName,
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
