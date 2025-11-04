package net.shugo.medicineshield.viewmodel

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.data.repository.MedicationRepository
import net.shugo.medicineshield.notification.NotificationScheduler
import net.shugo.medicineshield.notification.ReminderNotificationScheduler

class SettingsViewModel(
    private val context: Context,
    private val repository: MedicationRepository
) : ViewModel() {

    private val settingsPreferences = SettingsPreferences(context)
    private val notificationScheduler by lazy {
        NotificationScheduler.create(context, repository)
    }
    private val reminderScheduler by lazy {
        ReminderNotificationScheduler.create(context, repository)
    }

    private val _notificationsEnabled = MutableStateFlow(settingsPreferences.isNotificationsEnabled())
    val notificationsEnabled: StateFlow<Boolean> = _notificationsEnabled.asStateFlow()

    private val _reminderEnabled = MutableStateFlow(settingsPreferences.isReminderEnabled())
    val reminderEnabled: StateFlow<Boolean> = _reminderEnabled.asStateFlow()

    private val _reminderDelayMinutes = MutableStateFlow(settingsPreferences.getReminderDelayMinutes())
    val reminderDelayMinutes: StateFlow<Int> = _reminderDelayMinutes.asStateFlow()

    private val _dataRetentionEnabled = MutableStateFlow(settingsPreferences.isDataRetentionEnabled())
    val dataRetentionEnabled: StateFlow<Boolean> = _dataRetentionEnabled.asStateFlow()

    private val _dataRetentionDays = MutableStateFlow(settingsPreferences.getDataRetentionDays())
    val dataRetentionDays: StateFlow<Int> = _dataRetentionDays.asStateFlow()

    val appVersion: String by lazy {
        try {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            packageInfo.versionName ?: "Unknown"
        } catch (e: PackageManager.NameNotFoundException) {
            "Unknown"
        }
    }

    /**
     * Toggle notification enabled/disabled
     */
    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            // Update preference
            settingsPreferences.setNotificationsEnabled(enabled)
            _notificationsEnabled.value = enabled

            if (enabled) {
                // Re-schedule all notifications
                notificationScheduler.rescheduleAllNotifications()
            } else {
                // Cancel all notifications
                cancelAllNotifications()
            }
        }
    }

    /**
     * Toggle reminder notification enabled/disabled
     */
    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setReminderEnabled(enabled)
            _reminderEnabled.value = enabled
        }
    }

    /**
     * Set reminder delay in minutes
     */
    fun setReminderDelayMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsPreferences.setReminderDelayMinutes(minutes)
            _reminderDelayMinutes.value = minutes
        }
    }

    /**
     * Toggle data retention enabled/disabled
     */
    fun setDataRetentionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsPreferences.setDataRetentionEnabled(enabled)
            _dataRetentionEnabled.value = enabled
        }
    }

    /**
     * Set data retention period in days
     */
    fun setDataRetentionDays(days: Int) {
        viewModelScope.launch {
            settingsPreferences.setDataRetentionDays(days)
            _dataRetentionDays.value = days
        }
    }

    /**
     * Cancel all scheduled notifications
     */
    private suspend fun cancelAllNotifications() {
        // Get all unique medication times and cancel their notifications
        val medications = repository.getAllMedicationsWithTimes()
        medications.collect { medList ->
            val allTimes = mutableSetOf<String>()
            medList.forEach { med ->
                med.times.forEach { time ->
                    allTimes.add(time.time)
                }
            }

            allTimes.forEach { time ->
                notificationScheduler.cancelNotificationForTime(time)
                reminderScheduler.cancelReminderNotification(time)
            }
        }

        // Also cancel the daily refresh job
        notificationScheduler.cancelDailyRefreshJob()
    }
}
