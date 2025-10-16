package net.shugo.medicineshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.viewmodel.DailyMedicationViewModel
import net.shugo.medicineshield.utils.formatDose
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMedicationScreen(
    viewModel: DailyMedicationViewModel,
    onNavigateToMedicationList: () -> Unit,
    onNavigateToSettings: () -> Unit
) {
    val dailyMedications by viewModel.dailyMedications.collectAsState()
    val displayDateText by viewModel.displayDateText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()
    val dailyNote by viewModel.dailyNote.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_medication_title)) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.settings)
                        )
                    }
                    IconButton(onClick = onNavigateToMedicationList) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = stringResource(R.string.medication_list)
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
        ) {
            // 日付ナビゲーションバー
            DateNavigationBar(
                displayDateText = displayDateText,
                onPreviousDay = { viewModel.onPreviousDay() },
                onNextDay = { viewModel.onNextDay() },
                onDateClick = { showDatePicker = true }
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                dailyMedications.isEmpty() -> {
                    EmptyMedicationState(selectedDate, onNavigateToMedicationList)
                }

                else -> {
                    MedicationList(
                        medications = dailyMedications,
                        onToggleTaken = { medicationId, sequenceNumber, isTaken ->
                            viewModel.toggleMedicationTaken(medicationId, sequenceNumber, isTaken)
                        },
                        onAddAsNeeded = { medicationId ->
                            viewModel.addAsNeededMedication(medicationId)
                        },
                        onRemoveAsNeeded = { medicationId, sequenceNumber ->
                            viewModel.removeAsNeededMedication(medicationId, sequenceNumber)
                        },
                        onUpdateTakenAt = { medicationId, sequenceNumber, hour, minute ->
                            viewModel.updateTakenAt(medicationId, sequenceNumber, hour, minute)
                        },
                        dailyNote = dailyNote,
                        onSaveNote = { content ->
                            viewModel.saveNote(content)
                        },
                        onDeleteNote = {
                            viewModel.deleteNote()
                        }
                    )
                }
            }
        }

        // DatePickerダイアログ
        if (showDatePicker) {
            DailyMedicationDatePickerDialog(
                selectedDate = selectedDate,
                onDateSelected = { year, month, dayOfMonth ->
                    viewModel.onDateSelected(year, month, dayOfMonth)
                    showDatePicker = false
                },
                onDismiss = { showDatePicker = false }
            )
        }
    }
}

@Composable
fun DateNavigationBar(
    displayDateText: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onDateClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 前日ボタン
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_day),
                    modifier = Modifier.size(32.dp)
                )
            }

            // 日付表示（タップ可能）
            Text(
                text = displayDateText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable(onClick = onDateClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // 翌日ボタン
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next_day),
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMedicationDatePickerDialog(
    selectedDate: Calendar,
    onDateSelected: (year: Int, month: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.timeInMillis
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    val calendar = Calendar.getInstance().apply {
                        timeInMillis = millis
                    }
                    onDateSelected(
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                    )
                }
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@Composable
fun EmptyMedicationState(selectedDate: Calendar, onNavigateToMedicationList: () -> Unit) {
    // 今日かどうか判定
    val today = Calendar.getInstance()
    val isToday = selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                  selectedDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val isFuture = selectedDate.timeInMillis > today.timeInMillis

    val message = when {
        isToday -> stringResource(R.string.no_medication_today)
        isFuture -> stringResource(R.string.no_medication_future)
        else -> stringResource(R.string.no_medication_past)
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = message,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onNavigateToMedicationList
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.add_medication_button))
            }
        }
    }
}

