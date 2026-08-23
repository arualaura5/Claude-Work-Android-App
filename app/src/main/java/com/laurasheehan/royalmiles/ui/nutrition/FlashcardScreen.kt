package com.laurasheehan.royalmiles.ui.nutrition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.laurasheehan.royalmiles.core.education.NutritionFlashcard
import com.laurasheehan.royalmiles.core.education.NutritionFlashcards
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold
import com.laurasheehan.royalmiles.ui.theme.RoyalPurple
import com.laurasheehan.royalmiles.ui.theme.ShimmerSilverDim

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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Card ${index + 1} of ${cards.size}",
                style = MaterialTheme.typography.labelLarge,
                color = ShimmerSilverDim,
            )
            LinearProgressIndicator(
                progress = { (index + 1) / cards.size.toFloat() },
                color = ComebackGold,
                trackColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 24.dp),
            )

            Flashcard(
                card = cards[index],
                flipped = flipped,
                onTap = { flipped = !flipped },
                modifier = Modifier.weight(1f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                OutlinedButton(
                    onClick = {
                        if (index > 0) {
                            index--
                            flipped = false
                        }
                    },
                    enabled = index > 0,
                ) { Text("Previous") }

                TextButton(onClick = { flipped = !flipped }) {
                    Text(if (flipped) "Show question" else "Show answer")
                }

                OutlinedButton(
                    onClick = {
                        if (index < cards.lastIndex) {
                            index++
                            flipped = false
                        }
                    },
                    enabled = index < cards.lastIndex,
                ) { Text("Next") }
            }
        }
    }
}

@Composable
private fun Flashcard(card: NutritionFlashcard, flipped: Boolean, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onTap),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        if (flipped) listOf(RoyalPurple, BlushPink) else listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface),
                    ),
                )
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedContent(
                targetState = flipped,
                transitionSpec = { fadeIn(tween(200)) togetherWith fadeOut(tween(150)) },
                label = "flashcard-flip",
            ) { showAnswer ->
                if (showAnswer) {
                    Text(
                        card.answer,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White,
                    )
                } else {
                    Column {
                        Text(
                            "Tap to reveal",
                            style = MaterialTheme.typography.labelMedium,
                            color = ShimmerSilverDim,
                            fontStyle = FontStyle.Italic,
                        )
                        Text(
                            card.question,
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
        }
    }
}
