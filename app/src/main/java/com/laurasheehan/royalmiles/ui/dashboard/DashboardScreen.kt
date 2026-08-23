package com.laurasheehan.royalmiles.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.core.gamification.Badge
import com.laurasheehan.royalmiles.ui.components.BadgeChip
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
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { HeroHeader(daysToRace = state.daysToRace) }

            state.stats?.let { stats ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            XpBar(totalXp = stats.totalXp, level = stats.level, xpToNextLevel = stats.xpToNextLevel)
                            StreakChip(currentStreak = stats.currentStreak)
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
                item { Text("Nothing scheduled today — enjoy it.", color = ShimmerSilverDim) }
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
        }
    }
}

@Composable
private fun HeroHeader(daysToRace: Long) {
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
            Text(
                "Royal Parks Half",
                style = MaterialTheme.typography.headlineMedium,
                color = androidx.compose.ui.graphics.Color.White,
            )
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
        }
    }
}
