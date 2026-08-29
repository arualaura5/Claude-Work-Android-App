package com.laurasheehan.royalmiles.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.SessionEntity
import com.laurasheehan.royalmiles.ui.components.accentColor
import com.laurasheehan.royalmiles.ui.components.formatKm
import com.laurasheehan.royalmiles.ui.components.icon
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.sync.openUrl
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import java.time.format.DateTimeFormatter

private val logDateFormat = DateTimeFormatter.ofPattern("EEE d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLogScreen(viewModel: ActivityLogViewModel, onOpenSession: (Long) -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Activity") }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { TotalsCard(state) }

            if (state.sessions.isEmpty()) {
                item {
                    Text(
                        "Nothing logged yet. Sessions appear here once you tick them off or match " +
                            "them from Health Connect.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.sessions, key = { it.id }) { session ->
                LoggedSessionCard(session = session, onClick = { onOpenSession(session.id) })
            }
        }
    }
}

/** What's accumulated so far, stated flatly. The point is to see the work add up. */
@Composable
private fun TotalsCard(state: ActivityLogUiState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(RoyalPurple, BlushPink)))
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Total(label = "sessions", value = "${state.totalSessions}")
            Total(label = "km", value = formatKm(state.totalDistanceKm))
            Total(label = "hours", value = "%.1f".format(state.totalMinutes / 60.0))
        }
    }
}

@Composable
private fun Total(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun LoggedSessionCard(session: SessionEntity, onClick: () -> Unit) {
    val accent = session.type.accentColor()
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(session.type.icon(), contentDescription = null, tint = accent)
                }
                Text(
                    (session.completedAt ?: session.date).format(logDateFormat),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                buildString {
                    append(session.title)
                    session.actualDurationMin?.let { append(" · $it min") }
                },
                style = MaterialTheme.typography.titleMedium,
            )

            val metrics = loggedMetrics(session)
            if (metrics.isNotEmpty()) {
                Text(
                    metrics.joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            session.effortRating?.let { rating ->
                Text(
                    "Felt like $rating/5",
                    style = MaterialTheme.typography.bodySmall,
                    color = ComebackGold,
                )
            }

            session.garminUrl?.let { url ->
                val context = LocalContext.current
                TextButton(
                    onClick = { openUrl(context, url) },
                    contentPadding = PaddingValues(0.dp),
                ) {
                    Text("View in Garmin Connect")
                }
            }
        }
    }
}

/**
 * Whatever was actually recorded — planned targets are not padded in.
 *
 * Formatting deliberately mirrors the Sync screen's workout card down to the units and decimal
 * places, so the same run reads identically before and after it's matched to a session.
 */
private fun loggedMetrics(session: SessionEntity): List<String> = buildList {
    session.actualDistanceKm?.let { add("%.2f km".format(it)) }
    val km = session.actualDistanceKm
    val min = session.actualDurationMin
    if (km != null && km > 0 && min != null) {
        val pace = min / km
        add("%d:%02d /km".format(pace.toInt(), ((pace - pace.toInt()) * 60).toInt()))
    }
    session.actualAvgHeartRate?.let { add("$it bpm avg") }
    session.actualMaxHeartRate?.let { add("$it max") }
    session.actualCalories?.let { add("$it kcal") }
    session.actualElevationGainM?.let { add("${it}m elev") }
}
