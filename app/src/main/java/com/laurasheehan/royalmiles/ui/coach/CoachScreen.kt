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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold

// One corner language for the whole screen rather than mismatched Material defaults: a standard
// radius for the working cards, and a slightly rounder one for the two cards meant to read as the
// screen's headline — readiness and the coaching status.
private val CardCornerDp = 18.dp
private val HeroCardCornerDp = 22.dp
private val CardCorner = RoundedCornerShape(CardCornerDp)
private val HeroCardCorner = RoundedCornerShape(HeroCardCornerDp)

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
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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
        modifier = modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
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
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CoachingSummaryCard(coaching: CoachPayload.Coaching) {
    val accent = if (coaching.onTrack) ComebackGold else BlushPink
    Card(
        shape = HeroCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A real pill rather than plain coloured text: this is the second-strongest thing
                // on the screen after the readiness score, so it should look like a verdict, not a
                // label.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(accent.copy(alpha = 0.18f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Icon(
                        if (coaching.onTrack) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        if (coaching.onTrack) "On track" else "Off target",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                coaching.dataDate?.let {
                    Text(
                        "  ·  from $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            coaching.statusSummary?.let {
                Text(it, style = MaterialTheme.typography.bodyLarge, lineHeight = 22.sp)
            }
        }
    }
}

@Composable
private fun ActionPointCard(point: CoachPayload.Coaching.ActionPoint) {
    val accent = when (point.priority.lowercase()) {
        "critical" -> BlushPink
        "high" -> ComebackGold
        "medium" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(shape = CardCorner, modifier = Modifier.fillMaxWidth()) {
        // height(IntrinsicSize.Min) is what gives the accent bar a bounded height to fill —
        // fillMaxHeight against a Row's unbounded incoming constraints would collapse it.
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // A colour bar rather than a coloured card: the priority should be scannable without
            // making five stacked cards shout at once. Its corner radius mirrors the card's own so
            // it reads as part of the same shape rather than a rectangle stuck on top.
            Box(
                modifier = Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent, RoundedCornerShape(topStart = CardCornerDp, bottomStart = CardCornerDp)),
            )
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(point.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(point.body, style = MaterialTheme.typography.bodyMedium, lineHeight = 20.sp)
            }
        }
    }
}

@Composable
private fun NoCoachingCard(reason: String?) {
    Card(shape = CardCorner, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
        "fair" -> MaterialTheme.colorScheme.primary
        else -> BlushPink
    }
    Card(
        shape = HeroCardCorner,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Readiness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // The score as a tinted badge rather than bare digits: the same circular, low-alpha
            // accent treatment already used for session-type icons elsewhere in the app, so this
            // reads as the screen's headline number instead of another line of text.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        readiness.score?.toString() ?: "—",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(readiness.label.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    readiness.reason?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            // Stated, not enforced: a score off two signals and one off five read identically
            // otherwise, and which it is changes how much it is worth.
            val signals = readiness.componentCount
            val confidence = readiness.confidence
            val hasMeta = signals != null || confidence != null
            if (hasMeta || coverage != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            }
            if (hasMeta) {
                Text(
                    listOfNotNull(
                        signals?.let { "$it signals" },
                        confidence?.let { "$it confidence" },
                        readiness.date?.let { "scored $it" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            coverage?.let {
                Text(
                    "Last ${it.windowDays} days: ${it.hrvNights} HRV nights · " +
                        "${it.sleepNights} sleep · ${it.rhrDays} RHR",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun HrvCard(hrv: CoachPayload.HrvMetrics, milestone: CoachPayload.PlanStatus.Milestone?) {
    Card(shape = CardCorner, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "HRV & recovery",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

            Column(modifier = Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            val trend = listOfNotNull(
                hrv.trend14d?.takeIf { it != "unknown" }?.let { "14d $it" },
                hrv.trend30d?.takeIf { it != "unknown" }?.let { "30d $it" },
            )
            if (trend.isNotEmpty()) {
                Text(
                    trend.joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
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
                color = MaterialTheme.colorScheme.primary,
            )
            if (unit.isNotEmpty()) {
                Text(" $unit", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            target?.let {
                Text(
                    "  → ${formatMetric(it)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
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
        shape = CardCorner,
        // A blush tint rather than the neutral surfaceVariant used elsewhere on the screen: flags
        // are the one card that wants to read as "pay attention", not as routine detail.
        colors = CardDefaults.cardColors(containerColor = BlushPink.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Flags", style = MaterialTheme.typography.labelLarge, color = BlushPink, fontWeight = FontWeight.SemiBold)
            warnings.forEach { warning ->
                Text("• $warning", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
