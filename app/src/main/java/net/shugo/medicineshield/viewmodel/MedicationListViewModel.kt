package net.shugo.medicineshield.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.*

class MedicationListViewModel(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModel() {

    private val notificationScheduler by lazy {
        NotificationScheduler(context, repository)
    }

    val medications: StateFlow<List<MedicationWithTimes>> = repository
        .getAllMedicationsWithTimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val currentMedications: StateFlow<List<MedicationWithTimes>> = repository
        .getAllMedicationsWithTimes()
        .map { list ->
            val today = normalizeToStartOfDay(System.currentTimeMillis())
            list.filter { medWithTimes ->
                // 現在有効なConfigを取得
                val currentConfig = medWithTimes.configs
                    .filter { it.validTo == null }
                    .maxByOrNull { it.validFrom }
                // endDateがnullまたは今日以降なら「服用中」
                currentConfig?.let { config ->
                    config.medicationEndDate == null || config.medicationEndDate >= today
                } ?: false
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val pastMedications: StateFlow<List<MedicationWithTimes>> = repository
        .getAllMedicationsWithTimes()
        .map { list ->
            val today = normalizeToStartOfDay(System.currentTimeMillis())
            list.filter { medWithTimes ->
                // 現在有効なConfigを取得
                val currentConfig = medWithTimes.configs
                    .filter { it.validTo == null }
                    .maxByOrNull { it.validFrom }
                // endDateが存在し、今日より前なら「過去のお薬」
                currentConfig?.let { config ->
                    config.medicationEndDate != null && config.medicationEndDate < today
                } ?: false
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteMedication(medicationId: Long) {
        viewModelScope.launch {
            repository.deleteMedicationById(medicationId)
            // 通知を再スケジュール（削除された薬の時刻に他の薬がある場合は更新）
            notificationScheduler.rescheduleAllNotifications()
        }
    }

    private fun normalizeToStartOfDay(timestamp: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = timestamp
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }
}
