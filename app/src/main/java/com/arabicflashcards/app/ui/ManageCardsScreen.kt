@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Card
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arabicflashcards.app.data.Lesson
import com.arabicflashcards.app.data.StudyDirection
import com.arabicflashcards.app.data.UserCard

@Composable
internal fun ManageCardsScreen(
    cards: List<UserCard>,
    allTags: Set<String>,
    lessons: List<Lesson>,
    direction: StudyDirection,
    onAdd: (String, String, List<String>) -> Unit,
    onEdit: (String, String, String, List<String>) -> Unit,
    onDelete: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<UserCard?>(null) }
    var deletingCard by remember { mutableStateOf<UserCard?>(null) }
    var showTagManager by remember { mutableStateOf(false) }
    var previewCard by remember { mutableStateOf<UserCard?>(null) }
    var selectedTagFilter by remember { mutableStateOf<Set<String>>(emptySet()) }

    val filteredCards = remember(cards, selectedTagFilter) {
        if (selectedTagFilter.isEmpty()) cards
        else cards.filter { card -> card.tags.any { it in selectedTagFilter } }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${filteredCards.size} card${if (filteredCards.size == 1) "" else "s"}",
                style = MaterialTheme.typography.labelLarge
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { showTagManager = true }) { Text("Manage tags") }
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Add card")
                }
            }
        }

        Box(Modifier.fillMaxSize().weight(1f)) {
            if (filteredCards.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (cards.isEmpty()) "No cards yet — tap + to add one"
                        else "No cards match the selected tags",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCards, key = { it.id }) { card ->
                        val cardLessons = remember(card.id, lessons) {
                            lessons.filter { card.id in it.cardIds }
                        }
                        Card(
                            onClick = { previewCard = card },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    if (cardLessons.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            cardLessons.forEach { lesson -> LessonPill(lesson.name) }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                    }
                                    Text(
                                        parseCardMarkup(card.english, boldColor = MaterialTheme.colorScheme.tertiary),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        parseCardMarkup(card.egyptian, boldColor = MaterialTheme.colorScheme.tertiary),
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                    if (card.tags.isNotEmpty()) {
                                        Spacer(Modifier.height(4.dp))
                                        Row(
                                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            card.tags.forEach { tag -> TagPill(tag) }
                                        }
                                    }
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
        }
    }

    if (showAddDialog) {
        CardEditDialog(
            initial = null,
            allTags = allTags,
            onCreateTag = onCreateTag,
            onDismiss = { showAddDialog = false },
            onSave = { english, egyptian, tags ->
                onAdd(english, egyptian, tags)
                showAddDialog = false
            }
        )
    }

    editingCard?.let { card ->
        CardEditDialog(
            initial = card,
            allTags = allTags,
            onCreateTag = onCreateTag,
            onDismiss = { editingCard = null },
            onSave = { english, egyptian, tags ->
                onEdit(card.id, english, egyptian, tags)
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

    if (showTagManager) {
        TagManagerDialog(
            allTags = allTags,
            selectedTagFilter = selectedTagFilter,
            onCreateTag = onCreateTag,
            onDeleteTag = { tag ->
                onDeleteTag(tag)
                selectedTagFilter = selectedTagFilter - tag
            },
            onApplyFilter = { selectedTagFilter = it },
            onDismiss = { showTagManager = false }
        )
    }

    previewCard?.let { card ->
        CardPreviewDialog(
            card = card,
            direction = direction,
            lessons = lessons.filter { card.id in it.cardIds },
            onDismiss = { previewCard = null }
        )
    }
}

@Composable
private fun CardPreviewDialog(
    card: UserCard,
    direction: StudyDirection,
    lessons: List<Lesson>,
    onDismiss: () -> Unit
) {
    var flipped by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close preview")
                    }
                }
                if (lessons.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        lessons.forEach { lesson -> LessonPill(lesson.name) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                FlipStudyCard(
                    card = card,
                    direction = direction,
                    isFlipped = flipped,
                    onFlip = { flipped = !flipped },
                    onSwipeLeft = {},
                    onSwipeRight = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
                if (card.tags.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        card.tags.forEach { tag -> TagPill(tag) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardEditDialog(
    initial: UserCard?,
    allTags: Set<String>,
    onCreateTag: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (english: String, egyptian: String, tags: List<String>) -> Unit
) {
    var english by remember { mutableStateOf(initial?.english ?: "") }
    var egyptian by remember { mutableStateOf(initial?.egyptian ?: "") }
    var selectedTags by remember { mutableStateOf(initial?.tags?.toSet() ?: emptySet()) }
    var newTagText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a card" else "Edit card") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
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
                Spacer(Modifier.height(4.dp))
                Text(
                    "*bold*, _italic_, or *_both_* nested",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Spacer(Modifier.height(12.dp))
                Text("Tags", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                if (allTags.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(allTags.toList().sorted()) { tag ->
                            FilterChip(
                                selected = tag in selectedTags,
                                onClick = {
                                    selectedTags = if (tag in selectedTags) selectedTags - tag else selectedTags + tag
                                },
                                label = { Text(tag) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val tag = newTagText.trim()
                        if (tag.isNotEmpty()) {
                            onCreateTag(tag)
                            selectedTags = selectedTags + tag
                            newTagText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(english, egyptian, selectedTags.toList()) },
                enabled = english.isNotBlank() && egyptian.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun TagManagerDialog(
    allTags: Set<String>,
    selectedTagFilter: Set<String>,
    onCreateTag: (String) -> Unit,
    onDeleteTag: (String) -> Unit,
    onApplyFilter: (Set<String>) -> Unit,
    onDismiss: () -> Unit
) {
    var newTagText by remember { mutableStateOf("") }
    var localFilter by remember { mutableStateOf(selectedTagFilter) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage tags") },
        text = {
            Column {
                if (allTags.isEmpty()) {
                    Text(
                        "No tags yet. Create one below, or add one while editing a card.",
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                } else {
                    Text(
                        "Select which tags to filter My Cards by. Leave none selected to show every card.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(allTags.toList().sorted()) { tag ->
                            val selected = tag in localFilter
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        localFilter = if (selected) localFilter - tag else localFilter + tag
                                    }
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = selected, onCheckedChange = {
                                        localFilter = if (selected) localFilter - tag else localFilter + tag
                                    })
                                    Text(tag)
                                }
                                IconButton(onClick = {
                                    onDeleteTag(tag)
                                    localFilter = localFilter - tag
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete tag \"$tag\"")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagText,
                        onValueChange = { newTagText = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        val tag = newTagText.trim()
                        if (tag.isNotEmpty()) {
                            onCreateTag(tag)
                            newTagText = ""
                        }
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add tag")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onApplyFilter(localFilter)
                onDismiss()
            }) { Text("Done") }
        }
    )
}
