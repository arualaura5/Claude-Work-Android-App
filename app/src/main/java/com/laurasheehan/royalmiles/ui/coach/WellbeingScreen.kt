package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleLight
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.remember
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.roundToInt
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The Coach tab as a calm morning briefing (design reviewed with Codex): what her body seems to be
 * saying, what it means for her next session, the evidence, and the coach's words last. Personal
 * ranges ("normal for you") instead of grades; no streaks, targets or calorie counts.
 *
 * Every sentence in the coach's voice comes from the coach (brief, session check, suggestion,
 * action points). What the app writes itself is only plain description of her numbers.
 */
data class WellbeingUi(
    val dateLine: String,
    val greeting: String,
    val headline: String,
    val detail: String?,
    val recommendation: String?,
    val readinessScore: Int?,
    val readinessLabel: String?,
    val basis: String?,
    val warnings: List<String>,
    val training: TrainingContext?,
    val signals: List<Signal>,
    val rhythm: Rhythm,
    val sleep: SleepDetail?,
    val coachTake: List<CoachPoint>,
    val coachAbsent: String?,
    val fullCoaching: String?,
    val basedOn: List<Pair<String, String>>,
    val moreContext: List<Pair<String, String>>,
) {
    data class TrainingContext(
        val whenLabel: String,
        val session: String,
        val verdict: String? = null,
        val verdictTone: Tone = Tone.NORMAL,
        val reason: String? = null,
        val reasonDetail: String? = null,
        val zone: String? = null,
        val zoneDetail: String? = null,
        val note: String? = null,
        /** The plan row shown, so "Send to watch" confirms exactly this session. */
        val sessionId: Long? = null,
        /** A run with a distance or time: something a watch workout can be built from. */
        val canSendToWatch: Boolean = false,
    )

    /** One metric that matters today, against her usual. */
    data class Signal(
        val name: String,
        val value: String,
        val note: String,
        val explain: String,
        val series: List<Float>,
        val band: ClosedFloatingPointRange<Float>?,
        val tone: Tone,
    )

    /** Violet for normal variation, amber for worth noticing, pink only for a meaningful flag. */
    enum class Tone { NORMAL, NOTICE, FLAG }

    data class Rhythm(
        val dates: List<LocalDate>,
        val hrv: List<Float?>,
        val hrvBand: ClosedFloatingPointRange<Float>?,
        val rhr: List<Float?>,
        val rhrBand: ClosedFloatingPointRange<Float>?,
        val sleepHours: List<Float?>,
        val sleepUsual: Float?,
        val sleepBand: ClosedFloatingPointRange<Float>? = null,
        val runDays: Set<LocalDate>,
    )

    data class SleepDetail(
        val lastNight: String,
        val note: String?,
        val nights: List<Pair<LocalTime, LocalTime>>,
        val restingHr: String?,
        val minimumHr: String?,
        val consistency: String?,
    )

    data class CoachPoint(val title: String, val body: String)
}

private fun toneColor(tone: WellbeingUi.Tone): Color = when (tone) {
    WellbeingUi.Tone.NORMAL -> RoyalPurple
    WellbeingUi.Tone.NOTICE -> ComebackGold
    WellbeingUi.Tone.FLAG -> BlushPink
}

@Composable
fun WellbeingContent(
    ui: WellbeingUi,
    modifier: Modifier = Modifier,
    watch: WatchUi? = null,
    onSendToWatch: (Long) -> Unit = {},
    onTakeOffWatch: (Long) -> Unit = {},
) {
    var range by rememberSaveable { mutableStateOf(14) }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(ui.dateLine, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Brief(ui)
        // Flags are never folded away: a shorter page must not make a warning easier to miss.
        if (ui.warnings.isNotEmpty()) WarningsBox(ui.warnings)
        ui.training?.let { TrainingCard(it, watch, onSendToWatch, onTakeOffWatch) }
        if (ui.signals.isNotEmpty()) {
            SectionTitle("What changed")
            ui.signals.forEach { SignalRow(it) }
        }
        if (ui.rhythm.dates.isNotEmpty()) {
            SectionTitle("Your rhythm")
            RangeChips(range) { range = it }
            RhythmCard(ui.rhythm, range)
        }
        ui.sleep?.let {
            SectionTitle("Sleep and recovery")
            SleepCard(it)
        }
        SectionTitle("Coach's take")
        CoachTakeCard(ui)
        if (ui.moreContext.isNotEmpty()) MoreContext(ui.moreContext)
    }
}

