package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.Canvas
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
fun WellbeingContent(ui: WellbeingUi, modifier: Modifier = Modifier) {
    var range by rememberSaveable { mutableStateOf(14) }
    Column(modifier = modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(ui.dateLine, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Brief(ui)
        // Flags are never folded away: a shorter page must not make a warning easier to miss.
        if (ui.warnings.isNotEmpty()) WarningsBox(ui.warnings)
        ui.training?.let { TrainingCard(it) }
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
internal fun TrainingCard(training: WellbeingUi.TrainingContext) {
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
private fun RhythmCard(rhythm: WellbeingUi.Rhythm, days: Int) {
    val dates = rhythm.dates.takeLast(days)
    SoftCard {
        TrendChart("HRV overnight", "ms", rhythm.hrv.takeLast(days), rhythm.hrvBand, dates, rhythm.runDays, line = true)
        TrendChart("Resting heart rate", "bpm", rhythm.rhr.takeLast(days), rhythm.rhrBand, dates, rhythm.runDays, line = true)
        TrendChart(
            "Sleep", "h", rhythm.sleepHours.takeLast(days),
            rhythm.sleepUsual?.let { it..it }, dates, rhythm.runDays, line = false,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(BlushPink))
            Text("Run days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(8.dp))
            Box(Modifier.width(14.dp).height(8.dp).background(RoyalPurple.copy(alpha = 0.15f)))
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
    dates: List<LocalDate>,
    runDays: Set<LocalDate>,
    line: Boolean,
) {
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val present = values.filterNotNull()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(title, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            values.lastOrNull()?.let { latest ->
                Text(
                    if (unit == "h") formatHours(latest) else "${latest.toInt()} $unit",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(if (line) 70.dp else 60.dp)) {
            if (present.isEmpty()) return@Canvas
            val low = if (line) minOf(present.min(), band?.start ?: present.min()) - 2f else 0f
            val high = maxOf(present.max(), band?.endInclusive ?: present.max()) + if (line) 2f else 1f
            fun y(v: Float) = size.height - (v - low) / (high - low) * size.height
            val step = if (values.size > 1) size.width / (values.size - 1) else size.width
            val accent = RoyalPurple
            if (band != null && band.endInclusive > band.start) {
                drawRect(accent.copy(alpha = 0.12f), Offset(0f, y(band.endInclusive)), Size(size.width, y(band.start) - y(band.endInclusive)))
            }
            if (line) {
                // A night with no reading is a gap, never a line drawn through it.
                val path = Path()
                var drawing = false
                values.forEachIndexed { i, v ->
                    if (v == null) {
                        drawing = false
                    } else {
                        if (drawing) path.lineTo(i * step, y(v)) else path.moveTo(i * step, y(v))
                        drawing = true
                    }
                }
                drawPath(path, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                values.forEachIndexed { i, v -> if (v != null) drawCircle(accent, 2.5.dp.toPx(), Offset(i * step, y(v))) }
            } else {
                val barStep = size.width / values.size
                val barWidth = barStep * 0.6f
                values.forEachIndexed { i, v ->
                    if (v != null) {
                        drawRoundRect(
                            accent.copy(alpha = 0.75f),
                            Offset(i * barStep + (barStep - barWidth) / 2, y(v)),
                            Size(barWidth, size.height - y(v)),
                            CornerRadius(3.dp.toPx()),
                        )
                    }
                }
                band?.let { drawLine(BlushPink.copy(alpha = 0.7f), Offset(0f, y(it.start)), Offset(size.width, y(it.start)), 1.5.dp.toPx()) }
            }
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(8.dp)) {
            val step = if (dates.size > 1) size.width / (dates.size - 1) else size.width
            dates.forEachIndexed { i, d ->
                if (d in runDays) {
                    val x = if (line) i * step else (i + 0.5f) * size.width / dates.size
                    drawCircle(BlushPink, 3.dp.toPx(), Offset(x, size.height / 2))
                }
            }
        }
        Row {
            Text(dates.firstOrNull()?.format(shortDate).orEmpty(), style = MaterialTheme.typography.labelSmall, color = ink, modifier = Modifier.weight(1f))
            Text(dates.lastOrNull()?.format(shortDate).orEmpty(), style = MaterialTheme.typography.labelSmall, color = ink)
        }
    }
}

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

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
