package com.laurasheehan.royalmiles.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.data.health.NutritionSummary
import com.laurasheehan.royalmiles.ui.components.BadgeChip
import com.laurasheehan.royalmiles.ui.components.EffortTrend
import com.laurasheehan.royalmiles.ui.components.SessionCard
import com.laurasheehan.royalmiles.ui.components.StreakChip
import com.laurasheehan.royalmiles.ui.components.XpBar
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onOpenSession: (Long) -> Unit,
    onOpenSync: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val nutritionToday by viewModel.nutritionToday.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) { viewModel.refreshNutrition() }

    LaunchedEffect(viewModel) {
        viewModel.affirmations.collect { message -> snackbarHostState.showSnackbar(message) }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(containerColor = RoyalPurple, contentColor = androidx.compose.ui.graphics.Color.White) {
                    Text(data.visuals.message)
                }
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
                    onOpenSync = onOpenSync,
                )
            }

            state.stats?.let { stats ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            XpBar(totalXp = stats.totalXp, level = stats.level, xpToNextLevel = stats.xpToNextLevel)
                            StreakChip(currentStreak = stats.currentStreak)
                        }
                    }
                }
                if (stats.recentEffort.isNotEmpty()) {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(Modifier.padding(16.dp)) {
                                Text("How training's felt lately", style = MaterialTheme.typography.titleMedium)
                                EffortTrend(
                                    ratings = stats.recentEffort,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 12.dp),
                                )
                            }
                        }
                    }
                }
                if (stats.badges.isNotEmpty()) {
                    item {
                        Text("Badges", style = MaterialTheme.typography.titleMedium)
                    }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(Badge.entries) { badge ->
                                BadgeChip(badge = badge, unlocked = badge in stats.badges)
                            }
                        }
                    }
                }
            }

            item { Text("Today", style = MaterialTheme.typography.titleLarge) }
            if (state.today.isEmpty()) {
                item { Text("Nothing scheduled today. Rest counts too.", color = ShimmerSilverDim) }
            } else {
                items(state.today, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onToggleComplete = { viewModel.toggleComplete(session) },
                        onClick = { onOpenSession(session.id) },
                    )
                }
            }

            item {
                Text(
                    "Up next",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(state.upNext, key = { it.id }) { session ->
                SessionCard(
                    session = session,
                    onToggleComplete = { viewModel.toggleComplete(session) },
                    onClick = { onOpenSession(session.id) },
                )
            }

            nutritionToday?.let { nutrition ->
                item {
                    NutritionCard(
                        nutrition = nutrition,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
    }
}

/**
 * Deliberately plain: no colors tied to a target, no progress bar, no XP. Just what was logged.
 */
@Composable
private fun NutritionCard(nutrition: NutritionSummary, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Today's nutrition", style = MaterialTheme.typography.titleMedium)
            Text("via Cronometer", style = MaterialTheme.typography.bodySmall, color = ShimmerSilverDim)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                NutritionStat(label = "kcal", value = nutrition.kcal.roundToDisplay())
                NutritionStat(label = "protein g", value = nutrition.proteinG.roundToDisplay())
                NutritionStat(label = "carbs g", value = nutrition.carbsG.roundToDisplay())
                NutritionStat(label = "fat g", value = nutrition.fatG.roundToDisplay())
            }
        }
    }
}

@Composable
private fun NutritionStat(label: String, value: String) {
    Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ShimmerSilverDim)
    }
}

private fun Double.roundToDisplay(): String = kotlin.math.round(this).toInt().toString()

@Composable
private fun HeroHeader(daysToRace: Long, phaseMessage: String, onOpenSync: () -> Unit) {
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
                    color = androidx.compose.ui.graphics.Color.White,
                )
                IconButton(onClick = onOpenSync) {
                    Icon(Icons.Filled.Sync, contentDescription = "Sync workouts", tint = androidx.compose.ui.graphics.Color.White)
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
            if (phaseMessage.isNotBlank()) {
                Text(
                    text = phaseMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}
