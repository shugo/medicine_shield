package net.shugo.medicineshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.viewmodel.MedicationFormViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationFormScreen(
    viewModel: MedicationFormViewModel,
    isEdit: Boolean = false,
    onNavigateBack: () -> Unit
) {
    val formState by viewModel.formState.collectAsState()
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTimeIndex by remember { mutableStateOf<Int?>(null) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "お薬を編集" else "お薬を追加") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 薬の名前
            item {
                OutlinedTextField(
                    value = formState.name,
                    onValueChange = { viewModel.updateName(it) },
                    label = { Text("お薬の名前") },
                    isError = formState.nameError != null,
                    supportingText = formState.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 服用時間
            item {
                Text("服用時間", style = MaterialTheme.typography.titleMedium)
                if (formState.timesError != null) {
                    Text(
                        formState.timesError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            itemsIndexed(formState.times) { index, time ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            editingTimeIndex = index
                            showTimePicker = true
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        time,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .weight(1f)
                    )
                    IconButton(onClick = { viewModel.removeTime(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = {
                        editingTimeIndex = null
                        showTimePicker = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("時間を追加")
                }
            }

            // 服用サイクル
            item {
                Text("服用サイクル", style = MaterialTheme.typography.titleMedium)
                Column {
                    CycleType.entries.forEach { type ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.updateCycleType(type) }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = formState.cycleType == type,
                                onClick = { viewModel.updateCycleType(type) }
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                when (type) {
                                    CycleType.DAILY -> "毎日"
                                    CycleType.WEEKLY -> "毎週"
                                    CycleType.INTERVAL -> "N日ごと"
                                }
                            )
                        }
                    }
                }
                if (formState.cycleError != null) {
                    Text(
                        formState.cycleError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // サイクル詳細設定
            item {
                when (formState.cycleType) {
                    CycleType.WEEKLY -> {
                        WeekdaySelector(
                            selectedDays = formState.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet(),
                            onDaysChanged = { days ->
                                viewModel.updateCycleValue(days.sorted().joinToString(","))
                            }
                        )
                    }
                    CycleType.INTERVAL -> {
                        OutlinedTextField(
                            value = formState.cycleValue ?: "",
                            onValueChange = { viewModel.updateCycleValue(it) },
                            label = { Text("日数") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    CycleType.DAILY -> {
                        // No additional settings
                    }
                }
            }

            // 開始日
            item {
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("開始日: ${formatDate(formState.startDate)}")
                }
            }

            // 終了日
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            formState.endDate?.let { "終了日: ${formatDate(it)}" } ?: "終了日: なし"
                        )
                    }
                    if (formState.endDate != null) {
                        IconButton(onClick = { viewModel.updateEndDate(null) }) {
                            Icon(Icons.Default.Delete, contentDescription = "終了日をクリア")
                        }
                    }
                }
                if (formState.dateError != null) {
                    Text(
                        formState.dateError!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // 保存ボタン
            item {
                Button(
                    onClick = {
                        viewModel.saveMedication(onSuccess = onNavigateBack)
                    },
                    enabled = !formState.isSaving,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("保存")
                }
            }
        }
    }

    // Time Picker Dialog
    if (showTimePicker) {
        val currentEditingTime = editingTimeIndex?.let { formState.times.getOrNull(it) }
        TimePickerDialog(
            initialTime = currentEditingTime,
            onDismiss = {
                showTimePicker = false
                editingTimeIndex = null
            },
            onConfirm = { hour, minute ->
                val timeString = String.format("%02d:%02d", hour, minute)
                if (editingTimeIndex != null) {
                    viewModel.updateTime(editingTimeIndex!!, timeString)
                } else {
                    viewModel.addTime(timeString)
                }
                showTimePicker = false
                editingTimeIndex = null
            }
        )
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        DatePickerDialog(
            initialDate = formState.startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { date ->
                viewModel.updateStartDate(date)
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        DatePickerDialog(
            initialDate = formState.endDate ?: System.currentTimeMillis(),
            onDismiss = { showEndDatePicker = false },
            onConfirm = { date ->
                viewModel.updateEndDate(date)
                showEndDatePicker = false
            }
        )
    }
}

@Composable
fun WeekdaySelector(
    selectedDays: Set<Int>,
    onDaysChanged: (Set<Int>) -> Unit
) {
    val weekdays = listOf(
        0 to "日",
        1 to "月",
        2 to "火",
        3 to "水",
        4 to "木",
        5 to "金",
        6 to "土"
    )

    Column {
        Text("曜日を選択", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            weekdays.forEach { (dayIndex, dayName) ->
                FilterChip(
                    selected = selectedDays.contains(dayIndex),
                    onClick = {
                        val newDays = selectedDays.toMutableSet()
                        if (dayIndex in newDays) {
                            newDays.remove(dayIndex)
                        } else {
                            newDays.add(dayIndex)
                        }
                        onDaysChanged(newDays)
                    },
                    label = { Text(dayName) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialTime: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit
) {
    val (initialHour, initialMinute) = initialTime?.let {
        val parts = it.split(":")
        if (parts.size == 2) {
            parts[0].toIntOrNull() to parts[1].toIntOrNull()
        } else {
            null to null
        }
    } ?: (0 to 0)

    val timePickerState = rememberTimePickerState(
        initialHour = initialHour ?: 0,
        initialMinute = initialMinute ?: 0,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                onConfirm(timePickerState.hour, timePickerState.minute)
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        },
        text = {
            TimePicker(state = timePickerState)
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
    )

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                datePickerState.selectedDateMillis?.let { millis ->
                    // 選択された日付を00:00:00に正規化
                    val selectedCalendar = Calendar.getInstance().apply {
                        timeInMillis = millis
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }
                    onConfirm(selectedCalendar.timeInMillis)
                }
            }) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("キャンセル")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
