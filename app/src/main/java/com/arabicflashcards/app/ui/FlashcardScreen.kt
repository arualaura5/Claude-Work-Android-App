@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicflashcards.app.data.GameStats
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard
import kotlin.math.abs
import kotlinx.coroutines.delay

@Composable
fun FlashcardApp(viewModel: FlashcardViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Arabic Flashcards", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
                StatsRow(stats = state.stats)
            }
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Style, contentDescription = "Study") },
                    label = { Text("Study") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.ViewList, contentDescription = "My Cards") },
                    label = { Text("My Cards") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (selectedTab) {
                0 -> StudyScreen(
                    state = state,
                    onFlip = viewModel::flip,
                    onSwipeLeft = viewModel::next,
                    onSwipeRight = viewModel::previous,
                    onDirectionChange = viewModel::setDirection,
                    onGrade = viewModel::gradeCurrent,
                    onCelebrationDone = viewModel::consumeCelebration,
                    onGoToCards = { selectedTab = 1 }
                )
                else -> ManageCardsScreen(
                    cards = state.cards,
                    onAdd = viewModel::addCard,
                    onEdit = viewModel::updateCard,
                    onDelete = viewModel::deleteCard
                )
            }
        }
    }
}

@Composable
private fun StatsRow(stats: GameStats) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        StatChip(icon = Icons.Default.LocalFireDepartment, label = "${stats.currentStreak} day streak")
        StatChip(icon = Icons.Default.Star, label = "${stats.totalPoints} pts")
        Text(
            text = "Lv. ${stats.level}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.height(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StudyScreen(
    state: FlashcardUiState,
    onFlip: () -> Unit,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onDirectionChange: (StudyDirection) -> Unit,
    onGrade: (Boolean) -> Unit,
    onCelebrationDone: () -> Unit,
    onGoToCards: () -> Unit
) {
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
                    modifier = Modifier.fillMaxSize()
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
                onClick = { onGrade(false) },
                enabled = state.isFlipped,
                modifier = Modifier.weight(1f)
            ) { Text("❌ Didn't know it") }
            Button(
                onClick = { onGrade(true) },
                enabled = state.isFlipped,
                modifier = Modifier.weight(1f)
            ) { Text("✅ Knew it!") }
        }
    }
}

@Composable
private fun CelebrationOverlay(show: Boolean, onDone: () -> Unit) {
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
private fun FlipStudyCard(
    card: UserCard,
    direction: StudyDirection,
    isFlipped: Boolean,
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

    val frontText = if (direction == StudyDirection.EGYPTIAN_FIRST) card.egyptian else card.english
    val frontLabel = if (direction == StudyDirection.EGYPTIAN_FIRST) "Egyptian Arabic" else "English"
    val backText = if (direction == StudyDirection.EGYPTIAN_FIRST) card.english else card.egyptian
    val backLabel = if (direction == StudyDirection.EGYPTIAN_FIRST) "English" else "Egyptian Arabic"

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
                containerColor = if (card.mastered) MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                else MaterialTheme.colorScheme.surface
            ),
            onClick = onFlip
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (rotation <= 90f) {
                    CardFace(primary = frontText, secondary = frontLabel, primarySize = 32.sp)
                } else {
                    Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                        CardFace(primary = backText, secondary = backLabel, primarySize = 32.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CardFace(primary: String, secondary: String, primarySize: TextUnit) {
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
            text = "Tap to flip",
            style = MaterialTheme.typography.labelLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun ManageCardsScreen(
    cards: List<UserCard>,
    onAdd: (String, String) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onDelete: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<UserCard?>(null) }
    var deletingCard by remember { mutableStateOf<UserCard?>(null) }

    Box(Modifier.fillMaxSize()) {
        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No cards yet — tap + to add one",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(cards, key = { it.id }) { card ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(card.english, fontWeight = FontWeight.Bold)
                                Text(
                                    card.egyptian,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                )
                            }
                            IconButton(onClick = { editingCard = card }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit")
                            }
                            IconButton(onClick = { deletingCard = card }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add card")
        }
    }

    if (showAddDialog) {
        CardEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { english, egyptian ->
                onAdd(english, egyptian)
                showAddDialog = false
            }
        )
    }

    editingCard?.let { card ->
        CardEditDialog(
            initial = card,
            onDismiss = { editingCard = null },
            onSave = { english, egyptian ->
                onEdit(card.id, english, egyptian)
                editingCard = null
            }
        )
    }

    deletingCard?.let { card ->
        AlertDialog(
            onDismissRequest = { deletingCard = null },
            title = { Text("Delete this card?") },
            text = { Text("\"${card.english}\" will be removed from your notebook.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(card.id)
                    deletingCard = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { deletingCard = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun CardEditDialog(
    initial: UserCard?,
    onDismiss: () -> Unit,
    onSave: (english: String, egyptian: String) -> Unit
) {
    var english by remember { mutableStateOf(initial?.english ?: "") }
    var egyptian by remember { mutableStateOf(initial?.egyptian ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a card" else "Edit card") },
        text = {
            Column {
                OutlinedTextField(
                    value = english,
                    onValueChange = { english = it },
                    label = { Text("English") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = egyptian,
                    onValueChange = { egyptian = it },
                    label = { Text("Egyptian Arabic") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(english, egyptian) },
                enabled = english.isNotBlank() && egyptian.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
