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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.data.model.DailyNote
import net.shugo.medicineshield.data.model.MedicationIntakeStatus
import net.shugo.medicineshield.utils.formatDose
import net.shugo.medicineshield.viewmodel.DailyMedicationViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

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
    val scrollToNote by viewModel.scrollToNote.collectAsState()

    // Detect Configuration changes (language change, etc.) and update display
    val configuration = LocalConfiguration.current
    LaunchedEffect(configuration.locales) {
        viewModel.refreshData()
    }

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.daily_medication_title)) },
                actions = {
                    IconButton(onClick = {
                        val today = Calendar.getInstance()
                        viewModel.onDateSelected(
                            today.get(Calendar.YEAR),
                            today.get(Calendar.MONTH),
                            today.get(Calendar.DAY_OF_MONTH)
                        )
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Today,
                            contentDescription = stringResource(R.string.today)
                        )
                    }
                    IconButton(onClick = onNavigateToMedicationList) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = stringResource(R.string.medication_list)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.settings)
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
            // Date navigation bar
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

                else -> {
                    MedicationList(
                        selectedDate = selectedDate,
                        medications = dailyMedications,
                        onToggleTaken = { medicationId, sequenceNumber, isTaken ->
                            viewModel.toggleMedicationTaken(medicationId, sequenceNumber, isTaken)
                        },
                        onCancelMedication = { medicationId, sequenceNumber ->
                            viewModel.cancelMedication(medicationId, sequenceNumber)
                        },
                        onUncancelMedication = { medicationId, sequenceNumber ->
                            viewModel.uncancelMedication(medicationId, sequenceNumber)
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
                        },
                        viewModel = viewModel,
                        scrollToNote = scrollToNote
                    )
                }
            }
        }

        // DatePicker dialog
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
            // Previous day button
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.previous_day),
                    modifier = Modifier.size(32.dp)
                )
            }

            // Date display (tappable)
            Row(
                modifier = Modifier.clickable(onClick = onDateClick)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = displayDateText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.CalendarMonth,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Next day button
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
fun MedicationList(
    selectedDate: Calendar,
    medications: List<DailyMedicationItem>,
    onToggleTaken: (Long, Int, Boolean) -> Unit,
    onCancelMedication: (Long, Int) -> Unit,
    onUncancelMedication: (Long, Int) -> Unit,
    onAddAsNeeded: (Long) -> Unit,
    onRemoveAsNeeded: (Long, Int) -> Unit,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit,
    dailyNote: DailyNote?,
    onSaveNote: (String) -> Unit,
    onDeleteNote: () -> Unit,
    viewModel: DailyMedicationViewModel,
    scrollToNote: Boolean = false
) {
    // Separate PRN and scheduled medications
    val (asNeededMeds, scheduledMeds) = medications.partition { it.isAsNeeded }

    // Group scheduled medications by time
    val groupedScheduledMeds = scheduledMeds.groupBy { it.scheduledTime }

    // Group PRN medications by drug
    val groupedAsNeededMeds = asNeededMeds.groupBy { it.medicationId }

    // Determine if today
    val today = Calendar.getInstance()
    val isToday = selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                  selectedDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val isFuture = selectedDate.timeInMillis > today.timeInMillis

    // Create LazyListState
    val listState = rememberLazyListState()

    val timeParser = SimpleDateFormat("HH:mm", Locale.ROOT)
    val context = LocalContext.current
    // Use remember without a key to automatically reflect current system settings
    val timeFormatter = remember {
        DateFormat.getTimeFormat(context)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        state = listState
    ) {
        // Scheduled medication section
        groupedScheduledMeds.forEach { (time, items) ->
            val date = timeParser.parse(time)
            val timeText = date?.let { timeFormatter.format(it) } ?: "??:??"

            item {
                TimeHeader(timeText)
            }

            items(items) { medication ->
                ScheduledMedicationItem(
                    medication = medication,
                    onToggleTaken = onToggleTaken,
                    onCancelMedication = onCancelMedication,
                    onUncancelMedication = onUncancelMedication,
                    onUpdateTakenAt = onUpdateTakenAt
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        // PRN medication section
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

        // Message when no medication is scheduled
        if (medications.isEmpty()) {
            item {
                NoMedicationMessage(isToday = isToday, isFuture = isFuture)
            }
        }

        // Note section
        item {
            TimeHeader(stringResource(R.string.note_section_title))
        }

        item {
            DailyNoteSection(
                note = dailyNote,
                onSave = onSaveNote,
                onDelete = onDeleteNote,
                viewModel = viewModel,
                selectedDate = selectedDate,
            )
        }
    }

    val listEndIndex = listState.layoutInfo.totalItemsCount - 1

    // Scroll trigger
    LaunchedEffect(scrollToNote, listEndIndex) {
        if (scrollToNote) {
            listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            viewModel.resetScrollToNote()
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

@Composable
fun NoMedicationMessage(isToday: Boolean, isFuture: Boolean) {
    val message = when {
        isToday -> stringResource(R.string.no_medication_today)
        isFuture -> stringResource(R.string.no_medication_future)
        else -> stringResource(R.string.no_medication_past)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = message,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
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
    val doseFormat = stringResource(R.string.dose_format)
    val context = LocalContext.current
    // Use remember without a key to automatically reflect current system settings
    val timeFormatter = remember {
        DateFormat.getTimeFormat(context)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (medication.status) {
                MedicationIntakeStatus.CANCELED -> MaterialTheme.colorScheme.errorContainer
                MedicationIntakeStatus.TAKEN -> MaterialTheme.colorScheme.surfaceVariant
                MedicationIntakeStatus.UNCHECKED -> MaterialTheme.colorScheme.surface
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
                        fontWeight = FontWeight.Medium,
                        textDecoration = if (medication.status == MedicationIntakeStatus.CANCELED) TextDecoration.LineThrough else null,
                        color = if (medication.status == MedicationIntakeStatus.CANCELED) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatDose(doseFormat, medication.dose, medication.doseUnit),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (medication.status == MedicationIntakeStatus.CANCELED) TextDecoration.LineThrough else null
                    )
                }
                if (medication.status == MedicationIntakeStatus.CANCELED) {
                    Text(
                        text = stringResource(R.string.medication_canceled),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                } else if (medication.status == MedicationIntakeStatus.TAKEN && medication.takenAt != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.taken_at, medication.takenAt),
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
            initialTime = medication.takenAt,
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
    onCancelMedication: (Long, Int) -> Unit,
    onUncancelMedication: (Long, Int) -> Unit,
    onUpdateTakenAt: (Long, Int, Int, Int) -> Unit
) {
    // Determine checkbox state
    val checkboxState = when (medication.status) {
        MedicationIntakeStatus.UNCHECKED -> ToggleableState.Off        // Not taken
        MedicationIntakeStatus.TAKEN -> ToggleableState.On             // Taken
        MedicationIntakeStatus.CANCELED -> ToggleableState.Indeterminate  // Canceled
    }

    BaseMedicationCard(
        medication = medication,
        onUpdateTakenAt = onUpdateTakenAt,
        actionButton = {
            TriStateCheckbox(
                state = checkboxState,
                onClick = {
                    when (medication.status) {
                        MedicationIntakeStatus.UNCHECKED -> {
                            // Not taken → Taken
                            onToggleTaken(
                                medication.medicationId,
                                medication.sequenceNumber,
                                false  // Currently not taken
                            )
                        }
                        MedicationIntakeStatus.TAKEN -> {
                            // Taken → Canceled
                            onCancelMedication(
                                medication.medicationId,
                                medication.sequenceNumber
                            )
                        }
                        MedicationIntakeStatus.CANCELED -> {
                            // Canceled → Not taken
                            onUncancelMedication(
                                medication.medicationId,
                                medication.sequenceNumber
                            )
                        }
                    }
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
            if (medication.status == MedicationIntakeStatus.TAKEN) {
                // Delete button if taken
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
                // Checkbox if not taken
                IconButton(
                    onClick = {
                        onAddIntake(medication.medicationId)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = stringResource(R.string.add_as_needed_intake),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: String,  // HH:mm format
    onConfirm: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Parse HH:mm format string
    val timeParts = initialTime.split(":")
    val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 0
    val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute
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
                placeholder = { Text("") },
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
    note: DailyNote?,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    viewModel: DailyMedicationViewModel,
    selectedDate: Calendar
) {
    var showNoteDialog by remember { mutableStateOf(false) }
    var hasPrevious by remember { mutableStateOf(false) }
    var hasNext by remember { mutableStateOf(false) }

    // Check if notes exist before/after (also monitor selectedDate)
    LaunchedEffect(selectedDate) {
        hasPrevious = viewModel.hasPreviousNote()
        hasNext = viewModel.hasNextNote()
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        // Display note or add button
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

        // Navigation buttons (show only if notes exist before/after)
        if (hasPrevious || hasNext) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Go to previous note
                if (hasPrevious) {
                    OutlinedButton(
                        onClick = { viewModel.navigateToPreviousNote() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.previous_note))
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Go to next note
                if (hasNext) {
                    OutlinedButton(
                        onClick = { viewModel.navigateToNextNote() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.next_note))
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
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
