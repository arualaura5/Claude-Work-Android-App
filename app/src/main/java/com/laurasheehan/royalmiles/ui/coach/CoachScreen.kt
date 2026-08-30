package com.laurasheehan.royalmiles.ui.coach

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.ComebackGoldSoft
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleLight
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim

/**
 * The Coach tab: the laptop dashboard's conclusions, shown as they are.
 *
 * Deliberately a plain read of the payload — the numbers are presented with their dates and
 * nothing is hidden or gated behind a freshness rule. The dates are stated so they can be judged;
 * the judging is the reader's job, not this screen's.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachScreen(viewModel: CoachViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // OpenDocument rather than GetContent: only OpenDocument returns a URI that can be given
    // persistable read permission, which is what lets Refresh re-read the file later.
    val picker = rememberLauncherForActivityResult(
        contract = remember { ActivityResultContracts.OpenDocument() },
        onResult = { uri -> uri?.let(viewModel::import) },
    )

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            title = { Text("Import failed") },
            text = { Text(message) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                actions = {
                    if (state.sourceRemembered) {
                        IconButton(onClick = viewModel::refresh, enabled = !state.loading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Re-read the file")
                        }
                    }
                    IconButton(
                        onClick = { picker.launch(arrayOf("application/json", "*/*")) },
                        enabled = !state.loading,
                    ) {
                        Icon(Icons.Filled.FileOpen, contentDescription = "Import coach.json")
                    }
                },
            )
        },
    ) { padding ->
        val payload = state.payload

        if (payload == null) {
            EmptyState(
                loading = state.loading,
                onPick = { picker.launch(arrayOf("application/json", "*/*")) },
                modifier = Modifier.fillMaxSize().padding(padding),
            )
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { FreshnessLine(payload, state.dataAgeDays) }

            // Explicit if/else rather than `?.let { } ?: item { }` — the let block's value would be
            // the trailing coachNote?.let, so a payload with coaching but no coach note returns null
            // and silently falls through to the "no coaching" card.
            val coaching = payload.coaching
            if (coaching != null) {
                item { CoachingSummaryCard(coaching) }
                items(coaching.actionPoints.size) { index ->
                    ActionPointCard(coaching.actionPoints[index])
                }
                coaching.coachNote?.let { note ->
                    item { CoachNoteCard(note) }
                }
            } else {
                item { NoCoachingCard(payload.coachingAbsentReason) }
            }

            payload.readiness?.takeIf { it.available }?.let { readiness ->
                item { ReadinessCard(readiness, payload.coverage) }
            }

            payload.hrv?.let { hrv ->
                item { HrvCard(hrv, payload.plan?.milestone) }
            }

            if (payload.warnings.isNotEmpty()) {
                item { WarningsCard(payload.warnings) }
            }
        }
    }
}

@Composable
private fun EmptyState(loading: Boolean, onPick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            return@Column
        }
        Text("No coach data yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Garmin doesn't share HRV with Health Connect, so this comes from the dashboard on the " +
                "laptop instead. Run export_coach_payload.py there, put the coach.json it writes " +
                "somewhere this phone can reach, and pick it here.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onPick) { Text("Pick coach.json") }
    }
}

@Composable
private fun FreshnessLine(payload: CoachPayload, ageDays: Long?) {
    val dataDate = payload.freshness.dbDailyMaxDate ?: "unknown"
    val age = when {
        ageDays == null -> ""
        ageDays <= 0L -> " · today"
        ageDays == 1L -> " · 1 day ago"
        else -> " · $ageDays days ago"
    }
    Text(
        "Data to $dataDate$age",
        style = MaterialTheme.typography.labelMedium,
        color = ShimmerSilverDim,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun CoachingSummaryCard(coaching: CoachPayload.Coaching) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (coaching.onTrack) "On track" else "Off target",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (coaching.onTrack) ComebackGold else BlushPink,
                    fontWeight = FontWeight.SemiBold,
                )
                coaching.dataDate?.let {
                    Text(
                        "  ·  from $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = ShimmerSilverDim,
                    )
                }
            }
            coaching.statusSummary?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, lineHeight = 21.sp)
            }
        }
    }
}

