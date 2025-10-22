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
}
