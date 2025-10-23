package net.shugo.medicineshield.ui.screen

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.unit.dp
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.utils.DateUtils
import net.shugo.medicineshield.utils.formatDose
import net.shugo.medicineshield.viewmodel.MedicationListViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    viewModel: MedicationListViewModel,
    onNavigateBack: () -> Unit,
    onAddMedication: () -> Unit,
    onEditMedication: (Long) -> Unit
) {
    val currentMedications by viewModel.currentMedications.collectAsState()
    val pastMedications by viewModel.pastMedications.collectAsState()
    var medicationToDelete by remember { mutableStateOf<Long?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.medication_list_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMedication) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_medication))
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            SecondaryTabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text(stringResource(R.string.current_medications)) }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text(stringResource(R.string.past_medications)) }
                )
            }

            val medications = if (selectedTabIndex == 0) currentMedications else pastMedications
            val emptyMessage = if (selectedTabIndex == 0) {
                stringResource(R.string.no_medications)
            } else {
                stringResource(R.string.no_past_medications)
            }

            if (medications.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        emptyMessage,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(medications, key = { it.medication.id }) { medicationWithTimes ->
                        MedicationCard(
                            medicationWithTimes = medicationWithTimes,
                            onEdit = { onEditMedication(medicationWithTimes.medication.id) },
                            onDelete = { medicationToDelete = medicationWithTimes.medication.id }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    medicationToDelete?.let { medicationId ->
        AlertDialog(
            onDismissRequest = { medicationToDelete = null },
            title = { Text(stringResource(R.string.delete_confirmation_title)) },
            text = { Text(stringResource(R.string.delete_confirmation_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(medicationId)
                        medicationToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationToDelete = null }) {
                    Text(stringResource(R.string.no))
                }
            }
        )
    }
}

@Composable
fun MedicationCard(
    medicationWithTimes: MedicationWithTimes,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val medication = medicationWithTimes.medication
    val times = medicationWithTimes.times
    val doseFormat = stringResource(R.string.dose_format)
    val timeParser = SimpleDateFormat("HH:mm", Locale.ROOT)
    val context = LocalContext.current
    // Use remember without a key to automatically reflect current system settings
    val timeFormatter = remember {
        DateFormat.getTimeFormat(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    medication.name,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                Row {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 現在有効なConfigを取得
            val currentConfig = medicationWithTimes.config

            currentConfig?.let { config ->
                // 服用時間（頓服の場合は "As Needed x dose"、定時の場合は時刻リスト）
                val timesText = if (config.isAsNeeded) {
                    "${stringResource(R.string.as_needed_medication)} ${formatDose(doseFormat, config.dose, config.doseUnit)}"
                } else {
                    times.joinToString(", ") {
                        val time = timeParser.parse(it.time)
                        val formattedTime = time?.let {
                            timeFormatter.format(it)
                        } ?: "00:00"
                        "$formattedTime ${formatDose(doseFormat, it.dose, config.doseUnit)}"
                    }
                }
                Text(
                    stringResource(R.string.medication_times_label, timesText),
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(4.dp))

                // サイクル（頓服の場合は非表示）
                if (!config.isAsNeeded) {
                    val cycleText = when (config.cycleType) {
                        CycleType.DAILY -> stringResource(R.string.cycle_daily_full)
                        CycleType.WEEKLY -> {
                            val days = config.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                            val dayNames = days.map { getDayName(it) }
                            val separator = stringResource(R.string.day_name_separator)
                            "${stringResource(R.string.cycle_weekly_prefix)} ${dayNames.joinToString(separator)}"
                        }
                        CycleType.INTERVAL -> stringResource(R.string.cycle_interval_format, config.cycleValue ?: "")
                    }
                    Text(
                        stringResource(R.string.cycle_label, cycleText),
                        style = MaterialTheme.typography.bodyMedium
                    )

                    Spacer(Modifier.height(4.dp))
                }

                // 期間
                val locale = Locale.getDefault()
                val pattern = DateFormat.getBestDateTimePattern(locale, "yMd")
                val dateFormat = SimpleDateFormat(pattern, locale)
                val startDate = DateUtils.parseIsoDate(config.medicationStartDate)
                val startDateStr = startDate?.let {
                    dateFormat.format(it.time)
                } ?: "????-??-??"
                val endDateStr = if (config.medicationEndDate == DateUtils.MAX_DATE) {
                    null
                } else {
                    val endDate = DateUtils.parseIsoDate(config.medicationEndDate)
                    endDate?.let { dateFormat.format(it.time) }
                }
                val periodText = if (endDateStr != null) {
                    stringResource(R.string.period_with_end, startDateStr, endDateStr)
                } else {
                    stringResource(R.string.period_no_end, startDateStr)
                }
                Text(
                    stringResource(R.string.period_label, periodText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun getDayName(dayIndex: Int): String {
    return when (dayIndex) {
        0 -> stringResource(R.string.sunday_short)
        1 -> stringResource(R.string.monday_short)
        2 -> stringResource(R.string.tuesday_short)
        3 -> stringResource(R.string.wednesday_short)
        4 -> stringResource(R.string.thursday_short)
        5 -> stringResource(R.string.friday_short)
        6 -> stringResource(R.string.saturday_short)
        else -> ""
    }
}
