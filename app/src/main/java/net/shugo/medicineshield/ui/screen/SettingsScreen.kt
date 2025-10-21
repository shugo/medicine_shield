package net.shugo.medicineshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.preferences.SettingsPreferences
import net.shugo.medicineshield.viewmodel.SettingsViewModel
import kotlin.system.exitProcess

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val notificationsEnabled by viewModel.notificationsEnabled.collectAsState()
    val currentLanguage by viewModel.currentLanguage.collectAsState()
    val context = LocalContext.current

    var showLanguageDropdown by remember { mutableStateOf(false) }
    var showRestartDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Notification Settings Section
            Text(
                text = stringResource(R.string.notification_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.enable_notifications),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.notification_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = { enabled ->
                            viewModel.setNotificationsEnabled(enabled)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Language Settings Section
            Text(
                text = stringResource(R.string.language_settings),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showLanguageDropdown = true }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.language),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = getLanguageDisplayName(currentLanguage),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    DropdownMenu(
                        expanded = showLanguageDropdown,
                        onDismissRequest = { showLanguageDropdown = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_system)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_SYSTEM) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_SYSTEM)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_english)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_ENGLISH) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_ENGLISH)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_japanese)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_JAPANESE) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_JAPANESE)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_chinese_simplified)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_CHINESE_SIMPLIFIED) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_CHINESE_SIMPLIFIED)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_chinese_traditional)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_CHINESE_TRADITIONAL) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_CHINESE_TRADITIONAL)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.language_korean)) },
                            onClick = {
                                if (currentLanguage != SettingsPreferences.LANGUAGE_KOREAN) {
                                    viewModel.setLanguage(SettingsPreferences.LANGUAGE_KOREAN)
                                    showRestartDialog = true
                                }
                                showLanguageDropdown = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Information Section
            Text(
                text = stringResource(R.string.app_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // Version
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.version),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = viewModel.appVersion,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // License Information Section
            Text(
                text = stringResource(R.string.license_info),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    // App License with clickable MIT-0 link
                    val licenseText = stringResource(R.string.app_license)
                    val licenseUrl = stringResource(R.string.mit0_license_url)
                    val mit0Tag = "MIT-0"

                    val annotatedString = buildAnnotatedString {
                        val startIndex = licenseText.indexOf(mit0Tag)
                        if (startIndex >= 0) {
                            append(licenseText.substring(0, startIndex))
                            withLink(LinkAnnotation.Url(licenseUrl)) {
                                withStyle(
                                    style = SpanStyle(
                                        color = MaterialTheme.colorScheme.primary,
                                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                    )
                                ) {
                                    append(mit0Tag)
                                }
                            }
                            append(licenseText.substring(startIndex + mit0Tag.length))
                        } else {
                            append(licenseText)
                        }
                    }

                    BasicText(
                        text = annotatedString,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Open Source Licenses
                    Text(
                        text = stringResource(R.string.open_source_licenses),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.license_details),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    // Restart dialog
    if (showRestartDialog) {
        AlertDialog(
            onDismissRequest = { showRestartDialog = false },
            title = { Text(stringResource(R.string.restart_required)) },
            text = { Text(stringResource(R.string.restart_required_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Force app restart
                        android.os.Process.killProcess(android.os.Process.myPid())
                        exitProcess(0)
                    }
                ) {
                    Text(stringResource(R.string.restart_now))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showRestartDialog = false }
                ) {
                    Text(stringResource(R.string.restart_later))
                }
            }
        )
    }
}

/**
 * Get the display name for a language code
 */
@Composable
private fun getLanguageDisplayName(languageCode: String): String {
    return when (languageCode) {
        SettingsPreferences.LANGUAGE_SYSTEM -> stringResource(R.string.language_system)
        SettingsPreferences.LANGUAGE_ENGLISH -> stringResource(R.string.language_english)
        SettingsPreferences.LANGUAGE_JAPANESE -> stringResource(R.string.language_japanese)
        SettingsPreferences.LANGUAGE_CHINESE_SIMPLIFIED -> stringResource(R.string.language_chinese_simplified)
        SettingsPreferences.LANGUAGE_CHINESE_TRADITIONAL -> stringResource(R.string.language_chinese_traditional)
        SettingsPreferences.LANGUAGE_KOREAN -> stringResource(R.string.language_korean)
        else -> stringResource(R.string.language_system)
    }
}
