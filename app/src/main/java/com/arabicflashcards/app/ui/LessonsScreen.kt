@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arabicflashcards.app.audio.LocalSoundPlayer
import com.arabicflashcards.app.data.Lesson
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard
import com.arabicflashcards.app.ui.theme.Cream
import com.arabicflashcards.app.ui.theme.Gold
import com.arabicflashcards.app.ui.theme.GoldLight
import com.arabicflashcards.app.ui.theme.Nile
import com.arabicflashcards.app.ui.theme.NileLight
import com.arabicflashcards.app.ui.theme.Terracotta
import com.arabicflashcards.app.ui.theme.Sand

@Composable
internal fun LessonsScreen(
    state: FlashcardUiState,
    onCreateLesson: (String) -> Unit,
    onRenameLesson: (String, String) -> Unit,
    onDeleteLesson: (String) -> Unit,
    onSetLessonCards: (String, List<String>) -> Unit,
    onReorderLessons: (List<String>) -> Unit,
    onGradeCard: (String, Boolean) -> Unit,
    onCelebrationDone: () -> Unit,
    onDirectionChange: (StudyDirection) -> Unit
) {
    var openLessonId by rememberSaveable { mutableStateOf<String?>(null) }
    var playingLessonId by rememberSaveable { mutableStateOf<String?>(null) }

    val playingLesson = state.lessons.find { it.id == playingLessonId }
    val openLesson = state.lessons.find { it.id == openLessonId }

    when {
        playingLesson != null -> {
            val orderedCards = playingLesson.cardIds.mapNotNull { id -> state.cards.find { it.id == id } }
            LessonPlayerScreen(
                lessonName = playingLesson.name,
                cards = orderedCards,
                direction = state.direction,
                onDirectionChange = onDirectionChange,
                justScoredPoints = state.justScoredPoints,
                onCelebrationDone = onCelebrationDone,
                onGrade = onGradeCard,
                onExit = { playingLessonId = null }
            )
        }
        openLesson != null -> {
            LessonDetailScreen(
                lesson = openLesson,
                allCards = state.cards,
                onRename = { newName -> onRenameLesson(openLesson.id, newName) },
                onSetCards = { cardIds -> onSetLessonCards(openLesson.id, cardIds) },
                onStart = { playingLessonId = openLesson.id },
                onBack = { openLessonId = null },
                onDelete = { onDeleteLesson(openLesson.id); openLessonId = null }
            )
        }
        else -> {
            LessonsListScreen(
                lessons = state.lessons,
                allCards = state.cards,
                onCreateLesson = onCreateLesson,
                onOpenLesson = { openLessonId = it },
                onStartLesson = { playingLessonId = it },
                onReorderLessons = onReorderLessons
            )
        }
    }
}

private val lessonAccent = Brush.linearGradient(listOf(Nile, NileLight))
private val progressAccent = Brush.horizontalGradient(listOf(Gold, GoldLight))

