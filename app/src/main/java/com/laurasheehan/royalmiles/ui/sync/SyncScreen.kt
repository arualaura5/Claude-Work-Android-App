package com.laurasheehan.royalmiles.ui.sync

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.health.ExternalWorkout
import com.laurasheehan.royalmiles.ui.components.accentColor
import com.laurasheehan.royalmiles.ui.components.icon
import com.laurasheehan.royalmiles.ui.components.label
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import java.time.format.DateTimeFormatter

private val workoutDateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

/** Whatever Health Connect actually had for this workout — each part is skipped when absent. */
private fun workoutMetrics(workout: ExternalWorkout): List<String> = buildList {
    workout.distanceKm?.let { add("%.2f km".format(it)) }
    workout.paceMinPerKm?.let { pace ->
        val minutes = pace.toInt()
        val seconds = ((pace - minutes) * 60).toInt()
        add("%d:%02d /km".format(minutes, seconds))
    }
    workout.avgHeartRate?.let { add("$it bpm avg") }
    workout.maxHeartRate?.let { add("$it max") }
    workout.calories?.let { add("$it kcal") }
    workout.elevationGainM?.let { add("${it}m elev") }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(viewModel: SyncViewModel, onDone: () -> Unit, onOpenDiagnostics: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingMatch by remember { mutableStateOf<ExternalWorkout?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = remember { viewModel.permissionContract() },
        onResult = { viewModel.onPermissionGranted() },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sync workouts") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenDiagnostics) {
                        Icon(Icons.Filled.Info, contentDescription = "What's in Health Connect")
                    }
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.loading -> {}
            !state.available -> {
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .padding(24.dp),
                ) {
                    Text(
                        "Health Connect isn't available on this device. It ships with Android 14+ and " +
                            "can be installed from the Play Store on most phones back to Android 9.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            !state.hasPermission -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "Connect Health Connect to see workouts logged by Strava, Garmin Connect or " +
                            "Google Fit here, and match them to a planned session instead of typing it in.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { permissionLauncher.launch(viewModel.permissionsToRequest) }) {
                        Text("Connect Health Connect")
                    }
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.workouts.isEmpty()) {
                        item { Text("No workouts in Health Connect from the last 7 days yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    items(state.workouts) { workout ->
                        WorkoutCard(workout = workout, onMatch = { pendingMatch = workout })
                    }
                }
            }
        }
    }

    val workout = pendingMatch
    if (workout != null) {
        val candidates = state.candidatesByWorkout[workout].orEmpty()
        AlertDialog(
            onDismissRequest = { pendingMatch = null },
            title = { Text("Match to which session?") },
            text = {
                if (candidates.isEmpty()) {
                    Text("No nearby planned session found within a couple of days of this workout.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        candidates.forEach { session ->
                            TextButton(onClick = {
                                viewModel.match(workout, session)
                                pendingMatch = null
                            }) {
                                Text("${session.title} — ${session.date.format(workoutDateFormat)}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { pendingMatch = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun WorkoutCard(workout: ExternalWorkout, onMatch: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Icon(workout.guessedType.icon(), contentDescription = null, tint = workout.guessedType.accentColor())
                Text(workout.localDate.format(workoutDateFormat), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${workout.title ?: workout.guessedType.label()} · ${workout.durationMinutes} min",
                style = MaterialTheme.typography.titleMedium,
            )
            val metrics = workoutMetrics(workout)
            if (metrics.isNotEmpty()) {
                Text(
                    metrics.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = onMatch, colors = ButtonDefaults.buttonColors(containerColor = RoyalPurple)) {
                Text("Match to a session")
            }
        }
    }
}
