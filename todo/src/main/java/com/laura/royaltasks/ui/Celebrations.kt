package com.laura.royaltasks.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laura.royaltasks.data.titleFor
import com.laura.royaltasks.ui.theme.BlushPink
import com.laura.royaltasks.ui.theme.CloudLavender
import com.laura.royaltasks.ui.theme.ComebackGold
import com.laura.royaltasks.ui.theme.RoyalPurple
import com.laura.royaltasks.ui.theme.RoyalPurpleLight
import com.laura.royaltasks.ui.theme.tabular
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

val RoyalGradient = Brush.linearGradient(listOf(RoyalPurple, BlushPink))

private val ConfettiColors = listOf(RoyalPurple, RoyalPurpleLight, BlushPink, ComebackGold, CloudLavender)

data class Burst(val id: Long, val origin: Offset, val big: Boolean)
data class XpPop(val id: Long, val origin: Offset, val text: String)

private class Particle(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val size: Float,
    val color: Color,
    val isStrip: Boolean
)

@Composable
fun ConfettiLayer(bursts: List<Burst>, onFinished: (Long) -> Unit) {
    bursts.forEach { burst ->
        key(burst.id) { ConfettiBurst(burst, onFinished) }
    }
}

@Composable
private fun ConfettiBurst(burst: Burst, onFinished: (Long) -> Unit) {
    val density = LocalDensity.current.density
    val durationMs = if (burst.big) 2200 else 1100
    val particles = remember {
        List(if (burst.big) 110 else 34) {
            // Big bursts fan out in every direction; small ones spray mostly upward.
            val angle = if (burst.big) {
                Random.nextFloat() * 2f * Math.PI.toFloat()
            } else {
                (-Math.PI / 2).toFloat() + (Random.nextFloat() - 0.5f) * 2.4f
            }
            Particle(
                angle = angle,
                speed = (if (burst.big) 350f else 220f) + Random.nextFloat() * (if (burst.big) 650f else 380f),
                spin = (Random.nextFloat() - 0.5f) * 3f,
                size = 5f + Random.nextFloat() * 5f,
                color = ConfettiColors.random(),
                isStrip = Random.nextBoolean()
            )
        }
    }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMs, easing = LinearEasing))
        onFinished(burst.id)
    }
    Canvas(Modifier.fillMaxSize()) {
        val t = progress.value
        val seconds = t * durationMs / 1000f
        val gravity = 900f * density
        val alpha = (1f - t * t).coerceIn(0f, 1f)
        particles.forEach { p ->
            val drag = 1f / (1f + seconds * 1.6f)
            val vx = cos(p.angle) * p.speed * density
            val vy = sin(p.angle) * p.speed * density
            val x = burst.origin.x + vx * seconds * drag
            val y = burst.origin.y + vy * seconds * drag + 0.5f * gravity * seconds * seconds
            val w = p.size * density * (if (p.isStrip) 0.6f else 1f)
            val h = p.size * density * (if (p.isStrip) 1.8f else 1f)
            rotate(degrees = p.spin * seconds * 360f, pivot = Offset(x, y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(x - w / 2, y - h / 2),
                    size = Size(w, h),
                    alpha = alpha
                )
            }
        }
    }
}

@Composable
fun XpPopLayer(pops: List<XpPop>, onFinished: (Long) -> Unit) {
    pops.forEach { pop ->
        key(pop.id) { XpPopText(pop, onFinished) }
    }
}

@Composable
private fun XpPopText(pop: XpPop, onFinished: (Long) -> Unit) {
    val rise = remember { Animatable(0f) }
    val density = LocalDensity.current
    LaunchedEffect(Unit) {
        rise.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        onFinished(pop.id)
    }
    val t = rise.value
    Text(
        text = pop.text,
        style = MaterialTheme.typography.titleMedium.tabular(),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    val lift = with(density) { 56.dp.toPx() } * t
                    placeable.place(
                        x = (pop.origin.x + with(density) { 22.dp.toPx() }).roundToInt(),
                        y = (pop.origin.y - placeable.height / 2 - lift).roundToInt()
                    )
                }
            }
            .graphicsLayer {
                alpha = if (t < 0.6f) 1f else 1f - (t - 0.6f) / 0.4f
                val scale = 0.8f + 0.4f * (if (t < 0.25f) t / 0.25f else 1f)
                scaleX = scale
                scaleY = scale
            }
    )
}

/** Full-screen level-up moment, drawn in-window so confetti can fall over it. */
@Composable
fun LevelUpOverlay(level: Int, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    val pop = remember { Animatable(0.6f) }
    LaunchedEffect(Unit) {
        pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(RoundedCornerShape(28.dp))
                .background(RoyalGradient)
                // Swallow taps on the card itself so only the scrim dismisses.
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("👑", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = "LEVEL $level",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = Color.White.copy(alpha = 0.85f)
            )
            Text(
                text = titleFor(level),
                style = MaterialTheme.typography.displaySmall,
                fontStyle = FontStyle.Italic,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "New rank unlocked.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = RoyalPurple
                )
            ) {
                Text("Onwards", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
