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
            list.filter { it.medication.endDate == null || it.medication.endDate >= today }
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
            list.filter { it.medication.endDate != null && it.medication.endDate < today }
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
