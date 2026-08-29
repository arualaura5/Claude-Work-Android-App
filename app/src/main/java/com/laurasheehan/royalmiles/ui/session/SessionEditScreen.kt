package com.laurasheehan.royalmiles.ui.session

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.model.SessionType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionEditScreen(
    viewModel: SessionEditViewModel,
    onDone: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "Add session" else "Edit session") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete session")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (state.loading) return@Scaffold

        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Title") },
                modifier = Modifier.fillMaxWidth(),
            )

            TypeDropdown(selected = state.type, onSelected = viewModel::updateType)

            WeekDropdown(
                weeks = state.availableWeeks,
                selectedWeekNumber = state.weekNumber,
                onSelected = viewModel::updateWeek,
            )

            DatePickerField(date = state.date, onDateSelected = viewModel::updateDate)

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = state.distanceKm,
                    onValueChange = viewModel::updateDistance,
                    label = { Text("Distance (km)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = state.durationMin,
                    onValueChange = viewModel::updateDuration,
                    label = { Text("Duration (min)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::updateNotes,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Optional session", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.optional, onCheckedChange = viewModel::updateOptional)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Completed", style = MaterialTheme.typography.bodyLarge)
                Switch(checked = state.isCompleted, onCheckedChange = viewModel::updateCompleted)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Didn't do this one", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Closes it off so it stops appearing in Up next. Nothing is counted against you.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.isSkipped, onCheckedChange = viewModel::updateSkipped)
            }

            if (state.isCompleted) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.actualDistanceKm,
                        onValueChange = viewModel::updateActualDistance,
                        label = { Text("Actual km") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.actualDurationMin,
                        onValueChange = viewModel::updateActualDuration,
                        label = { Text("Actual min") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                EffortRatingPicker(selected = state.effortRating, onSelected = viewModel::updateEffortRating)
            }

            Button(onClick = viewModel::save, modifier = Modifier.fillMaxWidth()) {
                Text("Save")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeDropdown(selected: SessionType, onSelected: (SessionType) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selected.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text("Type") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            SessionType.entries.forEach { type ->
                DropdownMenuItem(
                    text = { Text(type.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onSelected(type)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekDropdown(
    weeks: List<com.laurasheehan.royalmiles.data.UiWeek>,
    selectedWeekNumber: Int,
    onSelected: (com.laurasheehan.royalmiles.data.UiWeek) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = weeks.firstOrNull { it.weekNumber == selectedWeekNumber }
        ?.let { "Week ${it.weekNumber} · ${it.phase.label}" }
        ?: "Week $selectedWeekNumber"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            label = { Text("Week") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.exposedDropdownSize(),
        ) {
            weeks.forEach { week ->
                DropdownMenuItem(
                    text = { Text("Week ${week.weekNumber} · ${week.phase.label}") },
                    onClick = {
                        onSelected(week)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val effortLabels = listOf(1 to "Rough", 2 to "Tough", 3 to "OK", 4 to "Good", 5 to "Great")

@Composable
private fun EffortRatingPicker(selected: Int?, onSelected: (Int) -> Unit) {
    Column {
        Text("How did that feel?", style = MaterialTheme.typography.bodyLarge)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            effortLabels.forEach { (value, label) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelected(value) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = com.laurasheehan.royalmiles.ui.theme.ComebackGold,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                    ),
                )
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEEE d MMM yyyy")

@Composable
private fun DatePickerField(date: LocalDate, onDateSelected: (LocalDate) -> Unit) {
    val context = LocalContext.current
    OutlinedButton(
        onClick = {
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth -> onDateSelected(LocalDate.of(year, month + 1, dayOfMonth)) },
                date.year,
                date.monthValue - 1,
                date.dayOfMonth,
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.CalendarMonth, contentDescription = null)
        Text("  ${date.format(dateFormat)}")
    }
}