// ── the brief ────────────────────────────────────────────────────────────────

@Composable
private fun Brief(ui: WellbeingUi) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Ring beside the headline rather than above it, so the whole brief fits on one screen.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                ui.readinessScore?.let { ScoreRing(score = it, label = ui.readinessLabel) }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.weight(1f)) {
                    Text(ui.greeting, style = MaterialTheme.typography.labelMedium, color = ink.copy(alpha = 0.8f))
                    Text(ui.headline, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink)
                }
            }
            ui.detail?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = ink) }
            ui.recommendation?.let { Recommendation(it) }
            ui.basis?.let { Pill(it) }
        }
    }
}

@Composable
private fun ScoreRing(score: Int, label: String?) {
    val track = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(96.dp)) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val stroke = 8.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 135f, 270f, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(
                Brush.sweepGradient(listOf(RoyalPurple, BlushPink, RoyalPurple)),
                135f, 270f * score.coerceIn(0, 100) / 100f, false, Offset(inset, inset), arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontSize = 30.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, color = ink)
            label?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = ink.copy(alpha = 0.8f)) }
        }
    }
}

@Composable
private fun Recommendation(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.AutoMirrored.Filled.DirectionsRun, contentDescription = null, tint = RoyalPurple, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun WarningsBox(warnings: List<String>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BlushPink.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        warnings.forEach { warning ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = BlushPink, modifier = Modifier.size(18.dp))
                Text(warning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

// ── her next session ─────────────────────────────────────────────────────────

@Composable
internal fun TrainingCard(
    training: WellbeingUi.TrainingContext,
    watch: WatchUi? = null,
    onSendToWatch: (Long) -> Unit = {},
    onTakeOffWatch: (Long) -> Unit = {},
) {
    SoftCard {
        Text(training.whenLabel.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(training.session, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            training.verdict?.let { verdict ->
                val tone = toneColor(training.verdictTone)
                Text(
                    verdict,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (training.verdictTone == WellbeingUi.Tone.NOTICE) MaterialTheme.colorScheme.onSurface else tone,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(tone.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        training.reason?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        training.reasonDetail?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        training.zone?.let { zone ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    zone,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(RoyalPurple).padding(horizontal = 10.dp, vertical = 4.dp),
                )
                training.zoneDetail?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val note = training.note
        if (note != null) {
            Text(
                note,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(10.dp),
            )
        }
        val sessionId = training.sessionId
        if (watch != null && sessionId != null) WatchRow(watch, { onSendToWatch(sessionId) }, { onTakeOffWatch(sessionId) })
    }
}

/** Nothing goes to her watch without this tap; once sent, it says so and can be taken back off. */
@Composable
private fun WatchRow(watch: WatchUi, onSend: () -> Unit, onTakeOff: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        when (watch.status) {
            WatchUi.Status.SENT -> {
                Icon(Icons.Filled.Watch, contentDescription = null, tint = RoyalPurple, modifier = Modifier.size(18.dp))
                Text("On your watch", style = MaterialTheme.typography.labelLarge, color = RoyalPurple, modifier = Modifier.weight(1f))
                TextButton(onClick = onTakeOff, enabled = !watch.busy) { Text("Take off") }
            }
            WatchUi.Status.NOT_SENT, WatchUi.Status.CHANGED_SINCE_SENT -> {
                if (watch.status == WatchUi.Status.CHANGED_SINCE_SENT) {
                    Text(
                        "Changed since you sent it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedButton(onClick = onSend, enabled = !watch.busy) {
                    Icon(Icons.Filled.Watch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when {
                            watch.busy -> "Sending…"
                            watch.status == WatchUi.Status.CHANGED_SINCE_SENT -> "Send this version"
                            else -> "Send to watch"
                        },
                    )
                }
            }
        }
    }
    watch.message?.let {
        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ── signals ──────────────────────────────────────────────────────────────────

@Composable
private fun SignalRow(signal: WellbeingUi.Signal) {
    val tone = toneColor(signal.tone)
    var open by rememberSaveable(signal.name) { mutableStateOf(false) }
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(tone))
                    Text(signal.name, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "What this measures",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(signal.value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(signal.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Sparkline(signal.series, signal.band, tone, modifier = Modifier.width(110.dp).height(48.dp))
        }
        if (open) {
            Text(signal.explain, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Sparkline(values: List<Float>, band: ClosedFloatingPointRange<Float>?, color: Color, modifier: Modifier) {
    val bandColor = color.copy(alpha = 0.12f)
    Canvas(modifier = modifier) {
        if (values.size < 2) return@Canvas
        val low = minOf(values.min(), band?.start ?: values.min()) - 1f
        val high = maxOf(values.max(), band?.endInclusive ?: values.max()) + 1f
        fun y(v: Float) = size.height - (v - low) / (high - low) * size.height
        band?.let { drawRect(bandColor, topLeft = Offset(0f, y(it.endInclusive)), size = Size(size.width, y(it.start) - y(it.endInclusive))) }
        val step = size.width / (values.size - 1)
        val path = Path().apply {
            values.forEachIndexed { i, v -> if (i == 0) moveTo(0f, y(v)) else lineTo(i * step, y(v)) }
        }
        drawPath(path, color, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        drawCircle(color, 3.5.dp.toPx(), Offset(size.width, y(values.last())))
    }
}

// ── rhythm ───────────────────────────────────────────────────────────────────

@Composable
private fun RangeChips(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(7, 14, 30).forEach { days ->
            val on = days == selected
            Text(
                "$days days",
                style = MaterialTheme.typography.labelLarge,
                color = if (on) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) RoyalPurple else Color.Transparent)
                    .clickable { onSelect(days) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
internal fun RhythmCard(
    rhythm: WellbeingUi.Rhythm,
    days: Int,
    initialSelection: Int? = null,
) {
    val dates = rhythm.dates.takeLast(days)
    // Opens on last night; a tapped day is highlighted on every chart.
    var selected by remember(days) { mutableStateOf((initialSelection ?: dates.lastIndex).coerceIn(0, dates.lastIndex)) }
    val select: (Int) -> Unit = { selected = it }
    SoftCard {
        TrendChart("HRV overnight", "ms", rhythm.hrv.takeLast(days), rhythm.hrvBand, null, dates, rhythm.runDays, line = true, selected, select)
        TrendChart("Resting heart rate", "bpm", rhythm.rhr.takeLast(days), rhythm.rhrBand, null, dates, rhythm.runDays, line = true, selected, select)
        TrendChart(
            "Sleep", "h", rhythm.sleepHours.takeLast(days), rhythm.sleepBand, rhythm.sleepUsual,
            dates, rhythm.runDays, line = false, selected, select,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(BlushPink))
            Text("Run days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(14.dp).height(8.dp).background(RoyalPurple.copy(alpha = 0.3f)))
            Text("Your usual range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TrendChart(
    title: String,
    unit: String,
    values: List<Float?>,
    band: ClosedFloatingPointRange<Float>?,
    usual: Float?,
    dates: List<LocalDate>,
    runDays: Set<LocalDate>,
    line: Boolean,
    selected: Int?,
    onSelect: (Int) -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)
    val measurer = rememberTextMeasurer()
    val scaleStyle = MaterialTheme.typography.labelSmall.copy(color = ink)
    val present = values.filterNotNull()
    fun number(v: Float) = "${v.roundToInt()}"
    // Room on the left for the value scale.
    val gutter = if (unit == "h") 30.dp else 28.dp
    fun withUnit(v: Float) = if (unit == "h") formatHours(v) else "${v.roundToInt()} $unit"
    fun rangeText(r: ClosedFloatingPointRange<Float>) =
        if (unit == "h") "${formatHours(r.start)} to ${formatHours(r.endInclusive)}" else "${number(r.start)}–${withUnit(r.endInclusive)}"


    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            values.lastOrNull()?.let { latest ->
                Text(withUnit(latest), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }
        if (band != null) {
            Text("Usual ${rangeText(band)}", style = MaterialTheme.typography.labelMedium, color = ink)
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (line) 84.dp else 70.dp)
                .pointerInput(values) {
                    detectTapGestures { tap ->
                        if (values.isEmpty()) return@detectTapGestures
                        val left = gutter.toPx()
                        val fraction = ((tap.x - left) / (size.width - left)).coerceIn(0f, 1f)
                        onSelect(
                            if (line) (fraction * (values.size - 1)).roundToInt()
                            else (fraction * values.size).toInt().coerceAtMost(values.lastIndex),
                        )
                    }
                },
        ) {
            if (present.isEmpty()) return@Canvas
            val left = gutter.toPx()
            val plotWidth = size.width - left
            val top = 6.dp.toPx()
            val plotHeight = size.height - top * 2
            val low = if (line) minOf(present.min(), band?.start ?: present.min()) - 2f
            else maxOf(0f, kotlin.math.floor(minOf(present.min(), band?.start ?: present.min())) - 1f)
            val high = maxOf(present.max(), band?.endInclusive ?: present.max()) + if (line) 2f else 1f
            fun y(v: Float) = top + plotHeight - (v - low) / (high - low) * plotHeight
            val step = if (values.size > 1) plotWidth / (values.size - 1) else plotWidth
            val barStep = plotWidth / values.size
            fun x(i: Int) = left + if (line) i * step else (i + 0.5f) * barStep
            val accent = RoyalPurple

            // The value scale: a few round values with faint gridlines.
            niceTicks(low, high).forEach { tick ->
                drawLine(grid, Offset(left, y(tick)), Offset(size.width, y(tick)), 1.dp.toPx())
                val label = measurer.measure(if (unit == "h") shortHours(tick) else number(tick), scaleStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), y(tick) - label.size.height / 2f))
            }
            if (band != null && band.endInclusive > band.start) {
                drawRect(accent.copy(alpha = 0.22f), Offset(left, y(band.endInclusive)), Size(plotWidth, y(band.start) - y(band.endInclusive)))
                val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
                listOf(band.start, band.endInclusive).forEach {
                    drawLine(RoyalPurpleLight, Offset(left, y(it)), Offset(size.width, y(it)), 1.dp.toPx(), pathEffect = dash)
                }
            }

            selected?.let { i ->
                drawLine(ink.copy(alpha = 0.5f), Offset(x(i), 0f), Offset(x(i), size.height), 1.dp.toPx())
            }

            if (line) {
                // A night with no reading is a gap, never a line drawn through it.
                val path = Path()
                var drawing = false
                values.forEachIndexed { i, v ->
                    if (v == null) {
                        drawing = false
                    } else {
                        if (drawing) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
                        drawing = true
                    }
                }
                drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v ->
                    if (v != null) drawCircle(if (i == selected) BlushPink else accent, if (i == selected) 5.dp.toPx() else 2.5.dp.toPx(), Offset(x(i), y(v)))
                }
            } else {
                val barWidth = barStep * 0.6f
                values.forEachIndexed { i, v ->
                    if (v != null) {
                        drawRoundRect(
                            if (i == selected) BlushPink else accent.copy(alpha = 0.75f),
                            Offset(x(i) - barWidth / 2, y(v)),
                            Size(barWidth, top + plotHeight - y(v)),
                            CornerRadius(3.dp.toPx()),
                        )
                    }
                }
                usual?.let { drawLine(BlushPink.copy(alpha = 0.7f), Offset(left, y(it)), Offset(left + plotWidth, y(it)), 1.5.dp.toPx()) }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val left = gutter.toPx()
            val plotWidth = size.width - left
            dates.forEachIndexed { i, d ->
                if (d in runDays) {
                    val x = left + if (line) (if (dates.size > 1) i * plotWidth / (dates.size - 1) else 0f) else (i + 0.5f) * plotWidth / dates.size
                    drawCircle(BlushPink, 3.dp.toPx(), Offset(x, size.height / 2))
                }
            }
        }
        Row(modifier = Modifier.padding(start = gutter)) {
            Text(dates.firstOrNull()?.format(shortDate).orEmpty(), style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
            Text(dates.lastOrNull()?.format(shortDate).orEmpty(), style = MaterialTheme.typography.labelSmall, color = ink)
        }
        run {
            val i = selected
            val value = i?.let { values.getOrNull(it) }
            val text = when {
                i == null -> "Tap a day to see it against your usual range."
                value == null -> "${dates[i].format(dayLabel)}: no reading"
                band == null -> "${dates[i].format(dayLabel)}: ${withUnit(value)}"
                else -> "${dates[i].format(dayLabel)}: ${withUnit(value)}, " + when {
                    value < band.start -> "below your usual ${rangeText(band)}"
                    value > band.endInclusive -> "above your usual ${rangeText(band)}"
                    else -> "inside your usual ${rangeText(band)}"
                }
            }
            Text(
                text,
                style = MaterialTheme.typography.labelMedium,
                color = if (i == null) ink else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

private val dayLabel = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH)

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Three round values spanning the chart, for its scale. */
internal fun niceTicks(low: Float, high: Float): List<Float> {
    val span = high - low
    if (span <= 0f) return listOf(low)
    val step = listOf(0.5f, 1f, 2f, 5f, 10f, 20f).firstOrNull { it >= span / 3f } ?: 50f
    val first = kotlin.math.ceil(low / step) * step
    return generateSequence(first) { it + step }.takeWhile { it <= high }.toList()
}

/** "10h", "7h30": hours short enough for the scale. */
private fun shortHours(hours: Float): String {
    val total = Math.round(hours * 60)
    return if (total % 60 == 0) "${total / 60}h" else "${total / 60}h${"%02d".format(total % 60)}"
}

internal fun formatHours(hours: Float): String {
    val total = Math.round(hours * 60)
    return "${total / 60} h ${"%02d".format(total % 60)}"
}

// ── sleep ────────────────────────────────────────────────────────────────────

@Composable
private fun SleepCard(sleep: WellbeingUi.SleepDetail) {
    SoftCard {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Last night", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(sleep.lastNight, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                sleep.restingHr?.let { Text("Resting $it", style = MaterialTheme.typography.bodySmall) }
                sleep.minimumHr?.let { Text("Lowest $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        sleep.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (sleep.nights.isNotEmpty()) {
            Text("When you slept, last ${sleep.nights.size} nights", style = MaterialTheme.typography.labelLarge)
            SleepWindows(sleep.nights)
        }
        sleep.consistency?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * A night as fractions of the 21:00 to 10:00 axis. A bedtime before 21:00 starts at the left edge
 * and a wake after 10:00 runs to the right edge, rather than wrapping round. Null if nothing shows.
 */
internal fun sleepWindow(start: LocalTime, end: LocalTime): Pair<Float, Float>? {
    val span = 13 * 60
    fun position(t: LocalTime, isStart: Boolean): Float {
        val minutes = ((t.hour * 60 + t.minute - 21 * 60) + 24 * 60) % (24 * 60)
        // 10:00 to 21:00 is off the axis: before it for a bedtime, after it for a wake.
        if (minutes > span) return if (isStart) 0f else 1f
        return minutes / span.toFloat()
    }
    val from = position(start, isStart = true)
    val to = position(end, isStart = false)
    return if (to > from) from to to else null
}

/** Each night from falling asleep to waking, on a 21:00 to 10:00 axis. */
@Composable
private fun SleepWindows(nights: List<Pair<LocalTime, LocalTime>>) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val grid = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(modifier = Modifier.fillMaxWidth().height((nights.size * 9).dp)) {
            listOf(0, 3, 6, 9, 12).forEach { h -> drawLine(grid, Offset(h / 13f * size.width, 0f), Offset(h / 13f * size.width, size.height), 1.dp.toPx()) }
            val row = size.height / nights.size
            nights.forEachIndexed { i, (start, end) ->
                val (from, to) = sleepWindow(start, end) ?: return@forEachIndexed
                drawRoundRect(
                    RoyalPurple.copy(alpha = if (i == nights.lastIndex) 1f else 0.55f),
                    Offset(from * size.width, i * row + row * 0.2f),
                    Size((to - from) * size.width, row * 0.6f),
                    CornerRadius(row),
                )
            }
        }
        Row {
            listOf("21:00", "00:00", "03:00", "06:00", "09:00").forEach {
                Text(it, style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
            }
        }
    }
}

// ── coach and context ────────────────────────────────────────────────────────

@Composable
private fun CoachTakeCard(ui: WellbeingUi) {
    var showBasis by rememberSaveable { mutableStateOf(false) }
    var showFull by rememberSaveable { mutableStateOf(false) }
    SoftCard {
        if (ui.coachTake.isEmpty()) {
            Text(
                ui.coachAbsent ?: "No coaching for today's data yet.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        ui.coachTake.forEach { point ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 7.dp).size(6.dp).clip(CircleShape).background(RoyalPurple))
                Column {
                    Text(point.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text(point.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        if (showBasis) {
            ui.basedOn.forEach { (name, detail) ->
                Column {
                    Text(name, style = MaterialTheme.typography.labelLarge)
                    Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (showFull) ui.fullCoaching?.let { MarkdownText(it) }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (ui.basedOn.isNotEmpty()) {
                TextButton(onClick = { showBasis = !showBasis }) { Text(if (showBasis) "Hide the basis" else "What this is based on") }
            }
            if (ui.fullCoaching != null) {
                TextButton(onClick = { showFull = !showFull }) { Text(if (showFull) "Show less" else "Read the full coaching") }
            }
        }
    }
}

@Composable
private fun MoreContext(items: List<Pair<String, String>>) {
    var open by rememberSaveable { mutableStateOf(false) }
    SoftCard(onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("More context", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(items.joinToString(" · ") { it.first }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = if (open) "Hide" else "Show more context")
        }
        if (open) {
            items.forEach { (name, value) ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(name, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun SoftCard(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
    }
}
