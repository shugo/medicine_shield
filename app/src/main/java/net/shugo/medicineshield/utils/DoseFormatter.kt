package net.shugo.medicineshield.utils

import android.annotation.SuppressLint

@SuppressLint("DefaultLocale")
fun formatDose(dose: Double, doseUnit: String?): String {
    val doseValue = String.format("%.1f", dose).replace(".0", "");
    return if (doseUnit != null) {
        "${doseValue}${doseUnit}"
    } else {
        doseValue
    }
}
