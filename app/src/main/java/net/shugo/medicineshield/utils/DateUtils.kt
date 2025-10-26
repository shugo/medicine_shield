package net.shugo.medicineshield.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Date-related utility functions
 */
object DateUtils {
    /**
     * Minimum date (represents valid for all past dates)
     */
    const val MIN_DATE = "0000-01-01"

    /**
     * Maximum date (represents valid forever in the future)
     */
    const val MAX_DATE = "9999-12-31"

    /**
     * Normalize timestamp to start of day (00:00:00.000)
     * @param timestamp target timestamp
     * @return timestamp at start of day
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
     * Convert ISO8601 format date string to Calendar object
     * @param dateString ISO8601 format date string
     * @return Calendar object
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
     * Convert Calendar object to ISO8601 format date string
     * @param calendar Calendar object to convert
     * @return ISO8601 format date string
     */
    fun formatIsoDate(calendar: Calendar): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(calendar.time)
    }
}
