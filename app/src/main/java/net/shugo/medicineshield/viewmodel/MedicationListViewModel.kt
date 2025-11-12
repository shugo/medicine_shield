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
        NotificationScheduler.create(context, repository)
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
                // Get current valid Config
                val currentConfig = medWithTimes.getCurrentConfig()
                // If endDate is today or later, "Currently taking" (includes MAX_DATE case)
                currentConfig?.let { config ->
                    config.medicationEndDate >= today
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
                // Get current valid Config
                val currentConfig = medWithTimes.getCurrentConfig()
                // If endDate is before today, "Past medications"
                currentConfig?.let { config ->
                    config.medicationEndDate < today
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
            // Reschedule notifications (update if other medications exist at deleted medication's time)
            notificationScheduler.rescheduleAllNotifications()
        }
    }

    fun stopMedication(medicationId: Long) {
        viewModelScope.launch {
            repository.stopMedication(medicationId)
            // Reschedule notifications (medication will no longer appear in daily list from today)
            notificationScheduler.rescheduleAllNotifications()
        }
    }

    /**
     * Convert Long timestamp to yyyy-MM-dd format string
     */
    private fun formatDateString(timestamp: Long): String {
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
        return dateFormatter.format(Date(timestamp))
    }
}
