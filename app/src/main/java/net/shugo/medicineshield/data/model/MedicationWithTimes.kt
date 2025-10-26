package net.shugo.medicineshield.data.model

import androidx.room.Embedded
import androidx.room.Relation
import net.shugo.medicineshield.utils.DateUtils
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MedicationWithTimes(
    @Embedded val medication: Medication,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val times: List<MedicationTime>,
    @Relation(
        parentColumn = "id",
        entityColumn = "medicationId"
    )
    val configs: List<MedicationConfig> = emptyList()
) {
    /**
     * Currently valid config (access as single value)
     * Equivalent to configs.firstOrNull() if Repository already filtered
     */
    val config: MedicationConfig?
        get() = configs.firstOrNull()

    /**
     * Get currently valid times
     * @return List of currently valid MedicationTime (sorted by time)
     */
    fun getCurrentTimes(): List<MedicationTime> {
        return times.filter { it.validTo == DateUtils.MAX_DATE }.sortedBy { it.time }
    }

    /**
     * Get times valid at specified date/time
     * @param targetDate timestamp of target date/time
     * @return List of MedicationTime valid at specified date/time (sorted by time)
     */
    fun getTimesForDate(targetDate: Long): List<MedicationTime> {
        val targetDateString = formatDateString(targetDate)
        return times
            .filter { it.validFrom <= targetDateString && it.validTo > targetDateString }
            .sortedBy { it.time }
    }

    /**
     * Get currently valid config
     * @return Currently valid MedicationConfig, null if not found
     */
    fun getCurrentConfig(): MedicationConfig? {
        return configs
            .filter { it.validTo == DateUtils.MAX_DATE }
            .maxByOrNull { it.validFrom }
    }

    /**
     * Get config valid at specified date/time
     * @param targetDate timestamp of target date/time
     * @return MedicationConfig valid at specified date/time, null if not found
     */
    fun getConfigForDate(targetDate: Long): MedicationConfig? {
        val targetDateString = formatDateString(targetDate)
        return configs
            .filter { it.validFrom <= targetDateString && it.validTo > targetDateString }
            .maxByOrNull { it.validFrom }
    }

    /**
     * Convert Long timestamp to yyyy-MM-dd format string
     */
    private fun formatDateString(timestamp: Long): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(Date(timestamp))
    }
}