/** Permanent last row in the lesson list — a short list should never trail off into dead space. */
@Composable
private fun AddLessonGhostRow(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outline
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .drawWithContent {
                drawContent()
                drawRoundRect(
                    color = borderColor,
                    style = Stroke(
                        width = 1.6.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                    ),
                    cornerRadius = CornerRadius(18.dp.toPx())
                )
            }
            .clickable(onClick = onClick)
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(8.dp))
        Text("Add another lesson", color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun LessonsListScreen(
    lessons: List<Lesson>,
    allCards: List<UserCard>,
    onCreateLesson: (String) -> Unit,
    onOpenLesson: (String) -> Unit,
    onStartLesson: (String) -> Unit,
    onReorderLessons: (List<String>) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        if (lessons.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Nile, Terracotta))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.AutoStories,
                            contentDescription = null,
                            tint = Cream,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("No lessons yet", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Group your cards into an ordered series you can study front to back.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { showCreateDialog = true }) { Text("Create your first lesson") }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
                    val cardsInLesson = remember(lesson.cardIds, allCards) {
                        lesson.cardIds.mapNotNull { id -> allCards.find { it.id == id } }
                    }
                    val count = cardsInLesson.size
                    val masteredCount = cardsInLesson.count { it.mastered }
                    val progress = if (count > 0) masteredCount.toFloat() / count else 0f
                    var menuExpanded by remember { mutableStateOf(false) }

                    val stripeColor = MaterialTheme.colorScheme.primary

                    Card(
                        onClick = { onOpenLesson(lesson.id) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)
                    ) {
                        Box {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .drawBehind {
                                    drawRect(color = stripeColor, size = Size(3.dp.toPx(), size.height))
                                }
                                .padding(16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.Top) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(lessonAccent),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "${index + 1}",
                                        color = Cream,
                                        fontWeight = FontWeight.Black,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f).padding(end = 28.dp)) {
                                    Text(
                                        lesson.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.MenuBook,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            "$count card${if (count == 1) "" else "s"}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                        )
                                        if (count > 0) {
                                            Text(
                                                "  ·  ",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                            )
                                            Text(
                                                if (masteredCount > 0) "${(progress * 100).toInt()}% mastered" else "not started",
                                                fontSize = 11.sp,
                                                fontWeight = if (masteredCount > 0) FontWeight.Bold else FontWeight.Normal,
                                                color = if (masteredCount > 0) Gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                                            )
                                        }
                                    }
                                    if (count > 0) {
                                        Spacer(Modifier.height(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(5.dp)
                                                .clip(RoundedCornerShape(50))
                                                .background(Sand)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxHeight()
                                                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                                                    .clip(RoundedCornerShape(50))
                                                    .background(progressAccent)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    modifier = Modifier
                                        .height(34.dp)
                                        .clip(RoundedCornerShape(50))
                                        .then(
                                            if (count > 0) Modifier.background(lessonAccent)
                                            else Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                        )
                                        .clickable(enabled = count > 0) { onStartLesson(lesson.id) }
                                        .padding(horizontal = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Cream,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(Modifier.width(5.dp))
                                    Text("Start", color = Cream, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            }
                        }

                        Box(modifier = Modifier.align(Alignment.TopEnd).padding(top = 10.dp, end = 10.dp)) {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                            ) {
                                Icon(
                                    Icons.Default.MoreHoriz,
                                    contentDescription = "More options for ${lesson.name}",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem(
                                    text = { Text("Move up") },
                                    enabled = index > 0,
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowUp, contentDescription = null) },
                                    onClick = {
                                        val ids = lessons.map { it.id }.toMutableList()
                                        ids.removeAt(index)
                                        ids.add(index - 1, lesson.id)
                                        onReorderLessons(ids)
                                        menuExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Move down") },
                                    enabled = index < lessons.lastIndex,
                                    leadingIcon = { Icon(Icons.Default.KeyboardArrowDown, contentDescription = null) },
                                    onClick = {
                                        val ids = lessons.map { it.id }.toMutableList()
                                        ids.removeAt(index)
                                        ids.add(index + 1, lesson.id)
                                        onReorderLessons(ids)
                                        menuExpanded = false
                                    }
                                )
                            }
                        }
                        }
                    }
                }

                item(key = "add_lesson_ghost") {
                    AddLessonGhostRow(onClick = { showCreateDialog = true })
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .size(56.dp)
                .shadow(elevation = 6.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(lessonAccent)
                .clickable { showCreateDialog = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Add, contentDescription = "Create lesson", tint = Cream)
        }
    }

    if (showCreateDialog) {
        LessonNameDialog(
            title = "New lesson",
            initial = "",
            onDismiss = { showCreateDialog = false },
            onSave = { name -> onCreateLesson(name); showCreateDialog = false }
        )
    }
}

