package net.shugo.medicineshield.utils

import java.util.Calendar

/**
 * 日付関連のユーティリティ関数
 */
object DateUtils {
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
}
