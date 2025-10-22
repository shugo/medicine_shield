package net.shugo.medicineshield.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.Locale

/**
 * Helper object for managing application locale/language settings.
 * Provides functionality to wrap Context with custom locale and convert language codes to Locale objects.
 */
object LocaleHelper {

    /**
     * Wraps the given context with a custom locale based on the language code.
     * If language is "system", returns the original context without modification.
     *
     * @param context The base context to wrap
     * @param language Language code (e.g., "en", "ja", "zh-CN", "zh-TW", "ko", "fr", "de", "it", "es", "pt", or "system")
     * @return Context configured with the specified locale
     */
    fun wrap(context: Context, language: String): Context {
        if (language == "system") {
            return context
        }

        val locale = getLocale(language)
        Locale.setDefault(locale)

        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)

        return context.createConfigurationContext(config)
    }

    /**
     * Converts a language code string to a Locale object.
     * Supports language codes with region variants (e.g., "zh-CN", "zh-TW").
     *
     * @param language Language code (e.g., "en", "ja", "zh-CN", "zh-TW", "ko", "fr", "de", "it", "es", "pt")
     * @return Locale object corresponding to the language code
     */
    fun getLocale(language: String): Locale {
        return when (language) {
            "zh-CN" -> Locale.SIMPLIFIED_CHINESE
            "zh-TW" -> Locale.TRADITIONAL_CHINESE
            "ko" -> Locale.KOREAN
            "ja" -> Locale.JAPANESE
            "en" -> Locale.ENGLISH
            "fr" -> Locale.FRENCH
            "de" -> Locale.GERMAN
            "it" -> Locale.ITALIAN
            "es" -> Locale.Builder().setLanguage("es").build()
            "pt" -> Locale.Builder().setLanguage("pt").build()
            else -> {
                // Handle generic language-country codes using Locale.Builder
                val parts = language.split("-")
                if (parts.size == 2) {
                    Locale.Builder()
                        .setLanguage(parts[0])
                        .setRegion(parts[1])
                        .build()
                } else {
                    Locale.Builder()
                        .setLanguage(language)
                        .build()
                }
            }
        }
    }
}