@Composable
private fun ActionPointCard(point: CoachPayload.Coaching.ActionPoint) {
    val accent = when (point.priority.lowercase()) {
        "critical" -> BlushPink
        "high" -> ComebackGold
        "medium" -> RoyalPurpleLight
        else -> ShimmerSilverDim
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        // height(IntrinsicSize.Min) is what gives the accent bar a bounded height to fill —
        // fillMaxHeight against a Row's unbounded incoming constraints would collapse it.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // A colour bar rather than a coloured card: the priority should be scannable without
            // making five stacked cards shout at once.
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp)),
            )
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(point.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    point.priority.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                )
                Text(point.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun CoachNoteCard(note: String) {
    Text(
        note,
        style = MaterialTheme.typography.bodySmall,
        color = ShimmerSilverDim,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
    )
}

@Composable
private fun NoCoachingCard(reason: String?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No coaching in this export", style = MaterialTheme.typography.titleSmall)
            Text(
                reason ?: "The dashboard hasn't filed coaching against this data yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadinessCard(readiness: CoachPayload.Readiness, coverage: CoachPayload.Coverage?) {
    val accent = when (readiness.status) {
        "good" -> ComebackGold
        "fair" -> RoyalPurpleLight
        else -> BlushPink
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Readiness", style = MaterialTheme.typography.labelLarge, color = ShimmerSilverDim)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    readiness.score?.toString() ?: "—",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    "  ${readiness.label.orEmpty()}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            readiness.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

            // Stated, not enforced: a score off two signals and one off five read identically
            // otherwise, and which it is changes how much it is worth.
            val signals = readiness.componentCount
            val confidence = readiness.confidence
            if (signals != null || confidence != null) {
                Text(
                    listOfNotNull(
                        signals?.let { "$it signals" },
                        confidence?.let { "$it confidence" },
                        readiness.date?.let { "scored $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = ShimmerSilverDim,
                )
            }
            coverage?.let {
                Text(
                    "Last ${it.windowDays} days: ${it.hrvNights} HRV nights · " +
                        "${it.sleepNights} sleep · ${it.rhrDays} RHR",
                    style = MaterialTheme.typography.labelSmall,
                    color = ShimmerSilverDim,
                )
            }
        }
    }
}

@Composable
private fun HrvCard(hrv: CoachPayload.HrvMetrics, milestone: CoachPayload.PlanStatus.Milestone?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("HRV & recovery", style = MaterialTheme.typography.labelLarge, color = ShimmerSilverDim)

            MetricRow("HRV 7-day", hrv.hrv7d, "ms", milestone?.hrv7d)
            MetricRow("HRV 30-day", hrv.hrv30d, "ms", null)
            MetricRow("H0 (first hour)", hrv.h0, "ms", milestone?.h0)
            MetricRow("Nightly peak", hrv.nightlyPeak, "ms", null)
            MetricRow("Resting HR", hrv.rhr, "bpm", milestone?.rhr)
            MetricRow(
                "High-HRV nights",
                hrv.highHrvNights?.toDouble(),
                "",
                milestone?.highHrvNights?.toDouble(),
            )

            val trend = listOfNotNull(
                hrv.trend14d?.takeIf { it != "unknown" }?.let { "14d $it" },
                hrv.trend30d?.takeIf { it != "unknown" }?.let { "30d $it" },
            )
            if (trend.isNotEmpty()) {
                Text(
                    trend.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = ShimmerSilverDim,
                )
            }
        }
    }
}

@Composable
private fun MetricRow(label: String, value: Double?, unit: String, target: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                value?.let { formatMetric(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoyalPurpleLight,
            )
            if (unit.isNotEmpty()) {
                Text(" $unit", style = MaterialTheme.typography.labelSmall, color = ShimmerSilverDim)
            }
            target?.let {
                Text(
                    "  → ${formatMetric(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = ComebackGoldSoft,
                )
            }
        }
    }
}

private fun formatMetric(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)

@Composable
private fun WarningsCard(warnings: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Flags", style = MaterialTheme.typography.labelLarge, color = ShimmerSilverDim)
            warnings.forEach { warning ->
                Text("• $warning", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
