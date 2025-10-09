package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.repository.MedicationRepository
import java.text.SimpleDateFormat
import java.util.*

class NotificationScheduler(
    private val context: Context,
    private val repository: MedicationRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        const val EXTRA_NOTIFICATION_TIME = "notification_time"
        private const val DAILY_REFRESH_REQUEST_CODE = 999999
    }

    /**
     * 時刻から通知IDを生成
     * 例: "09:00" -> 900, "14:30" -> 1430
     */
    fun getNotificationIdForTime(time: String): Int {
        return time.replace(":", "").toIntOrNull() ?: 0
    }

    /**
     * すべての通知を再スケジュールする（再起動時・深夜0時）
     */
    suspend fun rescheduleAllNotifications() = withContext(Dispatchers.IO) {
        // すべての薬を取得
        val medications = mutableListOf<MedicationWithTimes>()
        repository.getAllMedicationsWithTimes().collect { list ->
            medications.clear()
            medications.addAll(list)
        }

        // すべての時刻を収集
        val allTimes = mutableSetOf<String>()
        medications.forEach { med ->
            med.times.forEach { time ->
                allTimes.add(time.time)
            }
        }

        // 各時刻について次回の通知をスケジュール
        allTimes.forEach { time ->
            scheduleNextNotificationForTime(time)
        }

        // 深夜0時の再スケジュールジョブを設定
        scheduleDailyRefreshJob()
    }

    /**
     * 特定の時刻の次回通知をスケジュールする
     */
    suspend fun scheduleNextNotificationForTime(time: String) = withContext(Dispatchers.IO) {
        val nextDateTime = calculateNextNotificationDateTime(time)
        if (nextDateTime == null) {
            // 該当する次回日時がない場合は通知をキャンセル
            cancelNotificationForTime(time)
            return@withContext
        }

        // その時刻に服用すべき薬のリストを取得
        val medications = getMedicationsForTime(time, nextDateTime)
        if (medications.isEmpty()) {
            // 薬がない場合は通知をキャンセル
            cancelNotificationForTime(time)
            return@withContext
        }

        // 通知をスケジュール
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, MedicationNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TIME, time)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // AlarmManagerで正確な時刻に通知を設定
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    nextDateTime,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                nextDateTime,
                pendingIntent
            )
        }
    }

    /**
     * 特定時刻の通知をキャンセルする
     */
    fun cancelNotificationForTime(time: String) {
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, MedicationNotificationReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    /**
     * 毎日深夜0時の再スケジュールジョブを設定
     */
    fun scheduleDailyRefreshJob() {
        val calendar = Calendar.getInstance().apply {
            if (get(Calendar.HOUR_OF_DAY) >= 0) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val intent = Intent(context, DailyRefreshReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    pendingIntent
                )
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.timeInMillis,
                pendingIntent
            )
        }
    }

    /**
     * 指定時刻の次回通知日時を計算する
     * @return 次回通知日時のタイムスタンプ（該当なしの場合null）
     */
    private suspend fun calculateNextNotificationDateTime(time: String): Long? {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        // 今日の指定時刻
        val timeParts = time.split(":")
        if (timeParts.size != 2) return null

        val hour = timeParts[0].toIntOrNull() ?: return null
        val minute = timeParts[1].toIntOrNull() ?: return null

        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)

        // 今日の指定時刻が既に過ぎている場合は明日から
        if (calendar.timeInMillis <= now) {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        // その時刻に服用すべき薬があるか最大365日先までチェック
        for (i in 0 until 365) {
            val medications = getMedicationsForTime(time, calendar.timeInMillis)
            if (medications.isNotEmpty()) {
                return calendar.timeInMillis
            }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }

        return null
    }

    /**
     * 指定時刻・指定日に服用すべき薬のリストを取得
     */
    private suspend fun getMedicationsForTime(time: String, dateTime: Long): List<MedicationWithTimes> {
        val medications = mutableListOf<MedicationWithTimes>()
        repository.getAllMedicationsWithTimes().collect { list ->
            medications.clear()
            medications.addAll(list)
        }

        return medications.filter { medWithTimes ->
            // この薬がその時刻を持っているか
            val hasTime = medWithTimes.times.any { it.time == time }
            if (!hasTime) return@filter false

            // その日に服用すべきか判定
            shouldTakeMedication(medWithTimes.medication, dateTime)
        }
    }

    /**
     * 指定日にその薬を服用すべきかどうか判定
     */
    private fun shouldTakeMedication(medication: net.shugo.medicineshield.data.model.Medication, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // 日付のみで比較するため、時刻を00:00:00にリセット
        val normalizedTargetDate = normalizeToStartOfDay(targetDate)
        val normalizedStartDate = normalizeToStartOfDay(medication.startDate)
        val normalizedEndDate = medication.endDate?.let { normalizeToStartOfDay(it) }

        // 期間チェック
        if (normalizedTargetDate < normalizedStartDate) return false
        if (normalizedEndDate != null && normalizedTargetDate > normalizedEndDate) return false

        return when (medication.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // 曜日チェック (0=日曜, 1=月曜, ..., 6=土曜)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = medication.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // N日ごとチェック
                val intervalDays = medication.cycleValue?.toIntOrNull() ?: return false
                val daysSinceStart = ((normalizedTargetDate - normalizedStartDate) / (1000 * 60 * 60 * 24)).toInt()
                daysSinceStart % intervalDays == 0
            }
        }
    }

    /**
     * タイムスタンプを日付の開始時刻(00:00:00.000)に正規化
     */
    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
