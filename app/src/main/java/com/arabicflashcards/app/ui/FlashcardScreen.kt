@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicflashcards.app.data.Category
import com.arabicflashcards.app.data.Flashcard
import kotlin.math.abs

@Composable
fun FlashcardScreen(viewModel: FlashcardViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Arabic Flashcards", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = { viewModel.shuffle() }) {
                        Icon(Icons.Default.Shuffle, contentDescription = "Shuffle deck", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { viewModel.resetProgress() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset progress", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            CategoryFilterRow(
                selected = state.selectedCategory,
                onSelect = { viewModel.selectCategory(it) }
            )

            Spacer(Modifier.height(12.dp))

            if (state.deck.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cards in this category yet.")
                }
                return@Column
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
                text = "Mastered in this set: ${state.masteredInDeck} / ${state.total}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(Modifier.height(24.dp))

            val card = state.currentCard
            if (card != null) {
                FlipCard(
                    card = card,
                    isFlipped = state.isFlipped,
                    isMastered = card.id in state.masteredIds,
                    onFlip = { viewModel.flip() },
                    onSwipeLeft = { viewModel.next() },
                    onSwipeRight = { viewModel.previous() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { viewModel.previous() },
                    modifier = Modifier.weight(1f)
                ) { Text("Previous") }
                Button(
                    onClick = { viewModel.next() },
                    modifier = Modifier.weight(1f)
                ) { Text("Next") }
            }

            Spacer(Modifier.height(12.dp))

            val currentMastered = state.currentCard?.id in state.masteredIds
            Button(
                onClick = { viewModel.toggleMastered() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (currentMastered) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                )
            ) {
                Text(if (currentMastered) "✓ Mastered — tap to undo" else "Mark as mastered")
            }
        }
    }
}

@Composable
private fun CategoryFilterRow(
    selected: Category?,
    onSelect: (Category?) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") }
            )
        }
        items(Category.entries) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = { Text(category.displayName) }
            )
        }
    }
}

@Composable
private fun FlipCard(
    card: Flashcard,
    isFlipped: Boolean,
    isMastered: Boolean,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(durationMillis = 350),
        label = "cardFlip"
    )

    Box(
        modifier = modifier
            .aspectRatio(0.8f)
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
                containerColor = if (isMastered) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            ),
            onClick = onFlip
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (rotation <= 90f) {
                    CardFace(
                        primary = card.english,
                        secondary = card.category.displayName,
                        primarySize = 28.sp
                    )
                } else {
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        CardFace(
                            primary = card.arabic,
                            secondary = card.transliteration,
                            primarySize = 40.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(primary: String, secondary: String, primarySize: androidx.compose.ui.unit.TextUnit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            text = primary,
            fontSize = primarySize,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = secondary,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = "Tap to flip · swipe for next/previous",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}
