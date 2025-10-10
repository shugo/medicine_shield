package net.shugo.medicineshield.data.model

import androidx.room.Embedded
import androidx.room.Relation

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
     * 現在有効な時刻を取得
     * @return 現在有効なMedicationTimeのリスト（時刻順にソート済み）
     */
    fun getCurrentTimes(): List<MedicationTime> {
        return times.filter { it.validTo == null }.sortedBy { it.time }
    }

    /**
     * 指定日時に有効な時刻を取得
     * @param targetDate 対象日時のタイムスタンプ
     * @return 指定日時に有効なMedicationTimeのリスト（時刻順にソート済み）
     */
    fun getTimesForDate(targetDate: Long): List<MedicationTime> {
        return times
            .filter { it.validFrom <= targetDate && (it.validTo == null || it.validTo > targetDate) }
            .sortedBy { it.time }
    }

    /**
     * 現在有効な設定を取得
     * @return 現在有効なMedicationConfig、見つからない場合はnull
     */
    fun getCurrentConfig(): MedicationConfig? {
        return configs
            .filter { it.validTo == null }
            .maxByOrNull { it.validFrom }
    }

    /**
     * 指定日時に有効な設定を取得
     * @param targetDate 対象日時のタイムスタンプ
     * @return 指定日時に有効なMedicationConfig、見つからない場合はnull
     */
    fun getConfigForDate(targetDate: Long): MedicationConfig? {
        return configs
            .filter { it.validFrom <= targetDate && (it.validTo == null || it.validTo > targetDate) }
            .maxByOrNull { it.validFrom }
    }
}
