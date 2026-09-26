package com.laurasheehan.royalmiles.ui.coach

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun CoachScreen(viewModel: CoachViewModel, onOpenChat: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // OpenDocument rather than GetContent: only OpenDocument returns a URI that can be given
    // persistable read permission, which is what lets Refresh re-read the file later.
    val picker = rememberLauncherForActivityResult(
        contract = remember { ActivityResultContracts.OpenDocument() },
        onResult = { uri -> uri?.let(viewModel::import) },
    )

    var showConnect by remember { mutableStateOf(false) }

    state.error?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("OK") } },
            title = { Text("Coach update failed") },
            text = { Text(message) },
        )
    }

    if (showConnect) {
        ConnectDialog(
            onDismiss = { showConnect = false },
            onConnect = { address, key ->
                showConnect = false
                viewModel.connect(address, key)
            },
        )
    }

    CoachContent(
        state = state,
        onRefresh = viewModel::refresh,
        onConnect = { showConnect = true },
        onPick = { picker.launch(arrayOf("application/json", "*/*")) },
        onOpenChat = onOpenChat,
    )
}

/** Stateless, so it can be rendered in screenshot tests with sample data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CoachContent(
    state: CoachUiState,
    onRefresh: () -> Unit,
    onConnect: () -> Unit,
    onPick: () -> Unit,
    onOpenChat: () -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Coach") },
                actions = {
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (state.sourceRemembered) {
                            // The spinner takes the icon's own place rather than sitting beside it
                            // — the button still reads as "the refresh control", just mid-errand,
                            // instead of adding a second thing competing for attention next to it.
                            IconButton(onClick = onRefresh, enabled = !state.loading) {
                                Crossfade(targetState = state.loading, label = "refreshIcon") { isLoading ->
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    } else {
                                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh coach data")
                                    }
                                }
                            }
                        }
                        IconButton(onClick = onConnect, enabled = !state.loading) {
                            Icon(Icons.Filled.Cloud, contentDescription = "Connect to the cloud coach")
                        }
                        // Tonal rather than plain: this is the one action that works with nothing
                        // loaded yet, so it should read as a small button, not just another icon
                        // lined up next to Refresh.
                        FilledTonalIconButton(onClick = onPick, enabled = !state.loading) {
                            Icon(Icons.Filled.FileOpen, contentDescription = "Import coach.json")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (state.payload != null) SpeakToCoachBar(onOpenChat)
        },
    ) { padding ->
        val payload = state.payload

        if (payload == null) {
            EmptyState(
                loading = state.loading,
                onPick = onPick,
                onConnect = onConnect,
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

            // Readiness leads: it's the single number everything else on the screen explains or
            // acts on, so it reads first rather than arriving after the verdict that depends on it.
            payload.readiness?.takeIf { it.available }?.let { readiness ->
                item { ReadinessCard(readiness, payload.coverage) }
            }

            // Flags sit straight under readiness and are never folded away: making the screen
            // shorter must not make a warning easier to miss.
            if (payload.warnings.isNotEmpty()) {
                item { WarningsCard(payload.warnings) }
            }

            // Explicit if/else rather than `?.let { } ?: item { }` — the let block's value would be
            // the trailing coachNote?.let, so a payload with coaching but no coach note returns null
            // and silently falls through to the "no coaching" card.
            val coaching = payload.coaching
            if (coaching != null) {
                item { CoachingSummaryCard(coaching) }
            } else {
                item { NoCoachingCard(payload.coachingAbsentReason) }
            }

            payload.hrv?.let { hrv ->
                item { HrvCard(hrv, payload.plan?.milestone) }
            }
        }
    }
}

@Composable
private fun SpeakToCoachBar(onOpenChat: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Button(
            onClick = onOpenChat,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .height(52.dp),
            shape = RoundedCornerShape(50),
        ) {
            Icon(Icons.Filled.Forum, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text("Speak to coach", style = MaterialTheme.typography.titleSmall)
        }
    }
}

@Composable
private fun EmptyState(
    loading: Boolean,
    onPick: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // The same circular tinted-icon language the readiness score and session cards use
        // elsewhere in the app — mirrors the Coach tab's own nav icon, so an empty screen still
        // reads as part of the same designed app rather than a bare placeholder.
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.20f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f),
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Insights,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }

        Crossfade(targetState = loading, label = "coachEmptyState") { isLoading ->
            if (isLoading) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .clip(RoundedCornerShape(50)),
                )
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "No coach data yet",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Garmin doesn't share HRV with Health Connect, so this comes from the cloud coach " +
                            "instead. Connect once with its address and key and it refreshes when Coach opens. " +
                            "You can still use Refresh or pick a coach.json file by hand.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                    Button(onClick = onConnect, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(
                            Icons.Filled.Cloud,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp),
                        )
                        Text("Connect to the cloud coach")
                    }
                    TextButton(onClick = onPick) {
                        Icon(
                            Icons.Filled.FileOpen,
                            contentDescription = null,
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 6.dp),
                        )
                        Text("Pick coach.json")
                    }
                }
            }
        }
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
private fun ReadinessCard(readiness: CoachPayload.Readiness, coverage: CoachPayload.Coverage?) {
    val rawAccent = when (readiness.status) {
        "good" -> ComebackGold
        "fair" -> MaterialTheme.colorScheme.primary
        else -> BlushPink
    }
    // Animated rather than a hard cut: if a refresh flips the status, the card should feel like a
    // change in weather, not a flicker.
    val accent by animateColorAsState(rawAccent, label = "readinessAccent")

    Card(
        shape = HeroCardCorner,
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                // A faint wash of the status colour behind the whole card, not just the badge —
                // this is the screen's headline number, so it earns more presence than a flat
                // surface the way a bare score-in-a-circle otherwise would.
                .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.10f), Color.Transparent)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Readiness", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // The score as a tinted badge rather than bare digits: the same circular, low-alpha
            // accent treatment already used for session-type icons elsewhere in the app, so this
            // reads as the screen's headline number instead of another line of text. Sized up from
            // the rest of the screen's numbers so it is unmistakably the first thing to read.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        readiness.score?.toString() ?: "—",
                        style = MaterialTheme.typography.displaySmall.copy(fontFeatureSettings = "tnum"),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(readiness.label.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    readiness.reason?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 20.sp,
                        )
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
                    style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            coverage?.let {
                Text(
                    "Last ${it.windowDays} days: ${it.hrvNights} HRV nights · " +
                        "${it.sleepNights} sleep · ${it.rhrDays} RHR",
                    style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CoachingSummaryCard(coaching: CoachPayload.Coaching) {
    val rawAccent = if (coaching.onTrack) ComebackGold else BlushPink
    val accent by animateColorAsState(rawAccent, label = "coachingAccent")
    var expanded by remember { mutableStateOf(false) }
    val hasMore = coaching.actionPoints.isNotEmpty() || (coaching.statusSummary?.length ?: 0) > 180
    Card(
        shape = HeroCardCorner,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            // Three lines by default: the gist first, the full paragraph on request.
            coaching.statusSummary?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 24.sp,
                    maxLines = if (expanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            coaching.keyReminder?.let { reminder ->
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp).padding(top = 2.dp),
                    )
                    Text(reminder, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                }
            }
            if (coaching.actionPoints.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        coaching.actionPoints.forEach { ActionPointCard(it) }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        coaching.actionPoints.forEach { ActionPointLine(it) }
                    }
                }
            }
            if (hasMore) {
                TextButton(onClick = { expanded = !expanded }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (expanded) "Show less" else "Read the full coaching")
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionPointLine(point: CoachPayload.Coaching.ActionPoint) {
    val color = priorityColor(point.priority.lowercase())
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(point.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun priorityColor(priority: String): Color = when (priority) {
    "critical" -> BlushPink
    "high" -> ComebackGold
    "medium" -> MaterialTheme.colorScheme.primary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun ActionPointCard(point: CoachPayload.Coaching.ActionPoint) {
    val priority = point.priority.lowercase()
    val rawAccent = priorityColor(priority)
    val accent by animateColorAsState(rawAccent, label = "actionPointAccent")
    // A second, redundant cue alongside colour for the two priorities that most want attention —
    // colour alone shouldn't be the only signal that something here is urgent.
    val priorityIcon = when (priority) {
        "critical" -> Icons.Filled.ErrorOutline
        "high" -> Icons.Filled.Warning
        else -> null
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    priorityIcon?.let {
                        Icon(it, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
                    }
                    Text(point.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
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
private fun HrvCard(hrv: CoachPayload.HrvMetrics, milestone: CoachPayload.PlanStatus.Milestone?) {
    var expanded by remember { mutableStateOf(false) }
    Card(shape = CardCorner, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "HRV & recovery",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        listOfNotNull(
                            hrv.hrv7d?.let { "HRV 7-day ${formatMetric(it)} ms" },
                            hrv.rhr?.let { "RHR ${formatMetric(it)} bpm" },
                            hrv.trend14d?.takeIf { it != "unknown" }?.let { "14d $it" },
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    )
                }
                Icon(
                    if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Hide HRV detail" else "Show HRV detail",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!expanded) return@Column
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                modifier = Modifier.padding(top = 6.dp),
            )

            Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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

            HrvTrendRow(hrv)
        }
    }
}

@Composable
private fun HrvTrendRow(hrv: CoachPayload.HrvMetrics) {
    val entries = listOfNotNull(
        hrv.trend14d?.takeIf { it != "unknown" }?.let { "14d" to it },
        hrv.trend30d?.takeIf { it != "unknown" }?.let { "30d" to it },
    )
    if (entries.isEmpty()) return
    Row(
        modifier = Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        entries.forEachIndexed { index, (window, direction) ->
            if (index > 0) {
                Text("·", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Direction is meaning, not decoration: an upward HRV trend is a genuinely different
            // fact from a downward one, and colour plus an arrow now say so, rather than both
            // reading in the same neutral grey they did before. The export actually sends words
            // ("improving"/"worsening"/"stable"), not "up"/"down" — matching only the latter left
            // every real value falling through to the neutral flat arrow, silently hiding the one
            // case (worsening) this was meant to call out.
            val (icon, tint) = when (direction.lowercase()) {
                "improving", "up" -> Icons.Filled.TrendingUp to MaterialTheme.colorScheme.primary
                "worsening", "down" -> Icons.Filled.TrendingDown to BlushPink
                else -> Icons.Filled.TrendingFlat to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text("$window $direction", style = MaterialTheme.typography.labelMedium, color = tint)
            }
        }
    }
}

// Fixed, start-aligned columns for the value and target — a row with no target still needs its
// number to line up with rows that have one, and every → should start at the same x rather than
// drifting with however wide the preceding number happens to be.
private val MetricValueWidth = 60.dp
private val MetricTargetWidth = 68.dp

@Composable
private fun MetricRow(label: String, value: Double?, unit: String, target: Double?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Label muted, value strong: the number is what's being scanned for, not its name.
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Row(modifier = Modifier.widthIn(min = MetricValueWidth), verticalAlignment = Alignment.CenterVertically) {
            Text(
                value?.let { formatMetric(it) } ?: "—",
                // Tabular figures so a column of these rows lines up digit-for-digit instead of
                // drifting with however wide each number happens to be.
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (unit.isNotEmpty()) {
                // Same size as everything else in the row — this used to run a step smaller and
                // read as an afterthought next to the number.
                Text(" $unit", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(modifier = Modifier.widthIn(min = MetricTargetWidth), verticalAlignment = Alignment.CenterVertically) {
            target?.let {
                Text(
                    // The target carries the same unit as the value it's a target for — "→ 67 ms",
                    // not just "→ 67" — and the app's own primary purple rather than the secondary
                    // gold, which read poorly on a light card.
                    "→ ${formatMetric(it)}${if (unit.isNotEmpty()) " $unit" else ""}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.primary,
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
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = BlushPink, modifier = Modifier.size(18.dp))
                Text("Flags", style = MaterialTheme.typography.labelLarge, color = BlushPink, fontWeight = FontWeight.SemiBold)
            }
            warnings.forEach { warning ->
                Text("• $warning", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ConnectDialog(onDismiss: () -> Unit, onConnect: (address: String, key: String) -> Unit) {
    var address by remember { mutableStateOf("") }
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect to the cloud coach") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the Worker's address and the coach key. The key can only read coaching, " +
                        "and it stays on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("https://….workers.dev") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Coach key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConnect(address, key) },
                enabled = address.isNotBlank() && key.isNotBlank(),
            ) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
