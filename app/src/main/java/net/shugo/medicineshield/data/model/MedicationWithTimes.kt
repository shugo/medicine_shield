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
     * 現在有効な設定（単一値としてアクセス）
     * Repositoryが既にフィルタリング済みの場合、configs.firstOrNull()と同等
     */
    val config: MedicationConfig?
        get() = configs.firstOrNull()

    /**
     * 現在有効な時刻を取得
     * @return 現在有効なMedicationTimeのリスト（時刻順にソート済み）
     */
    fun getCurrentTimes(): List<MedicationTime> {
        return times.filter { it.validTo == DateUtils.MAX_DATE }.sortedBy { it.time }
    }

    /**
     * 指定日時に有効な時刻を取得
     * @param targetDate 対象日時のタイムスタンプ
     * @return 指定日時に有効なMedicationTimeのリスト（時刻順にソート済み）
     */
    fun getTimesForDate(targetDate: Long): List<MedicationTime> {
        val targetDateString = formatDateString(targetDate)
        return times
            .filter { it.validFrom <= targetDateString && it.validTo > targetDateString }
            .sortedBy { it.time }
    }

    /**
     * 現在有効な設定を取得
     * @return 現在有効なMedicationConfig、見つからない場合はnull
     */
    fun getCurrentConfig(): MedicationConfig? {
        return configs
            .filter { it.validTo == DateUtils.MAX_DATE }
            .maxByOrNull { it.validFrom }
    }

    /**
     * 指定日時に有効な設定を取得
     * @param targetDate 対象日時のタイムスタンプ
     * @return 指定日時に有効なMedicationConfig、見つからない場合はnull
     */
    fun getConfigForDate(targetDate: Long): MedicationConfig? {
        val targetDateString = formatDateString(targetDate)
        return configs
            .filter { it.validFrom <= targetDateString && it.validTo > targetDateString }
            .maxByOrNull { it.validFrom }
    }

    /**
     * Long型のタイムスタンプをyyyy-MM-dd形式の文字列に変換
     */
    private fun formatDateString(timestamp: Long): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(Date(timestamp))
    }
}
