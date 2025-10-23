package net.shugo.medicineshield.notification

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.*

class NotificationScheduler(
    private val context: Context,
    private val repository: MedicationRepository
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val settingsPreferences = SettingsPreferences(context)
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    companion object {
        const val EXTRA_NOTIFICATION_TIME = "notification_time"
        const val EXTRA_SCHEDULED_DATE = "scheduled_date"
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
        // 通知が無効な場合は何もしない
        if (!settingsPreferences.isNotificationsEnabled()) {
            return@withContext
        }

        // すべての薬を取得
        val medications = repository.getAllMedicationsWithTimes().first()

        // すべての時刻を収集（現在有効なもののみ）
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

        // 服薬予定日を計算（通知がトリガーされる日時から）
        val scheduledDate = dateFormatter.format(Date(nextDateTime))

        // 通知をスケジュール
        val notificationId = getNotificationIdForTime(time)
        val intent = Intent(context, MedicationNotificationReceiver::class.java).apply {
            putExtra(EXTRA_NOTIFICATION_TIME, time)
            putExtra(EXTRA_SCHEDULED_DATE, scheduledDate)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // AlarmManagerで通知を設定（正確な時刻ではないが、薬の通知には十分）
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextDateTime,
            pendingIntent
        )
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
            // 現在時刻が00:00:00.000より後であれば翌日に設定
            if (get(Calendar.HOUR_OF_DAY) > 0 ||
                get(Calendar.MINUTE) > 0 ||
                get(Calendar.SECOND) > 0 ||
                get(Calendar.MILLISECOND) > 0) {
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

        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            pendingIntent
        )
    }

    /**
     * 深夜0時の再スケジュールジョブをキャンセル
     */
    fun cancelDailyRefreshJob() {
        val intent = Intent(context, DailyRefreshReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            DAILY_REFRESH_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
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

        // その時刻に服用すべき薬があるか最大7日先までチェック
        // 毎日深夜0時に再スケジュールされるため、7日で十分
        for (i in 0 until 7) {
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
        val medications = repository.getAllMedicationsWithTimes().first()
        val normalizedDateTime = DateUtils.normalizeToStartOfDay(dateTime)

        return medications.filter { medWithTimes ->
            // この薬がその時刻を持っているか（その日に有効な時刻）
            val validTimes = medWithTimes.getTimesForDate(normalizedDateTime)
            val hasTime = validTimes.any { it.time == time }
            if (!hasTime) return@filter false

            // その日に有効なConfigを取得
            val validConfig = medWithTimes.getConfigForDate(normalizedDateTime)

            // その日に服用すべきか判定
            validConfig?.let { shouldTakeMedication(it, dateTime) } ?: false
        }
    }

    /**
     * 指定日にその薬を服用すべきかどうか判定（Configベース）
     */
    private fun shouldTakeMedication(config: net.shugo.medicineshield.data.model.MedicationConfig, targetDate: Long): Boolean {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = targetDate

        // targetDateをyyyy-MM-dd形式の文字列に変換
        val targetDateString = dateFormatter.format(Date(targetDate))

        // 期間チェック（String型での比較、yyyy-MM-ddは辞書順で比較可能）
        if (targetDateString < config.medicationStartDate) return false
        if (targetDateString > config.medicationEndDate) return false

        // 頓服薬は通知をスケジュールしない
        if (config.isAsNeeded) return false

        return when (config.cycleType) {
            CycleType.DAILY -> true

            CycleType.WEEKLY -> {
                // 曜日チェック (0=日曜, 1=月曜, ..., 6=土曜)
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK) - 1
                val allowedDays = config.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                dayOfWeek in allowedDays
            }

            CycleType.INTERVAL -> {
                // N日ごとチェック
                val intervalDays = config.cycleValue?.toIntOrNull() ?: return false
                val startDateTimestamp = dateFormatter.parse(config.medicationStartDate)?.time ?: return false
                val daysSinceStart = ((targetDate - startDateTimestamp) / (1000 * 60 * 60 * 24)).toInt()
                daysSinceStart % intervalDays == 0
            }
        }
    }
}
