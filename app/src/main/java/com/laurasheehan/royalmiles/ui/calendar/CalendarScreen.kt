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
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.model.TrainingPhase
import com.laurasheehan.royalmiles.ui.components.SessionCard
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim
import java.time.format.DateTimeFormatter

private val weekDateFormat = DateTimeFormatter.ofPattern("d MMM")

@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onOpenSession: (Long) -> Unit,
    onAddSession: () -> Unit,
) {
    val weeks by viewModel.weeks.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onAddSession, containerColor = RoyalPurple) {
                Icon(Icons.Filled.Add, contentDescription = "Add session", tint = androidx.compose.ui.graphics.Color.White)
            }
        },
    ) { padding ->
        LazyColumn(
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
                    )
                    week.sessions.forEach { session ->
                        SessionCard(
                            session = session,
                            onToggleComplete = { viewModel.toggleComplete(session) },
                            onClick = { onOpenSession(session.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekHeader(weekNumber: Int, phase: TrainingPhase, startDate: java.time.LocalDate, longRunKm: Double?) {
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
                color = ShimmerSilverDim,
            )
        }
        if (longRunKm != null) {
            Text(
                "${com.laurasheehan.royalmiles.ui.components.formatKm(longRunKm)}km",
                style = MaterialTheme.typography.titleMedium,
                color = ComebackGold,
            )
        }
    }
}

private fun phaseColor(phase: TrainingPhase) = when (phase) {
    TrainingPhase.BASE -> RoyalPurple
    TrainingPhase.BUILD -> BlushPink
    TrainingPhase.PEAK -> ComebackGold
    TrainingPhase.TAPER -> RoyalPurple
}
