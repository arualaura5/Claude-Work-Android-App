package com.laurasheehan.royalmiles.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.ui.components.SessionCard
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import java.time.format.DateTimeFormatter

private val weekDateFormat = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenSession: (Long) -> Unit,
    onAddSession: () -> Unit,
) {
    val weeks by viewModel.weeks.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var weekToScaleDown by remember { mutableStateOf<java.time.LocalDate?>(null) }
    var hasScrolled by remember { mutableStateOf(false) }

    LaunchedEffect(viewModel) {
        viewModel.affirmations.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    // Open on the week she's actually in. Landing on week 1 in the past made the plan feel like a
    // record of what's gone rather than what's next.
    LaunchedEffect(weeks.size) {
        if (weeks.isNotEmpty() && !hasScrolled) {
            hasScrolled = true
            listState.scrollToItem(viewModel.currentWeekIndex(weeks))
        }
    }

    fun skipWithUndo(session: SessionEntity) {
        viewModel.skip(session)
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = "Cleared. Onwards.",
                actionLabel = "Undo",
                duration = SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoSkip(session.id)
        }
    }

    weekToScaleDown?.let { week ->
        AlertDialog(
            onDismissRequest = { weekToScaleDown = null },
            title = { Text("Dial this week back?") },
            text = {
                Text(
                    "Long run down a quarter, easy runs down a fifth, and the second strength " +
                        "session written off. That's a coaching call, not a miss — it costs you " +
                        "nothing in XP, badges or your streak.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.scaleDownWeek(week)
                    weekToScaleDown = null
                }) { Text("Dial it back") }
            },
            dismissButton = {
                TextButton(onClick = { weekToScaleDown = null }) { Text("Leave it") }
            },
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = RoyalPurple,
                    contentColor = Color.White,
                    actionColor = ComebackGold,
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSession, containerColor = RoyalPurple) {
                Icon(Icons.Filled.Add, contentDescription = "Add session", tint = Color.White)
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(weeks, key = { it.weekNumber }) { week ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekHeader(
                        weekNumber = week.weekNumber,
                        phase = week.phase,
                        startDate = week.startDate,
                        longRunKm = week.longRunKm,
                        onScaleDown = { weekToScaleDown = week.startDate },
                    )
                    week.sessions.forEach { session ->
                        SessionCard(
                            session = session,
                            onToggleComplete = { viewModel.toggleComplete(session) },
                            onClick = { onOpenSession(session.id) },
                            onSkip = { skipWithUndo(session) },
                            onRate = { viewModel.rate(session, it) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(
    weekNumber: Int,
    phase: TrainingPhase,
    startDate: java.time.LocalDate,
    longRunKm: Double?,
    onScaleDown: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(phaseColor(phase).copy(alpha = 0.16f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("Week $weekNumber", style = MaterialTheme.typography.titleMedium)
            Text(
                "w/c ${startDate.format(weekDateFormat)} · ${phase.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (longRunKm != null) {
                Text(
                    "${com.laurasheehan.royalmiles.ui.components.formatKm(longRunKm)}km",
                    style = MaterialTheme.typography.titleMedium,
                    color = ComebackGold,
                )
            }
            androidx.compose.material3.IconButton(onClick = onScaleDown) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = "Dial this week back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun phaseColor(phase: TrainingPhase) = when (phase) {
    TrainingPhase.BASE -> RoyalPurple
    TrainingPhase.BUILD -> BlushPink
    TrainingPhase.PEAK -> ComebackGold
    TrainingPhase.TAPER -> RoyalPurple
}
