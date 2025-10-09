package net.shugo.medicineshield.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.data.repository.MedicationRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MedicationListViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    val medications: StateFlow<List<MedicationWithTimes>> = repository
        .getAllMedicationsWithTimes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun deleteMedication(medicationId: Long) {
        viewModelScope.launch {
            repository.deleteMedicationById(medicationId)
        }
    }
}
