package com.laurasheehan.royalmiles.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.ui.components.BadgeChip
import com.laurasheehan.royalmiles.ui.components.CelebrationDialog
import com.laurasheehan.royalmiles.ui.components.LongRunProgression
import com.laurasheehan.royalmiles.ui.components.SessionCard
import com.laurasheehan.royalmiles.ui.components.StreakChip
import com.laurasheehan.royalmiles.ui.components.WeekWrapCard
import com.laurasheehan.royalmiles.ui.components.XpBar
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.launch

private val weekCommencingFormat = DateTimeFormatter.ofPattern("d MMM")

/** Unlocked badges, then the next few still to come — never a screen that is mostly padlocks. */
private const val LOCKED_BADGES_SHOWN = 3

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenSync: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val celebration by viewModel.celebration.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(viewModel) {
        viewModel.affirmations.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    celebration?.let { moment ->
        CelebrationDialog(
            headline = moment.headline,
            detail = moment.detail,
            badges = moment.badges,
            onDismiss = viewModel::dismissCelebration,
        )
    }

    /** Writing a session off is always undoable, and says so. */
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

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    containerColor = RoyalPurple,
                    contentColor = Color.White,
                    actionColor = ComebackGold,
                    snackbarData = data,
                )
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                HeroHeader(
                    daysToRace = state.daysToRace,
                    phaseMessage = state.phaseMessage,
                    weekNumber = state.weekNumber,
                    totalWeeks = state.totalWeeks,
                    weekCommencing = state.weekCommencing,
                    onOpenSync = onOpenSync,
                )
            }

            if (state.coachMotivation != null || state.coachKeyReminder != null) {
                item {
                    CoachTopNote(
                        motivation = state.coachMotivation,
                        keyReminder = state.coachKeyReminder,
                    )
                }
            }

            state.weekWrap?.let { wrap ->
                item { WeekWrapCard(summary = wrap, onDismiss = viewModel::dismissWeekWrap) }
            }

            state.stats?.let { stats ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            XpBar(totalXp = stats.totalXp, level = stats.level, xpToNextLevel = stats.xpToNextLevel)
                            // Hidden rather than shown at zero: a "0 weeks" chip is a reprimand,
                            // and the chip's job is to reward, not to keep score of nothing.
                            if (stats.currentWeekStreak > 0) {
                                StreakChip(weekStreak = stats.currentWeekStreak)
                            }
                        }
                    }
                }
            }

            if (state.longRuns.size > 1) {
                item { LongRunProgression(points = state.longRuns) }
            }

            state.stats?.let { stats ->
                if (stats.badges.isNotEmpty()) {
                    item {
                        Text("Badges", style = MaterialTheme.typography.titleMedium)
                    }
                    item {
                        val shown = Badge.entries.filter { it in stats.badges } +
                            Badge.entries.filterNot { it in stats.badges }.take(LOCKED_BADGES_SHOWN)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(shown) { badge ->
                                BadgeChip(badge = badge, unlocked = badge in stats.badges)
                            }
                        }
                    }
                }
            }

            item { Text("Today", style = MaterialTheme.typography.titleLarge) }
            if (state.today.isEmpty()) {
                item { Text("Nothing scheduled today. Rest counts too.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.today, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onToggleComplete = { viewModel.toggleComplete(session) },
                        onSkip = { skipWithUndo(session) },
                        onRate = { viewModel.rate(session, it) },
                        onClick = { onOpenSession(session.id) },
                    )
                }
            }

            if (state.stillOpen.isNotEmpty()) {
                item {
                    Column(modifier = Modifier.padding(top = 8.dp)) {
                        Text("Still open", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Long-press to write one off. Nothing is counted against you either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.stillOpen, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onToggleComplete = { viewModel.toggleComplete(session) },
                        onSkip = { skipWithUndo(session) },
                        onRate = { viewModel.rate(session, it) },
                        onClick = { onOpenSession(session.id) },
                    )
                }
                moreCountItem(total = state.stillOpenTotal, shown = state.stillOpen.size)
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Up next", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onOpenCalendar) {
                        Text("Full schedule")
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
            items(state.upNext, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onToggleComplete = { viewModel.toggleComplete(session) },
                    onSkip = { skipWithUndo(session) },
                    onRate = { viewModel.rate(session, it) },
                    onClick = { onOpenSession(session.id) },
                )
            }
            moreCountItem(total = state.upNextTotal, shown = state.upNext.size)
        }
    }
}

@Composable
private fun CoachTopNote(motivation: String?, keyReminder: String?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            motivation?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            keyReminder?.let {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Key reminder",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.moreCountItem(total: Int, shown: Int) {
    val remaining = total - shown
    if (remaining > 0) {
        item {
            Text(
                "and $remaining more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 2.dp),
            )
        }
    }
}

@Composable
private fun HeroHeader(
    daysToRace: Long,
    phaseMessage: String,
    weekNumber: Int,
    totalWeeks: Int,
    weekCommencing: java.time.LocalDate?,
    onOpenSync: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(RoyalPurple, BlushPink)))
                .padding(20.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Royal Parks Half",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                )
                IconButton(onClick = onOpenSync) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync workouts", tint = Color.White)
                }
            }
            Text(
                text = when {
                    daysToRace > 1 -> "$daysToRace days to go"
                    daysToRace == 1L -> "1 day to go — you've got this"
                    daysToRace == 0L -> "Race day. Trust the training."
                    else -> "Race complete"
                },
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = ComebackGold,
            )
            // A countdown only measures runway disappearing. This measures what's already banked,
            // and it fills whether or not any given session got done.
            if (weekNumber > 0 && totalWeeks > 0) {
                Text(
                    text = buildString {
                        append("Week $weekNumber of $totalWeeks")
                        weekCommencing?.let { append(" · w/c ${it.format(weekCommencingFormat)}") }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    modifier = Modifier.padding(top = 10.dp),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .height(8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.White.copy(alpha = 0.25f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(weekNumber.toFloat() / totalWeeks.toFloat())
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(ComebackGold),
                    )
                }
            }
            if (phaseMessage.isNotBlank()) {
                Text(
                    text = phaseMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

