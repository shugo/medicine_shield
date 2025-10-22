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
        private const val KEY_LANGUAGE = "language"

        // Language constants
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_ENGLISH = "en"
        const val LANGUAGE_JAPANESE = "ja"
        const val LANGUAGE_CHINESE_SIMPLIFIED = "zh-CN"
        const val LANGUAGE_CHINESE_TRADITIONAL = "zh-TW"
        const val LANGUAGE_KOREAN = "ko"
        const val LANGUAGE_FRENCH = "fr"
        const val LANGUAGE_GERMAN = "de"
        const val LANGUAGE_ITALIAN = "it"
        const val LANGUAGE_SPANISH = "es"
        const val LANGUAGE_PORTUGUESE = "pt"
        private const val DEFAULT_LANGUAGE = LANGUAGE_SYSTEM

        val ALL_LANGUAGES = arrayOf(
            LANGUAGE_SYSTEM,
            LANGUAGE_ENGLISH,
            LANGUAGE_JAPANESE,
            LANGUAGE_CHINESE_SIMPLIFIED,
            LANGUAGE_CHINESE_TRADITIONAL,
            LANGUAGE_KOREAN,
            LANGUAGE_FRENCH,
            LANGUAGE_GERMAN,
            LANGUAGE_ITALIAN,
            LANGUAGE_SPANISH,
            LANGUAGE_PORTUGUESE
        )
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
     * Get the current language setting
     * Returns one of: LANGUAGE_SYSTEM, LANGUAGE_ENGLISH, LANGUAGE_JAPANESE,
     * LANGUAGE_CHINESE_SIMPLIFIED, LANGUAGE_CHINESE_TRADITIONAL, LANGUAGE_KOREAN,
     * LANGUAGE_FRENCH, LANGUAGE_GERMAN, LANGUAGE_ITALIAN, LANGUAGE_SPANISH, LANGUAGE_PORTUGUESE
     */
    fun getLanguage(): String {
        return sharedPreferences.getString(KEY_LANGUAGE, DEFAULT_LANGUAGE) ?: DEFAULT_LANGUAGE
    }

    /**
     * Set the language preference
     * @param language One of the LANGUAGE_* constants
     */
    fun setLanguage(language: String) {
        sharedPreferences.edit()
            .putString(KEY_LANGUAGE, language)
            .apply()
    }
}