@Composable
private fun LessonNameDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Lesson name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim()) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LessonDetailScreen(
    lesson: Lesson,
    allCards: List<UserCard>,
    onRename: (String) -> Unit,
    onSetCards: (List<String>) -> Unit,
    onStart: () -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit
) {
    var showRenameDialog by remember { mutableStateOf(false) }
    var showAddCardsDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val orderedCards = remember(lesson.cardIds, allCards) {
        lesson.cardIds.mapNotNull { id -> allCards.find { it.id == id } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to lessons")
            }
            Text(lesson.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { showRenameDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Rename lesson")
            }
            IconButton(onClick = { showDeleteDialog = true }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete lesson")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "${orderedCards.size} card${if (orderedCards.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge
            )
            Button(onClick = onStart, enabled = orderedCards.isNotEmpty()) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Start lesson")
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(Modifier.fillMaxSize().weight(1f)) {
            if (orderedCards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No cards in this lesson yet — tap + to add some",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(orderedCards, key = { _, card -> card.id }) { index, card ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${index + 1}.",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        parseCardMarkup(card.english, boldColor = MaterialTheme.colorScheme.tertiary),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        parseCardMarkup(card.egyptian, boldColor = MaterialTheme.colorScheme.tertiary),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        val ids = orderedCards.map { it.id }.toMutableList()
                                        ids.removeAt(index)
                                        ids.add(index - 1, card.id)
                                        onSetCards(ids)
                                    },
                                    enabled = index > 0
                                ) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move card up") }
                                IconButton(
                                    onClick = {
                                        val ids = orderedCards.map { it.id }.toMutableList()
                                        ids.removeAt(index)
                                        ids.add(index + 1, card.id)
                                        onSetCards(ids)
                                    },
                                    enabled = index < orderedCards.lastIndex
                                ) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move card down") }
                                IconButton(onClick = { onSetCards(orderedCards.map { it.id } - card.id) }) {
                                    Icon(Icons.Default.Close, contentDescription = "Remove from lesson")
                                }
                            }
                        }
                    }
                }
            }

            FloatingActionButton(
                onClick = { showAddCardsDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "Add cards to lesson") }
        }
    }

    if (showRenameDialog) {
        LessonNameDialog(
            title = "Rename lesson",
            initial = lesson.name,
            onDismiss = { showRenameDialog = false },
            onSave = { name -> onRename(name); showRenameDialog = false }
        )
    }

    if (showAddCardsDialog) {
        AddCardsToLessonDialog(
            allCards = allCards,
            alreadyInLesson = lesson.cardIds.toSet(),
            onDismiss = { showAddCardsDialog = false },
            onConfirm = { selectedIds ->
                onSetCards(lesson.cardIds + selectedIds.filterNot { it in lesson.cardIds })
                showAddCardsDialog = false
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete this lesson?") },
            text = { Text("\"${lesson.name}\" will be removed. Your cards themselves won't be deleted.") },
            confirmButton = {
                TextButton(onClick = { showDeleteDialog = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun AddCardsToLessonDialog(
    allCards: List<UserCard>,
    alreadyInLesson: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val available = remember(allCards, alreadyInLesson) { allCards.filterNot { it.id in alreadyInLesson } }
    var selected by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add cards") },
        text = {
            if (available.isEmpty()) {
                Text(
                    "All your cards are already in this lesson.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                    items(available, key = { it.id }) { card ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (card.id in selected) selected - card.id else selected + card.id
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = card.id in selected,
                                onCheckedChange = {
                                    selected = if (card.id in selected) selected - card.id else selected + card.id
                                }
                            )
                            Column {
                                Text(card.english, fontWeight = FontWeight.Medium)
                                Text(
                                    card.egyptian,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }, enabled = selected.isNotEmpty()) {
                Text("Add ${selected.size} card${if (selected.size == 1) "" else "s"}")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun LessonPlayerScreen(
    lessonName: String,
    cards: List<UserCard>,
    direction: StudyDirection,
    onDirectionChange: (StudyDirection) -> Unit,
    justScoredPoints: Boolean,
    onCelebrationDone: () -> Unit,
    onGrade: (String, Boolean) -> Unit,
    onExit: () -> Unit
) {
    var index by rememberSaveable { mutableStateOf(0) }
    var flipped by rememberSaveable { mutableStateOf(false) }
    val soundPlayer = LocalSoundPlayer.current

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onExit) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Exit lesson")
            }
            Text(lessonName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = direction == StudyDirection.EGYPTIAN_FIRST,
                onClick = { flipped = false; onDirectionChange(StudyDirection.EGYPTIAN_FIRST) },
                label = { Text("🇪🇬 Egyptian → English", style = MaterialTheme.typography.labelMedium) }
            )
            FilterChip(
                selected = direction == StudyDirection.ENGLISH_FIRST,
                onClick = { flipped = false; onDirectionChange(StudyDirection.ENGLISH_FIRST) },
                label = { Text("English → 🇪🇬 Egyptian", style = MaterialTheme.typography.labelMedium) }
            )
        }

        if (cards.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "This lesson has no cards yet.",
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
            }
            return
        }

        if (index >= cards.size) {
            LaunchedEffect(Unit) { soundPlayer.playLessonComplete() }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Lesson complete! 🎉", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { index = 0; flipped = false }) { Text("Study again") }
                        Button(onClick = onExit) { Text("Done") }
                    }
                }
            }
            return
        }

        Text(
            "${index + 1} / ${cards.size}",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
        )
        LinearProgressIndicator(
            progress = { (index + 1f) / cards.size },
            modifier = Modifier.fillMaxWidth().height(6.dp)
        )

        Spacer(Modifier.height(16.dp))

        Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            FlipStudyCard(
                card = cards[index],
                direction = direction,
                isFlipped = flipped,
                onFlip = { flipped = !flipped },
                onSwipeLeft = { if (index < cards.lastIndex) { index++; flipped = false } },
                onSwipeRight = { if (index > 0) { index--; flipped = false } },
                modifier = Modifier.fillMaxWidth()
            )
            CelebrationOverlay(show = justScoredPoints, onDone = onCelebrationDone)
        }

        Spacer(Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = { if (index > 0) { index--; flipped = false } },
                modifier = Modifier.weight(1f)
            ) { Text("‹ Prev") }
            OutlinedButton(
                onClick = { if (index < cards.lastIndex) { index++; flipped = false } },
                modifier = Modifier.weight(1f)
            ) { Text("Next ›") }
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    soundPlayer.playIncorrect()
                    onGrade(cards[index].id, false)
                    index++
                    flipped = false
                },
                enabled = flipped,
                modifier = Modifier.weight(1f)
            ) { Text("❌ Didn't know it") }
            Button(
                onClick = {
                    soundPlayer.playCorrect()
                    onGrade(cards[index].id, true)
                    index++
                    flipped = false
                },
                enabled = flipped,
                modifier = Modifier.weight(1f)
            ) { Text("✅ Knew it!") }
        }
    }
}
