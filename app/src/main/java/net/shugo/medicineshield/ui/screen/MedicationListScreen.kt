package net.shugo.medicineshield.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.shugo.medicineshield.data.model.CycleType
import net.shugo.medicineshield.data.model.MedicationWithTimes
import net.shugo.medicineshield.viewmodel.MedicationListViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationListScreen(
    viewModel: MedicationListViewModel,
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
                title = { Text("お薬一覧") }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddMedication) {
                Icon(Icons.Default.Add, contentDescription = "お薬を追加")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTabIndex) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = { Text("服用中") }
                )
                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = { Text("過去のお薬") }
                )
            }

            val medications = if (selectedTabIndex == 0) currentMedications else pastMedications
            val emptyMessage = if (selectedTabIndex == 0) {
                "登録されているお薬はありません"
            } else {
                "過去のお薬はありません"
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
            title = { Text("確認") },
            text = { Text("服用履歴も削除されます。\n本当にこのお薬を削除しますか?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMedication(medicationId)
                        medicationToDelete = null
                    }
                ) {
                    Text("はい")
                }
            },
            dismissButton = {
                TextButton(onClick = { medicationToDelete = null }) {
                    Text("いいえ")
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
                        Icon(Icons.Default.Edit, contentDescription = "編集")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "削除")
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // 服用時間
            Text(
                "服用時間: ${times.joinToString(", ") { it.time }}",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(4.dp))

            // 現在有効なConfigを取得
            val currentConfig = medicationWithTimes.getCurrentConfig()

            currentConfig?.let { config ->
                // サイクル
                val cycleText = when (config.cycleType) {
                    CycleType.DAILY -> "毎日"
                    CycleType.WEEKLY -> {
                        val days = config.cycleValue?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
                        val dayNames = days.map { getDayName(it) }
                        "毎週${dayNames.joinToString("・")}"
                    }
                    CycleType.INTERVAL -> "${config.cycleValue}日ごと"
                }
                Text(
                    "サイクル: $cycleText",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(4.dp))

                // 期間
                val dateFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                val startDateStr = dateFormat.format(Date(config.medicationStartDate))
                val endDateStr = config.medicationEndDate?.let { dateFormat.format(Date(it)) }
                val periodText = if (endDateStr != null) {
                    "$startDateStr 〜 $endDateStr"
                } else {
                    "$startDateStr 〜"
                }
                Text(
                    "期間: $periodText",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun getDayName(dayIndex: Int): String {
    return when (dayIndex) {
        0 -> "日"
        1 -> "月"
        2 -> "火"
        3 -> "水"
        4 -> "木"
        5 -> "金"
        6 -> "土"
        else -> ""
    }
}
