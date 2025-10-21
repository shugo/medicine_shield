package net.shugo.medicineshield.ui.screen

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.shugo.medicineshield.R
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationConfig
import net.shugo.medicineshield.utils.formatDose
import net.shugo.medicineshield.utils.formatDoseInput
import net.shugo.medicineshield.utils.parseDoseInput
import net.shugo.medicineshield.viewmodel.MedicationFormViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val doseFormat = stringResource(R.string.dose_format)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (isEdit) stringResource(R.string.edit_medication_title)
                        else stringResource(R.string.add_medication_title)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
                    label = { Text(stringResource(R.string.medication_name)) },
                    isError = formState.nameError != null,
                    supportingText = formState.nameError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // 頓服チェックボックス
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.updateIsAsNeeded(!formState.isAsNeeded) }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = formState.isAsNeeded,
                        onCheckedChange = { viewModel.updateIsAsNeeded(it) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.as_needed_checkbox),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = stringResource(R.string.as_needed_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // デフォルト服用量
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = if (formState.doseError == null) Alignment.CenterVertically else Alignment.Top
                ) {
                    OutlinedTextField(
                        value = formState.defaultDoseText,
                        onValueChange = { newValue ->
                            viewModel.updateDefaultDose(newValue)
                        },
                        label = { Text(stringResource(R.string.dose)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                        isError = formState.doseError != null,
                        supportingText = formState.doseError?.let { { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )

                    DoseUnitSelector(
                        selectedUnit = formState.doseUnit,
                        onUnitSelected = { viewModel.updateDoseUnit(it) },
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val currentDose = parseDoseInput(formState.defaultDoseText) ?: 1.0
                                val newDose = currentDose + 1.0
                                if (newDose <= MedicationConfig.MAX_DOSE) {
                                    viewModel.updateDefaultDose(formatDoseInput(newDose))
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase dose")
                        }

                        IconButton(
                            onClick = {
                                val currentDose = parseDoseInput(formState.defaultDoseText) ?: 1.0
                                val newDose = currentDose - 1.0
                                if (newDose >= MedicationConfig.MIN_DOSE) {
                                    viewModel.updateDefaultDose(formatDoseInput(newDose))
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease dose")
                        }
                    }
                }
            }

            // 服用時間（頓服の場合は非表示）
            if (!formState.isAsNeeded) {
                item {
                    Text(stringResource(R.string.medication_times), style = MaterialTheme.typography.titleMedium)
                    if (formState.timesError != null) {
                        Text(
                            formState.timesError!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                itemsIndexed(formState.times) { index, timeWithSeq ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${timeWithSeq.time} ${formatDose(doseFormat, timeWithSeq.dose, formState.doseUnit)}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .padding(vertical = 12.dp)
                            .weight(1f)
                    )
                    IconButton(onClick = {
                        editingTimeIndex = index
                        showTimePicker = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit))
                    }
                    IconButton(onClick = { viewModel.removeTime(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
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
                        Text(stringResource(R.string.add_time))
                    }
                }
            }

            // 服用サイクル（頓服の場合は非表示）
            if (!formState.isAsNeeded) {
                item {
                Text(stringResource(R.string.cycle_type), style = MaterialTheme.typography.titleMedium)
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
                                    CycleType.DAILY -> stringResource(R.string.cycle_daily)
                                    CycleType.WEEKLY -> stringResource(R.string.cycle_weekly)
                                    CycleType.INTERVAL -> stringResource(R.string.cycle_interval)
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
                                label = { Text(stringResource(R.string.interval_days)) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        CycleType.DAILY -> {
                            // No additional settings
                        }
                    }
                }
            }

            // 開始日
            item {
                OutlinedButton(
                    onClick = { showStartDatePicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.start_date_label, formatDate(formState.startDate)))
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
                            formState.endDate?.let {
                                stringResource(R.string.end_date_label, formatDate(it))
                            } ?: stringResource(R.string.end_date_none)
                        )
                    }
                    if (formState.endDate != null) {
                        IconButton(onClick = { viewModel.updateEndDate(null) }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_end_date))
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

            // バリデーションエラーメッセージ
            item {
                val hasErrors = formState.nameError != null ||
                        formState.timesError != null ||
                        formState.cycleError != null ||
                        formState.dateError != null ||
                        formState.doseError != null

                if (hasErrors) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            text = stringResource(R.string.error_form_has_errors),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
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
                    Text(stringResource(R.string.save))
                }
            }
        }
    }

    // Time and Dose Picker Dialog
    if (showTimePicker) {
        val currentEditingTimeWithSeq = editingTimeIndex?.let { formState.times.getOrNull(it) }
        // 新規追加時のデフォルト値：formState.defaultDoseText、編集時は現在のdose
        val defaultDose = currentEditingTimeWithSeq?.dose ?: parseDoseInput(formState.defaultDoseText) ?: 1.0
        TimeAndDosePickerDialog(
            initialTime = currentEditingTimeWithSeq?.time,
            initialDose = defaultDose,
            onDismiss = {
                showTimePicker = false
                editingTimeIndex = null
            },
            onConfirm = { hour, minute, dose ->
                val timeString = String.format("%02d:%02d", hour, minute)
                if (editingTimeIndex != null) {
                    viewModel.updateTime(editingTimeIndex!!, timeString, dose)
                } else {
                    viewModel.addTime(timeString, dose)
                }
                showTimePicker = false
                editingTimeIndex = null
            }
        )
    }

    // Date Picker Dialogs
    if (showStartDatePicker) {
        MedicationFormDatePickerDialog(
            initialDate = formState.startDate,
            onDismiss = { showStartDatePicker = false },
            onConfirm = { date ->
                viewModel.updateStartDate(date)
                showStartDatePicker = false
            }
        )
    }

    if (showEndDatePicker) {
        MedicationFormDatePickerDialog(
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
        0 to stringResource(R.string.sunday_short),
        1 to stringResource(R.string.monday_short),
        2 to stringResource(R.string.tuesday_short),
        3 to stringResource(R.string.wednesday_short),
        4 to stringResource(R.string.thursday_short),
        5 to stringResource(R.string.friday_short),
        6 to stringResource(R.string.saturday_short)
    )

    Column {
        Text(stringResource(R.string.select_days), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    label = { Text(dayName) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeAndDosePickerDialog(
    initialTime: String? = null,
    initialDose: Double = 1.0,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int, dose: Double) -> Unit
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

    var doseText by remember { mutableStateOf(formatDoseInput(initialDose)) }
    var doseError by remember { mutableStateOf<String?>(null) }
    val doseErrorText = stringResource(R.string.error_invalid_dose, MedicationConfig.MIN_DOSE, MedicationConfig.MAX_DOSE)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val dose = parseDoseInput(doseText)
                if (dose != null && dose in MedicationConfig.MIN_DOSE..MedicationConfig.MAX_DOSE) {
                    onConfirm(timePickerState.hour, timePickerState.minute, dose)
                } else {
                    doseError = doseErrorText
                }
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
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TimePicker(state = timePickerState)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = doseText,
                        onValueChange = {
                            doseText = it
                            doseError = null
                        },
                        label = { Text(stringResource(R.string.dose)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.End),
                        isError = doseError != null,
                        supportingText = doseError?.let { { Text(it) } },
                        modifier = Modifier.weight(1f)
                    )

                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = {
                                val currentDose = parseDoseInput(doseText) ?: 1.0
                                val newDose = currentDose + 1.0
                                if (newDose <= MedicationConfig.MAX_DOSE) {
                                    doseText = formatDoseInput(newDose)
                                    doseError = null
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase dose")
                        }

                        IconButton(
                            onClick = {
                                val currentDose = parseDoseInput(doseText) ?: 1.0
                                val newDose = currentDose - 1.0
                                if (newDose >= MedicationConfig.MIN_DOSE) {
                                    doseText = formatDoseInput(newDose)
                                    doseError = null
                                }
                            }
                        ) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease dose")
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationFormDatePickerDialog(
    initialDate: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate
    )

    DatePickerDialog(
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
private fun formatDate(timestamp: Long): String {
    val locale = Locale.getDefault()
    val pattern = DateFormat.getBestDateTimePattern(locale, "yMd")
    val sdf = SimpleDateFormat(pattern, locale)
    return sdf.format(Date(timestamp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DoseUnitSelector(
    selectedUnit: String?,
    onUnitSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    // 一般的な単位の選択肢
    val unitOptions = listOf(
        stringResource(R.string.dose_unit_tab),
        stringResource(R.string.dose_unit_cap),
        stringResource(R.string.dose_unit_pack),
        stringResource(R.string.dose_unit_ml),
        stringResource(R.string.dose_unit_mg),
        stringResource(R.string.dose_unit_g),
        stringResource(R.string.dose_unit_drop),
        stringResource(R.string.dose_unit_puff),
        stringResource(R.string.dose_unit_app)
    )

    val customLabel = stringResource(R.string.dose_unit_custom)

    var expanded by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }

    // 現在の選択値を判定（事前定義されたオプションかカスタムか）
    val isCustomUnit = selectedUnit != null && selectedUnit !in unitOptions
    val displayValue = when {
        selectedUnit == null -> ""
        else -> selectedUnit
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayValue,
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.dose_unit)) },
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true)
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            // 空欄オプション
            DropdownMenuItem(
                text = { Text(stringResource(R.string.dose_unit_none)) },
                onClick = {
                    onUnitSelected(null)
                    expanded = false
                }
            )

            // 事前定義された単位
            unitOptions.forEach { unit ->
                DropdownMenuItem(
                    text = { Text(unit) },
                    onClick = {
                        onUnitSelected(unit)
                        expanded = false
                    }
                )
            }

            // カスタムオプション
            DropdownMenuItem(
                text = { Text(customLabel) },
                onClick = {
                    expanded = false
                    showCustomDialog = true
                }
            )
        }
    }

    // カスタム単位入力ダイアログ
    if (showCustomDialog) {
        var customUnitText by remember { mutableStateOf(if (isCustomUnit) selectedUnit ?: "" else "") }

        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text(stringResource(R.string.dose_unit_custom)) },
            text = {
                OutlinedTextField(
                    value = customUnitText,
                    onValueChange = { customUnitText = it },
                    label = { Text(stringResource(R.string.dose_unit)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onUnitSelected(customUnitText.ifBlank { null })
                    showCustomDialog = false
                }) {
                    Text(stringResource(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
