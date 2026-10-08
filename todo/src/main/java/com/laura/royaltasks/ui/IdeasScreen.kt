package com.laura.royaltasks.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.laura.royaltasks.data.Idea
import com.laura.royaltasks.ui.theme.ComebackGold
import com.laura.royaltasks.ui.theme.tabular
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

enum class AppTab { TASKS, IDEAS }

private val IdeaShape = RoundedCornerShape(16.dp)

@Composable
fun AppTabBar(selected: AppTab, openTasks: Int, ideas: Int, onSelect: (AppTab) -> Unit) {
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        selectedTextColor = MaterialTheme.colorScheme.onSurface,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        NavigationBarItem(
            selected = selected == AppTab.TASKS,
            onClick = { onSelect(AppTab.TASKS) },
            icon = {
                Icon(
                    if (selected == AppTab.TASKS) Icons.Filled.TaskAlt else Icons.Outlined.TaskAlt,
                    contentDescription = null
                )
            },
            label = { Text(if (openTasks > 0) "Tasks · $openTasks" else "Tasks", style = MaterialTheme.typography.labelMedium.tabular()) },
            colors = itemColors
        )
        NavigationBarItem(
            selected = selected == AppTab.IDEAS,
            onClick = { onSelect(AppTab.IDEAS) },
            icon = {
                Icon(
                    if (selected == AppTab.IDEAS) Icons.Filled.Lightbulb else Icons.Outlined.Lightbulb,
                    contentDescription = null
                )
            },
            label = { Text(if (ideas > 0) "Ideas · $ideas" else "Ideas", style = MaterialTheme.typography.labelMedium.tabular()) },
            colors = itemColors
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun IdeasTab(
    state: IdeasState,
    padding: PaddingValues,
    onAdd: (String) -> Unit,
    onUpdate: (String, String) -> Unit,
    onDelete: (Idea) -> Unit,
    onMakeTask: (Idea) -> Unit
) {
    var editing by remember { mutableStateOf<Idea?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = padding.calculateTopPadding() + 12.dp,
            bottom = padding.calculateBottomPadding() + 24.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "ideas-hero") { IdeasHero(count = state.ideas.size) }
        item(key = "ideas-add") {
            JotBox(onAdd = onAdd, modifier = Modifier.padding(top = 6.dp, bottom = 4.dp))
        }
        if (state.loaded && state.ideas.isEmpty()) {
            item(key = "ideas-empty") {
                Text(
                    text = "Nothing jotted yet.",
                    style = MaterialTheme.typography.bodyLarge,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp)
                )
            }
        }
        items(state.ideas, key = { it.id }) { idea ->
            SwipeableIdeaCard(
                idea = idea,
                onOpen = { editing = idea },
                onDelete = onDelete,
                modifier = Modifier.animateItemPlacement()
            )
        }
    }

    editing?.let { idea ->
        EditIdeaDialog(
            idea = idea,
            onSave = { text ->
                onUpdate(idea.id, text)
                editing = null
            },
            onMakeTask = {
                onMakeTask(idea)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

/** Same gradient ground as the Tasks hero, kept quieter: this tab is for thinking. */
@Composable
private fun IdeasHero(count: Int) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(RoyalGradient)
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (count == 1) "1 IDEA SAVED" else "$count IDEAS SAVED",
                style = MaterialTheme.typography.labelLarge.tabular(),
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = ComebackGold)
        }
        Text(
            text = "Ideas",
            style = MaterialTheme.typography.headlineLarge,
            color = Color.White
        )
        Text(
            text = "Catch it now. Sort it later.",
            style = MaterialTheme.typography.bodyMedium,
            fontStyle = FontStyle.Italic,
            color = Color.White
        )
    }
}

@Composable
private fun JotBox(onAdd: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }

    Surface(
        shape = IdeaShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(bottom = 10.dp, end = 10.dp)) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Jot an idea…") },
                minLines = 2,
                maxLines = 8,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = {
                    onAdd(text)
                    text = ""
                },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Save idea", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableIdeaCard(
    idea: Idea,
    onOpen: () -> Unit,
    onDelete: (Idea) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDelete(idea)
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { IdeaClearBackground(dismissState) }
    ) {
        Surface(
            shape = IdeaShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClickLabel = "Open idea", onClick = onOpen)
                    .padding(horizontal = 18.dp, vertical = 14.dp)
            ) {
                Text(
                    text = idea.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = savedOn(idea.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IdeaClearBackground(state: SwipeToDismissBoxState) {
    val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }
    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxSize()
            .clip(IdeaShape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "Clear",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun EditIdeaDialog(
    idea: Idea,
    onSave: (String) -> Unit,
    onMakeTask: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(idea.text) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Idea", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    minLines = 3,
                    maxLines = 10,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(onClick = onMakeTask) {
                    Icon(Icons.Outlined.TaskAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Make it a task", style = MaterialTheme.typography.labelLarge)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private val DayMonth = DateTimeFormatter.ofPattern("d MMM")
private val DayMonthYear = DateTimeFormatter.ofPattern("d MMM yyyy")

private fun savedOn(epochMillis: Long): String {
    if (epochMillis <= 0L) return ""
    val zone = ZoneId.systemDefault()
    val date = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        date == today -> "Today"
        date == today.minusDays(1) -> "Yesterday"
        date.year == today.year -> date.format(DayMonth)
        else -> date.format(DayMonthYear)
    }
}
