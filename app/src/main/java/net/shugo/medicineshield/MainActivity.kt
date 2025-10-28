package net.shugo.medicineshield

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.database.AppDatabase
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationHelper
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.ui.screen.DailyMedicationScreen
import net.shugo.medicineshield.ui.screen.MedicationFormScreen
import net.shugo.medicineshield.ui.screen.MedicationListScreen
import net.shugo.medicineshield.ui.screen.SettingsScreen
import net.shugo.medicineshield.viewmodel.DailyMedicationViewModel
import net.shugo.medicineshield.viewmodel.MedicationFormViewModel
import net.shugo.medicineshield.viewmodel.MedicationListViewModel
import net.shugo.medicineshield.viewmodel.SettingsViewModel

class MainActivity : ComponentActivity() {
    private lateinit var repository: MedicationRepository
    private val scheduledDateState = mutableStateOf<String?>(null)

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // If permission is granted, schedule notifications
            setupNotifications()
        } else {
            // If permission is denied, turn off notifications in settings
            val settingsPreferences = SettingsPreferences(this)
            settingsPreferences.setNotificationsEnabled(false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Enable edge-to-edge display with proper status bar appearance
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        repository = MedicationRepository(
            database.medicationDao(),
            database.medicationTimeDao(),
            database.medicationIntakeDao(),
            database.medicationConfigDao(),
            database.dailyNoteDao()
        )

        // Create notification channel
        val notificationHelper = NotificationHelper(this)
        notificationHelper.createNotificationChannel()

        // Request notification permission
        requestNotificationPermission()

        // Get the date passed when launching from notification
        scheduledDateState.value = intent?.getStringExtra(NotificationHelper.EXTRA_SCHEDULED_DATE)

        setContent {
            MedicineShieldTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MedicineShieldApp(repository, scheduledDateState)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // If launched from a new notification, update the date
        scheduledDateState.value = intent.getStringExtra(NotificationHelper.EXTRA_SCHEDULED_DATE)
    }

    private fun requestNotificationPermission() {
        // Check notification settings
        val settingsPreferences = SettingsPreferences(this)
        if (!settingsPreferences.isNotificationsEnabled()) {
            // If notifications are off, don't request permission
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    // If permission is already granted
                    setupNotifications()
                }
                else -> {
                    // Request permission
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            // Permission not required for Android 13 and below
            setupNotifications()
        }
    }

    private fun setupNotifications() {
        lifecycleScope.launch {
            val scheduler = NotificationScheduler.create(applicationContext, repository)
            scheduler.rescheduleAllNotifications()
        }
    }
}

@Composable
fun MedicineShieldTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val isDarkTheme = isSystemInDarkTheme()

    // Use dynamic color for Android 12 and later
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDarkTheme) {
            dynamicDarkColorScheme(context)
        } else {
            dynamicLightColorScheme(context)
        }
    } else {
        // For Android 12 and below, use default Material 3 color scheme
        if (isDarkTheme) {
            androidx.compose.material3.darkColorScheme()
        } else {
            androidx.compose.material3.lightColorScheme()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

/**
 * Safely pops the back stack, preventing the start destination from being popped
 */
fun NavController.safePopBackStack() {
    if (previousBackStackEntry != null) {
        popBackStack()
    }
}

@Composable
fun MedicineShieldApp(
    repository: MedicationRepository,
    scheduledDateState: MutableState<String?>
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    NavHost(
        navController = navController,
        startDestination = "daily_medication"
    ) {
        composable("daily_medication") {
            val viewModel: DailyMedicationViewModel = viewModel(
                factory = DailyMedicationViewModelFactory(context.applicationContext as Application, repository)
            )

            // If launched from notification, navigate to that date
            val scheduledDate = scheduledDateState.value  // Read State within Composable
            if (scheduledDate != null) {
                LaunchedEffect(scheduledDate) {
                    viewModel.setDateFromNotification(scheduledDate)
                    // Clear after processing so it can be re-executed on the next notification
                    scheduledDateState.value = null
                }
            }

            DailyMedicationScreen(
                viewModel = viewModel,
                onNavigateToMedicationList = {
                    navController.navigate("medication_list")
                },
                onNavigateToSettings = {
                    navController.navigate("settings")
                }
            )
        }

        composable("settings") {
            val viewModel: SettingsViewModel = viewModel(
                factory = SettingsViewModelFactory(context.applicationContext, repository)
            )
            SettingsScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable("medication_list") {
            val viewModel: MedicationListViewModel = viewModel(
                factory = MedicationListViewModelFactory(repository, context)
            )
            MedicationListScreen(
                viewModel = viewModel,
                onNavigateBack = { navController.safePopBackStack() },
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
                factory = MedicationFormViewModelFactory(repository, context)
            )
            MedicationFormScreen(
                viewModel = viewModel,
                isEdit = false,
                onNavigateBack = { navController.safePopBackStack() }
            )
        }

        composable(
            "edit_medication/{medicationId}",
            arguments = listOf(navArgument("medicationId") { type = NavType.LongType })
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getLong("medicationId") ?: 0L
            val viewModel: MedicationFormViewModel = viewModel(
                factory = MedicationFormViewModelFactory(repository, context)
            )
            viewModel.loadMedication(medicationId)
            MedicationFormScreen(
                viewModel = viewModel,
                isEdit = true,
                onNavigateBack = { navController.safePopBackStack() }
            )
        }
    }
}

class MedicationListViewModelFactory(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationListViewModel::class.java)) {
            return MedicationListViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MedicationFormViewModelFactory(
    private val repository: MedicationRepository,
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MedicationFormViewModel::class.java)) {
            return MedicationFormViewModel(repository, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DailyMedicationViewModelFactory(
    private val application: Application,
    private val repository: MedicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DailyMedicationViewModel::class.java)) {
            return DailyMedicationViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class SettingsViewModelFactory(
    private val context: Context,
    private val repository: MedicationRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            return SettingsViewModel(context, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
