package com.example.medicineshield

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.medicineshield.data.database.AppDatabase
import com.example.medicineshield.data.repository.MedicationRepository
import com.example.medicineshield.ui.screen.MedicationFormScreen
import com.example.medicineshield.ui.screen.MedicationListScreen
import com.example.medicineshield.ui.screen.TodayMedicationScreen
import com.example.medicineshield.viewmodel.MedicationFormViewModel
import com.example.medicineshield.viewmodel.MedicationListViewModel
import com.example.medicineshield.viewmodel.TodayMedicationViewModel

class MainActivity : ComponentActivity() {
    private lateinit var repository: MedicationRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val database = AppDatabase.getDatabase(applicationContext)
        repository = MedicationRepository(
            database.medicationDao(),
            database.medicationTimeDao(),
            database.medicationIntakeDao()
        )

        setContent {
            MedicineShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MedicineShieldApp(repository)
                }
            }
        }
    }
}

@Composable
fun MedicineShieldTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        content = content
    )
}

@Composable
fun MedicineShieldApp(repository: MedicationRepository) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "today_medication"
    ) {
        composable("today_medication") {
            val viewModel: TodayMedicationViewModel = viewModel(
                factory = TodayMedicationViewModelFactory(repository)
            )
            TodayMedicationScreen(
                viewModel = viewModel,
                onNavigateToMedicationList = {
                    navController.navigate("medication_list")
                }
            )
        }

        composable("medication_list") {
            val viewModel: MedicationListViewModel = viewModel(
                factory = MedicationListViewModelFactory(repository)
            )
            MedicationListScreen(
                viewModel = viewModel,
                onAddMedication = {
                    navController.navigate("add_medication")
                },
                onEditMedication = { medicationId ->
                    navController.navigate("edit_medication/$medicationId")
                }
            )
        }

        composable("add_medication") {
            val viewModel: MedicationFormViewModel = viewModel(
                factory = MedicationFormViewModelFactory(repository)
            )
            MedicationFormScreen(
                viewModel = viewModel,
                isEdit = false,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            "edit_medication/{medicationId}",
            arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
            val viewModel: MedicationFormViewModel = viewModel(
                factory = MedicationFormViewModelFactory(repository)
            )
            viewModel.loadMedication(medicationId)
            MedicationFormScreen(
                viewModel = viewModel,
                isEdit = true,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}

class MedicationListViewModelFactory(
    private val repository: MedicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationListViewModel::class.java)) {
            return MedicationListViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MedicationFormViewModelFactory(
    private val repository: MedicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationFormViewModel::class.java)) {
            return MedicationFormViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class TodayMedicationViewModelFactory(
    private val repository: MedicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TodayMedicationViewModel::class.java)) {
            return TodayMedicationViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
