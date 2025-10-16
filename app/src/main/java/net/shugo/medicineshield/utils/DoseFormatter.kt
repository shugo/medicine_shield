package net.shugo.medicineshield.utils

import java.text.DecimalFormat
import android.annotation.SuppressLint

fun formatDose(dose: Double, doseUnit: String?): String {
    val doseValue = formatDoseValue(dose)
    return "× ${doseValue}${doseUnit}"
}

@SuppressLint("DefaultLocale")
private fun formatDoseValue(dose: Double): String {
    val formatter = DecimalFormat("#.#")
    return formatter.format(dose)
}
