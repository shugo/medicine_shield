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
