package net.shugo.medicineshield.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.utils.DateUtils
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
            val today = formatDateString(System.currentTimeMillis())
            list.filter { medWithTimes ->
                // 現在有効なConfigを取得
                val currentConfig = medWithTimes.getCurrentConfig()
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
            val today = formatDateString(System.currentTimeMillis())
            list.filter { medWithTimes ->
                // 現在有効なConfigを取得
                val currentConfig = medWithTimes.getCurrentConfig()
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

    /**
     * Long型のタイムスタンプをyyyy-MM-dd形式の文字列に変換
     */
    private fun formatDateString(timestamp: Long): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(Date(timestamp))
    }
}
