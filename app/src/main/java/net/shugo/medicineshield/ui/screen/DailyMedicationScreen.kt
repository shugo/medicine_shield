package net.shugo.medicineshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.shugo.medicineshield.data.model.DailyMedicationItem
import net.shugo.medicineshield.viewmodel.DailyMedicationViewModel
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyMedicationScreen(
    viewModel: DailyMedicationViewModel,
    onNavigateToMedicationList: () -> Unit
) {
    val dailyMedications by viewModel.dailyMedications.collectAsState()
    val displayDateText by viewModel.displayDateText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val selectedDate by viewModel.selectedDate.collectAsState()

    var showDatePicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("日々の服薬") },
                actions = {
                    IconButton(onClick = onNavigateToMedicationList) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "お薬一覧"
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
                        }
                    )
                }
            }
        }

        // DatePickerダイアログ
        if (showDatePicker) {
            DatePickerDialog(
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
                    contentDescription = "前日",
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
                    contentDescription = "翌日",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    selectedDate: Calendar,
    onDateSelected: (year: Int, month: Int, dayOfMonth: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.timeInMillis
    )

    androidx.compose.material3.DatePickerDialog(
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

@Composable
fun EmptyMedicationState(selectedDate: Calendar, onNavigateToMedicationList: () -> Unit) {
    // 今日かどうか判定
    val today = Calendar.getInstance()
    val isToday = selectedDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                  selectedDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    val isFuture = selectedDate.timeInMillis > today.timeInMillis

    val message = when {
        isToday -> "今日飲むお薬はありません"
        isFuture -> "この日に飲むお薬はありません"
        else -> "この日に飲んだお薬はありません"
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
                Text("お薬を追加する")
            }
        }
    }
}

@Composable
fun MedicationList(
    medications: List<DailyMedicationItem>,
    onToggleTaken: (Long, Int, Boolean) -> Unit
) {
    // 時刻でグループ化
    val groupedMedications = medications.groupBy { it.scheduledTime }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        groupedMedications.forEach { (time, items) ->
            item {
                TimeHeader(time)
            }

            items(items) { medication ->
                MedicationItem(
                    medication = medication,
                    onToggleTaken = onToggleTaken
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
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
fun MedicationItem(
    medication: DailyMedicationItem,
    onToggleTaken: (Long, Int, Boolean) -> Unit
) {
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
                Text(
                    text = medication.medicationName,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                if (medication.isTaken && medication.takenAt != null) {
                    Text(
                        text = formatTakenTime(medication.takenAt),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

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
    }
}

@Composable
fun formatTakenTime(timestamp: Long): String {
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return String.format(
        "服用済み %02d:%02d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE)
    )
}
