package net.shugo.medicineshield.data.preferences

import android.content.Context
import android.content.SharedPreferences

class SettingsPreferences(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    companion object {
        private const val PREFS_NAME = "medicine_shield_settings"
        private const val KEY_NOTIFICATIONS_ENABLED = "notifications_enabled"
        private const val DEFAULT_NOTIFICATIONS_ENABLED = true
        private const val KEY_REMINDER_ENABLED = "reminder_enabled"
        private const val DEFAULT_REMINDER_ENABLED = false
        private const val KEY_REMINDER_DELAY_MINUTES = "reminder_delay_minutes"
        private const val DEFAULT_REMINDER_DELAY_MINUTES = 30
        private const val KEY_DATA_RETENTION_ENABLED = "data_retention_enabled"
        private const val DEFAULT_DATA_RETENTION_ENABLED = false
        private const val KEY_DATA_RETENTION_DAYS = "data_retention_days"
        private const val DEFAULT_DATA_RETENTION_DAYS = 365

        const val MAX_REMINDER_DELAY_MINUTES = 1440 // 24 hours
        const val MIN_DATA_RETENTION_DAYS = 30
    }

    /**
     * Get whether notifications are enabled
     */
    fun isNotificationsEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_NOTIFICATIONS_ENABLED, DEFAULT_NOTIFICATIONS_ENABLED)
    }

    /**
     * Set whether notifications are enabled
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_NOTIFICATIONS_ENABLED, enabled)
            .apply()
    }

    /**
     * Get whether reminder notifications are enabled
     */
    fun isReminderEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_REMINDER_ENABLED, DEFAULT_REMINDER_ENABLED)
    }

    /**
     * Set whether reminder notifications are enabled
     */
    fun setReminderEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_REMINDER_ENABLED, enabled)
            .apply()
    }

    /**
     * Get reminder delay in minutes
     */
    fun getReminderDelayMinutes(): Int {
        return sharedPreferences.getInt(KEY_REMINDER_DELAY_MINUTES, DEFAULT_REMINDER_DELAY_MINUTES)
    }

    /**
     * Set reminder delay in minutes
     */
    fun setReminderDelayMinutes(minutes: Int) {
        sharedPreferences.edit()
            .putInt(KEY_REMINDER_DELAY_MINUTES, minutes)
            .apply()
    }

    /**
     * Get whether data retention is enabled
     */
    fun isDataRetentionEnabled(): Boolean {
        return sharedPreferences.getBoolean(KEY_DATA_RETENTION_ENABLED, DEFAULT_DATA_RETENTION_ENABLED)
    }

    /**
     * Set whether data retention is enabled
     */
    fun setDataRetentionEnabled(enabled: Boolean) {
        sharedPreferences.edit()
            .putBoolean(KEY_DATA_RETENTION_ENABLED, enabled)
            .apply()
    }

    /**
     * Get data retention period in days
     */
    fun getDataRetentionDays(): Int {
        return sharedPreferences.getInt(KEY_DATA_RETENTION_DAYS, DEFAULT_DATA_RETENTION_DAYS)
    }

    /**
     * Set data retention period in days
     */
    fun setDataRetentionDays(days: Int) {
        require(days >= MIN_DATA_RETENTION_DAYS) { "Retention days must be at least $MIN_DATA_RETENTION_DAYS" }
        sharedPreferences.edit()
            .putInt(KEY_DATA_RETENTION_DAYS, days)
            .apply()
    }
}
