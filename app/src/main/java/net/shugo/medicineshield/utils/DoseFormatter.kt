package net.shugo.medicineshield.utils

import java.text.DecimalFormat
import java.text.ParseException

/**
 * Formats the dose string, replacing "(s)" in the unit for pluralization.
 * Assumes doseUnit ends with "(s)" for countable units (e.g., "tablet(s)").
 * If doseUnit does not end with "(s)", pluralization is not applied.
 */
fun formatDose(doseFormat: String, dose: Double, doseUnit: String?): String {
    val value = formatDoseValue(dose)
    val replacement = if (value == "1") "" else "s"
    val unit = doseUnit?.replace(Regex("\\(s\\)$"), replacement) ?: ""
    return String.format(doseFormat, value, unit).trim() // trim in case unit is empty
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
