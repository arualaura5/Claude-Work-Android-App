@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicflashcards.app.audio.LocalSoundPlayer
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard
import com.arabicflashcards.app.ui.theme.Tajawal
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
internal fun StudyScreen(
    state: FlashcardUiState,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDirectionChange: (StudyDirection) -> Unit,
    onGrade: (Boolean) -> Unit,
    onCelebrationDone: () -> Unit,
    onToggleTag: (String) -> Unit,
    onClearTagFilter: () -> Unit,
    onGoToCards: () -> Unit
) {
    val soundPlayer = LocalSoundPlayer.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = state.direction == StudyDirection.EGYPTIAN_FIRST,
                onClick = { onDirectionChange(StudyDirection.EGYPTIAN_FIRST) },
                label = { Text("🇪🇬 Egyptian → English") }
            )
            FilterChip(
                selected = state.direction == StudyDirection.ENGLISH_FIRST,
                onClick = { onDirectionChange(StudyDirection.ENGLISH_FIRST) },
                label = { Text("English → 🇪🇬 Egyptian") }
            )
        }

        if (state.allTags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.allTags.toList().sorted()) { tag ->
                    FilterChip(
                        selected = tag in state.selectedTagFilter,
                        onClick = { onToggleTag(tag) },
                        label = { Text(tag) }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        if (state.cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Your notebook is empty",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Add phrases as you come across them and they'll show up here for review.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onGoToCards) { Text("Add your first card") }
                }
            }
            return
        }

        if (state.studyDeck.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No cards match the selected tags",
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = onClearTagFilter) { Text("Clear tag filter") }
                }
            }
            return
        }

        Text(
            text = state.positionLabel,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        LinearProgressIndicator(
            progress = { (state.currentIndex + 1f) / state.total },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
        )

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Mastered: ${state.masteredCount} / ${state.total}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            val card = state.currentCard
            if (card != null) {
                FlipStudyCard(
                    card = card,
                    direction = state.direction,
                    isFlipped = state.isFlipped,
                    onFlip = onFlip,
                    onSwipeLeft = onSwipeLeft,
                    onSwipeRight = onSwipeRight,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            CelebrationOverlay(show = state.justScoredPoints, onDone = onCelebrationDone)
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onSwipeRight, modifier = Modifier.weight(1f)) { Text("‹ Prev") }
            OutlinedButton(onClick = onSwipeLeft, modifier = Modifier.weight(1f)) { Text("Next ›") }
        }

        Spacer(Modifier.height(12.dp))

        if (!state.isFlipped) {
            Text(
                "Flip the card to check your answer",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(8.dp))
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { soundPlayer.playIncorrect(); onGrade(false) },
                enabled = state.isFlipped,
                modifier = Modifier.weight(1f)
            ) { Text("❌ Didn't know it") }
            Button(
                onClick = { soundPlayer.playCorrect(); onGrade(true) },
                enabled = state.isFlipped,
                modifier = Modifier.weight(1f)
            ) { Text("✅ Knew it!") }
        }
    }
}

@Composable
internal fun CelebrationOverlay(show: Boolean, onDone: () -> Unit) {
    LaunchedEffect(show) {
        if (show) {
            delay(900)
            onDone()
        }
    }
    AnimatedVisibility(
        visible = show,
        enter = scaleIn(initialScale = 0.5f) + fadeIn(),
        exit = fadeOut(),
        modifier = Modifier.padding(top = 8.dp)
    ) {
        Text(
            text = "+10 ⭐",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary
        )
    }
}

@Composable
internal fun FlipStudyCard(
    card: UserCard,
    direction: StudyDirection,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val soundPlayer = LocalSoundPlayer.current
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "cardFlip"
    )

    val frontText = if (direction == StudyDirection.EGYPTIAN_FIRST) card.egyptian else card.english
    val frontLabel = if (direction == StudyDirection.EGYPTIAN_FIRST) "Egyptian Arabic" else "English"
    val backText = if (direction == StudyDirection.EGYPTIAN_FIRST) card.english else card.egyptian
    val backLabel = if (direction == StudyDirection.EGYPTIAN_FIRST) "English" else "Egyptian Arabic"

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                var totalDrag = 0f
                detectHorizontalDragGestures(
                    onDragStart = { totalDrag = 0f },
                    onHorizontalDrag = { _, dragAmount -> totalDrag += dragAmount },
                    onDragEnd = {
                        if (abs(totalDrag) > 120f) {
                            if (totalDrag < 0) onSwipeLeft() else onSwipeRight()
                        }
                    }
                )
            }
    ) {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (card.mastered) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            onClick = { soundPlayer.playFlip(); onFlip() }
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (rotation <= 90f) {
                    CardFace(primary = frontText, secondary = frontLabel, primarySize = fontSizeFor(frontText))
                } else {
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        CardFace(primary = backText, secondary = backLabel, primarySize = fontSizeFor(backText))
                    }
                }
            }
        }
    }
}

/**
 * Shrinks the flashcard's primary text as content grows, so long entries
 * (e.g. a full verb conjugation) fit on screen instead of overflowing and
 * pushing the label/hint text out of view.
 */
private fun fontSizeFor(text: String): TextUnit {
    val lines = text.count { it == '\n' } + 1
    return when {
        lines >= 6 || text.length > 150 -> 15.sp
        lines >= 4 || text.length > 80 -> 18.sp
        else -> 22.sp
    }
}

@Composable
private fun CardFace(primary: String, secondary: String, primarySize: TextUnit) {
    val isLong = primary.count { it == '\n' } + 1 >= 4 || primary.length > 80
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(if (isLong) 12.dp else 20.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = parseCardMarkup(primary, boldColor = MaterialTheme.colorScheme.tertiary),
            fontFamily = Tajawal,
            fontSize = primarySize,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            lineHeight = primarySize * 1.3f
        )
        Spacer(Modifier.height(if (isLong) 4.dp else 10.dp))
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        if (!isLong) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = "Tap to flip",
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}
