package net.shugo.medicineshield.notification

import android.app.PendingIntent

/**
 * Interface for scheduling and canceling alarms
 * This abstraction allows for easier testing by enabling mock implementations
 */
interface AlarmScheduler {
    /**
     * Schedule an alarm at the specified time
     */
    fun scheduleAlarm(triggerAtMillis: Long, pendingIntent: PendingIntent)

    /**
     * Cancel a scheduled alarm
     */
    fun cancelAlarm(pendingIntent: PendingIntent)
}
