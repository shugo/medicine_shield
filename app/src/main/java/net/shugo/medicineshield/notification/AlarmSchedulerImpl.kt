package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.app.PendingIntent

/**
 * Default implementation of AlarmScheduler using Android's AlarmManager
 */
class AlarmSchedulerImpl(
    private val alarmManager: AlarmManager
) : AlarmScheduler {
    override fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent) {
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            triggerAtMillis,
            pendingIntent
        )
    }

    override fun cancelAlarm(pendingIntent: PendingIntent) {
        alarmManager.cancel(pendingIntent)
    }
}
