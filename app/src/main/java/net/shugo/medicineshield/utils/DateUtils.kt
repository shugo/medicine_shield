package net.shugo.medicineshield.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * 日付関連のユーティリティ関数
 */
object DateUtils {
    /**
     * 日付の最小値（過去すべての日付で有効を表す）
     */
    const val MIN_DATE = "0000-01-01"

    /**
     * 日付の最大値（未来永劫有効を表す）
     */
    const val MAX_DATE = "9999-12-31"

    /**
     * タイムスタンプを日付の開始時刻(00:00:00.000)に正規化
     * @param timestamp 対象のタイムスタンプ
     * @return 日付の開始時刻のタイムスタンプ
     */
    fun normalizeToStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    /**
     * ISO8601フォーマットの日付文字列をCalendarオブジェクトに変換
     * @param dateString ISO8601フォーマットの日付文字列
     * @return Calendarオブジェクト
     */
    fun parseIsoDate(dateString: String): Calendar? {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        val date = dateFormatter.parse(dateString)
        if (date == null) return null;
        val calendar = Calendar.getInstance()
        calendar.time = date;
        return calendar;
    }

    /**
     * CalendarオブジェクトをISO8601フォーマットの日付文字列に変換
     * @param calendar 変換対象のCalendarオブジェクト
     * @return ISO8601フォーマットの日付文字列
     */
    fun formatIsoDate(calendar: Calendar): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(calendar.time)
    }
}
