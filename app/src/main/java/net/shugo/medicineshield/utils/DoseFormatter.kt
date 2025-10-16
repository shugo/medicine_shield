package net.shugo.medicineshield.utils

import java.text.DecimalFormat
import java.text.ParseException
import android.annotation.SuppressLint

fun formatDose(dose: Double, doseUnit: String?): String {
    val doseValue = formatDoseValue(dose)
    return "× ${doseValue}${doseUnit ?: ""}"
}

private fun formatDoseValue(dose: Double): String {
    val formatter = DecimalFormat("#.#")
    return formatter.format(dose)
}

/**
 * Format dose for input field (always shows 1 decimal place)
 * Uses locale-specific decimal separator (e.g., "2.5" in en_US, "2,5" in de_DE)
 */
fun formatDoseInput(dose: Double): String {
    val formatter = DecimalFormat("0.0")
    return formatter.format(dose)
}

/**
 * Parse dose from input field string
 * Handles locale-specific decimal separator (e.g., "2.5" or "2,5")
 */
fun parseDoseInput(text: String): Double? {
    if (text.isBlank()) return null

    return try {
        val formatter = DecimalFormat()
        formatter.parse(text)?.toDouble()
    } catch (e: ParseException) {
        null
    }
}
