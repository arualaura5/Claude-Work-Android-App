package com.laurasheehan.royalmiles.ui.nutrition

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.laurasheehan.royalmiles.core.education.NutritionFlashcard
import com.laurasheehan.royalmiles.core.education.NutritionFlashcards
import com.laurasheehan.royalmiles.ui.components.shimmer
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.ComebackGoldSoft
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleDeep
import com.laurasheehan.royalmiles.ui.theme.RoyalPurpleLight

private val CardShape = RoundedCornerShape(28.dp)

/** Purple → pink → gold sweep, the app's celebration palette, used for deck progress. */
private val deckShimmerColors = listOf(RoyalPurple, BlushPink, ComebackGold, RoyalPurpleLight, RoyalPurple)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlashcardScreen(onDone: () -> Unit) {
    val cards = NutritionFlashcards.cards
    var index by remember { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learn: macros & performance") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DeckProgress(position = index + 1, total = cards.size)

            // The card sizes to its own content and floats in the middle of what's left, rather
            // than stretching to fill the screen with empty space around a short answer.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Flashcard(
                    card = cards[index],
                    flipped = flipped,
                    onTap = { flipped = !flipped },
                )
            }

            FlashcardControls(
                flipped = flipped,
                canGoBack = index > 0,
                canGoForward = index < cards.lastIndex,
                onPrevious = {
                    if (index > 0) {
                        index--
                        flipped = false
                    }
                },
                onFlip = { flipped = !flipped },
                onNext = {
                    if (index < cards.lastIndex) {
                        index++
                        flipped = false
                    }
                },
            )
        }
    }
}

/**
 * Position in the deck, never a score. A shimmering fill instead of a hairline track so it reads
 * as part of the app's jewellery rather than a stock Material progress bar.
 */
@Composable
private fun DeckProgress(position: Int, total: Int, modifier: Modifier = Modifier) {
    val target = (position.toFloat() / total.toFloat()).coerceIn(0.05f, 1f)
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 400),
        label = "deckProgress",
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Card $position of $total",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "Nutrition deck",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 1.5.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 20.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(10.dp)
                    .clip(RoundedCornerShape(50))
                    .shimmer(deckShimmerColors),
            )
        }
    }
}

/**
 * The question is the bright face — it's the prompt you're meant to sit with, so it gets the
 * purple-to-pink gradient, a gold eyebrow and a coloured shadow. The answer is deliberately
 * subdued: it's reading material, so it sits on the theme surface with a gold-to-pink edge and a
 * soft border, styled but quiet enough to actually read.
 */
@Composable
private fun Flashcard(card: NutritionFlashcard, flipped: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    val rotation by animateFloatAsState(
        targetValue = if (flipped) 180f else 0f,
        animationSpec = tween(durationMillis = 450),
        label = "cardFlip",
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            },
    ) {
        if (rotation > 90f) {
            // Counter-rotated so the text isn't mirrored once the card has turned past edge-on.
            AnswerFace(
                card = card,
                onTap = onTap,
                modifier = Modifier.graphicsLayer { rotationY = 180f },
            )
        } else {
            QuestionFace(card = card, onTap = onTap)
        }
    }
}

@Composable
private fun QuestionFace(card: NutritionFlashcard, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = 20.dp,
                shape = CardShape,
                ambientColor = RoyalPurpleDeep,
                spotColor = BlushPink,
            )
            .clip(CardShape)
            .background(Brush.linearGradient(listOf(RoyalPurpleDeep, RoyalPurple, BlushPink)))
            .clickable(onClick = onTap)
            .heightIn(min = 250.dp),
        contentAlignment = Alignment.Center,
    ) {
        // A soft top-left highlight, so the gradient reads as lit rather than flat.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.White.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(0f, 0f),
                        radius = 700f,
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 30.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "QUESTION",
                style = MaterialTheme.typography.labelSmall,
                color = ComebackGoldSoft,
                letterSpacing = 2.sp,
            )
            Text(
                card.question,
                style = MaterialTheme.typography.headlineSmall.copy(lineHeight = 32.sp),
                color = Color.White,
                modifier = Modifier.padding(top = 14.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 22.dp)
                    .height(3.dp)
                    .fillMaxWidth(0.35f)
                    .clip(RoundedCornerShape(50))
                    .background(Brush.horizontalGradient(listOf(ComebackGold, Color.Transparent))),
            )
            Text(
                "Tap the card to reveal the answer",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f),
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
private fun AnswerFace(card: NutritionFlashcard, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .shadow(elevation = 12.dp, shape = CardShape)
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(ComebackGold.copy(alpha = 0.55f), RoyalPurpleLight.copy(alpha = 0.35f)),
                ),
                shape = CardShape,
            )
            .clickable(onClick = onTap)
            .heightIn(min = 250.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 26.dp, vertical = 30.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                "ANSWER",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 2.sp,
            )
            // The question, restated small, so the answer still has its context after the flip.
            Text(
                card.question,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontStyle = FontStyle.Italic,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
            Box(
                modifier = Modifier
                    .padding(top = 16.dp, bottom = 16.dp)
                    .height(1.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f)),
            )
            Text(
                card.answer,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 26.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        // A thin gold-to-pink strip along the top edge: the one bit of jewellery the calm face gets.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(5.dp)
                .background(Brush.horizontalGradient(listOf(ComebackGold, BlushPink, RoyalPurple))),
        )
    }
}

@Composable
private fun FlashcardControls(
    flipped: Boolean,
    canGoBack: Boolean,
    canGoForward: Boolean,
    onPrevious: () -> Unit,
    onFlip: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 22.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavCircle(
            icon = Icons.Filled.ArrowBack,
            contentDescription = "Previous card",
            enabled = canGoBack,
            onClick = onPrevious,
        )
        FlipPill(
            label = if (flipped) "Show question" else "Show answer",
            onClick = onFlip,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp),
        )
        NavCircle(
            icon = Icons.Filled.ArrowForward,
            contentDescription = "Next card",
            enabled = canGoForward,
            onClick = onNext,
        )
    }
}

@Composable
private fun NavCircle(
    icon: ImageVector,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fillModifier = if (enabled) {
        Modifier.background(Brush.linearGradient(listOf(RoyalPurple, RoyalPurpleDeep)))
    } else {
        Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
    }
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(CircleShape)
            .then(fillModifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun FlipPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(Brush.horizontalGradient(listOf(RoyalPurple, BlushPink)))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Refresh,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