@Composable
fun MedicationList(
    medications: List<DailyMedicationItem>,
    onToggleTaken: (Long, Int, Boolean) -> Unit,
    onAddAsNeeded: (Long) -> Unit,
    onRemoveAsNeeded: (Long, Int) -> Unit,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit,
    dailyNote: net.shugo.medicineshield.data.model.DailyNote?,
    onSaveNote: (String) -> Unit,
    onDeleteNote: () -> Unit
) {
    // 頓服薬と定時薬を分離
    val (asNeededMeds, scheduledMeds) = medications.partition { it.isAsNeeded }

    // 定時薬を時刻でグループ化
    val groupedScheduledMeds = scheduledMeds.groupBy { it.scheduledTime }

    // 頓服薬を薬ごとにグループ化
    val groupedAsNeededMeds = asNeededMeds.groupBy { it.medicationId }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // 定時薬のセクション
        groupedScheduledMeds.forEach { (time, items) ->
            item {
                TimeHeader(time)
            }

            items(items) { medication ->
                ScheduledMedicationItem(
                    medication = medication,
                    onToggleTaken = onToggleTaken,
                    onUpdateTakenAt = onUpdateTakenAt
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // 頓服薬のセクション
        if (groupedAsNeededMeds.isNotEmpty()) {
            item {
                TimeHeader(stringResource(R.string.as_needed_medication))
            }

            groupedAsNeededMeds.forEach { (_, items) ->
                items(items) { medication ->
                    AsNeededMedicationItem(
                        medication = medication,
                        onAddIntake = onAddAsNeeded,
                        onRemoveIntake = onRemoveAsNeeded,
                        onUpdateTakenAt = onUpdateTakenAt
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // メモセクション
        item {
            TimeHeader(stringResource(R.string.note_section_title))
        }

        item {
            DailyNoteSection(
                note = dailyNote,
                onSave = onSaveNote,
                onDelete = onDeleteNote
            )
        }
    }
}

@Composable
fun TimeHeader(time: String) {
    Text(
        text = time,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

/**
 * Base medication card that displays medication information with customizable action button
 */
@Composable
private fun BaseMedicationCard(
    medication: DailyMedicationItem,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit,
    actionButton: @Composable () -> Unit
) {
    var showTimePickerDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (medication.isTaken) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = medication.medicationName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDose(medication.dose, medication.doseUnit),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (medication.isTaken && medication.takenAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = formatTakenTime(medication.takenAt),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { showTimePickerDialog = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(R.string.edit_taken_time),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            actionButton()
        }
    }

    if (showTimePickerDialog && medication.takenAt != null) {
        TimePickerDialog(
            initialTimestamp = medication.takenAt,
            onConfirm = { hour, minute ->
                onUpdateTakenAt(medication.medicationId, medication.sequenceNumber, hour, minute)
                showTimePickerDialog = false
            },
            onDismiss = { showTimePickerDialog = false }
        )
    }
}

@Composable
fun ScheduledMedicationItem(
    medication: DailyMedicationItem,
    onToggleTaken: (Long, Int, Boolean) -> Unit,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit
) {
    BaseMedicationCard(
        medication = medication,
        onUpdateTakenAt = onUpdateTakenAt,
        actionButton = {
            Checkbox(
                checked = medication.isTaken,
                onCheckedChange = {
                    onToggleTaken(
                        medication.medicationId,
                        medication.sequenceNumber,
                        medication.isTaken
                    )
                }
            )
        }
    )
}

@Composable
fun AsNeededMedicationItem(
    medication: DailyMedicationItem,
    onAddIntake: (Long) -> Unit,
    onRemoveIntake: (Long, Int) -> Unit,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit
) {
    BaseMedicationCard(
        medication = medication,
        onUpdateTakenAt = onUpdateTakenAt,
        actionButton = {
            if (medication.isTaken) {
                // 服用済みの場合は削除ボタン
                IconButton(
                    onClick = {
                        onRemoveIntake(medication.medicationId, medication.sequenceNumber)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.remove_intake),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                // 未服用の場合はチェックボックス
                Checkbox(
                    checked = false,
                    onCheckedChange = {
                        onAddIntake(medication.medicationId)
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTimestamp: Long,
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = initialTimestamp

    val timePickerState = rememberTimePickerState(
        initialHour = calendar.get(Calendar.HOUR_OF_DAY),
        initialMinute = calendar.get(Calendar.MINUTE),
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

@Composable
fun formatTakenTime(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return stringResource(
        R.string.taken_at,
        calendar.get(Calendar.HOUR_OF_DAY),
        calendar.get(Calendar.MINUTE)
    )
}

// ========== Daily Note Components ==========

@Composable
fun NoteEditDialog(
    initialContent: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var noteContent by remember { mutableStateOf(initialContent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialContent.isEmpty()) {
                    stringResource(R.string.add_note)
                } else {
                    stringResource(R.string.edit_note)
                }
            )
        },
        text = {
            OutlinedTextField(
                value = noteContent,
                onValueChange = { noteContent = it },
                label = { Text(stringResource(R.string.note_content)) },
                placeholder = { Text(stringResource(R.string.note_content_hint)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                maxLines = 10,
                singleLine = false
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (noteContent.isNotBlank()) {
                        onSave(noteContent)
                        onDismiss()
                    }
                }
            ) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
fun NoteCard(
    content: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = content,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )

            Row {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = stringResource(R.string.edit_note),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete_note),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.delete_note)) },
            text = { Text(stringResource(R.string.confirm_delete_note)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete()
                        showDeleteConfirmation = false
                    }
                ) {
                    Text(stringResource(R.string.delete_note))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}

@Composable
fun DailyNoteSection(
    note: net.shugo.medicineshield.data.model.DailyNote?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showNoteDialog by remember { mutableStateOf(false) }

    if (note != null) {
        NoteCard(
            content = note.content,
            onEdit = { showNoteDialog = true },
            onDelete = onDelete
        )
    } else {
        OutlinedButton(
            onClick = { showNoteDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.add_note))
        }
    }

    if (showNoteDialog) {
        NoteEditDialog(
            initialContent = note?.content ?: "",
            onSave = onSave,
            onDismiss = { showNoteDialog = false }
        )
    }
}
