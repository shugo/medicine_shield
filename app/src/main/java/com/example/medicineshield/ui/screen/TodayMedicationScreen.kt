package com.example.medicineshield.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.medicineshield.data.model.TodayMedicationItem
import com.example.medicineshield.viewmodel.TodayMedicationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayMedicationScreen(
    viewModel: TodayMedicationViewModel,
    onNavigateToMedicationList: () -> Unit
) {
    val todayMedications by viewModel.todayMedications.collectAsState()
    val todayDate by viewModel.todayDate.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("今日飲む薬") },
                actions = {
                    IconButton(onClick = onNavigateToMedicationList) {
                        Icon(
                            imageVector = Icons.Default.List,
                            contentDescription = "薬一覧"
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
            // 日付表示
            Text(
                text = todayDate,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(16.dp)
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

                todayMedications.isEmpty() -> {
                    EmptyMedicationState(onNavigateToMedicationList)
                }

                else -> {
                    MedicationList(
                        medications = todayMedications,
                        onToggleTaken = { medicationId, scheduledTime, isTaken ->
                            viewModel.toggleMedicationTaken(medicationId, scheduledTime, isTaken)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyMedicationState(onNavigateToMedicationList: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "今日飲む薬はありません",
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
                Text("薬を追加する")
            }
        }
    }
}

@Composable
fun MedicationList(
    medications: List<TodayMedicationItem>,
    onToggleTaken: (Long, String, Boolean) -> Unit
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
    medication: TodayMedicationItem,
    onToggleTaken: (Long, String, Boolean) -> Unit
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
                        medication.scheduledTime,
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
