package com.laura.royaltasks.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.laura.royaltasks.BuildConfig
import com.laura.royaltasks.data.CROWN_BONUS_XP
import com.laura.royaltasks.data.Priority
import com.laura.royaltasks.data.Task
import com.laura.royaltasks.ui.theme.BlushPink
import com.laura.royaltasks.ui.theme.ShimmerSilverDim
import com.laura.royaltasks.ui.theme.tabular
import kotlinx.coroutines.launch

private val CardShape = RoundedCornerShape(16.dp)

/** Accent colour for a priority. Used for bars and dots only — never text. */
@Composable
private fun Priority.accent(): Color = when (this) {
    Priority.HIGH -> BlushPink
    Priority.NORMAL -> MaterialTheme.colorScheme.primary
    Priority.LOW -> ShimmerSilverDim
}

private fun View.confirmHaptic() {
    performHapticFeedback(
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TodoScreen(vm: TaskViewModel) {
    val state by vm.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var bursts by remember { mutableStateOf(listOf<Burst>()) }
    var pops by remember { mutableStateOf(listOf<XpPop>()) }
    var nextFxId by remember { mutableLongStateOf(0L) }
    var levelUp by remember { mutableStateOf<Int?>(null) }
    var editing by remember { mutableStateOf<Task?>(null) }
    var tab by rememberSaveable { mutableStateOf(AppTab.TASKS) }
    val ideasState by vm.ideasState.collectAsState()

    fun showMessage(message: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
        snackbar.currentSnackbarData?.dismiss()
        scope.launch {
            val result = snackbar.showSnackbar(message, actionLabel, duration = SnackbarDuration.Short)
            if (result == SnackbarResult.ActionPerformed) onAction()
        }
    }

    LaunchedEffect(Unit) {
        vm.events.collect { event ->
            val screenCenter = with(density) {
                Offset(
                    configuration.screenWidthDp.dp.toPx() / 2,
                    configuration.screenHeightDp.dp.toPx() * 0.35f
                )
            }
            when (event) {
                Celebration.CrownEarned -> {
                    bursts = bursts + Burst(nextFxId++, screenCenter, big = true)
                    showMessage("👑 Crown earned. +$CROWN_BONUS_XP XP bonus.")
                }
                is Celebration.LevelUp -> {
                    levelUp = event.level
                    bursts = bursts + Burst(nextFxId++, screenCenter, big = true)
                }
            }
        }
    }

    val onComplete: (Task, Offset) -> Unit = { task, origin ->
        view.confirmHaptic()
        bursts = bursts + Burst(nextFxId++, origin, big = false)
        pops = pops + XpPop(nextFxId++, origin, "+${task.priority.xp} XP")
        vm.complete(task.id)
    }
    val onDelete: (Task) -> Unit = { task ->
        vm.delete(task.id)
        showMessage("Cleared.", actionLabel = "Undo") { vm.restore(task) }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                AppTabBar(
                    selected = tab,
                    openTasks = state.open.size,
                    ideas = ideasState.ideas.size,
                    onSelect = { tab = it }
                )
            }
        ) { padding ->
            if (tab == AppTab.IDEAS) {
                IdeasTab(
                    state = ideasState,
                    padding = padding,
                    onAdd = vm::addIdea,
                    onUpdate = vm::updateIdea,
                    onDelete = { idea ->
                        vm.deleteIdea(idea.id)
                        showMessage("Cleared.", actionLabel = "Undo") { vm.restoreIdea(idea) }
                    },
                    onMakeTask = { idea ->
                        vm.ideaToTask(idea) { taskId ->
                            showMessage("Added to tasks.", actionLabel = "Undo") {
                                vm.undoIdeaToTask(idea, taskId)
                            }
                        }
                    }
                )
            } else LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 12.dp,
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "hero") {
                    HeroCard(
                        progress = state.progress,
                        today = state.today,
                        doneToday = state.doneToday.size,
                        soundEnabled = state.soundEnabled,
                        onToggleSound = vm::toggleSound
                    )
                }
                item(key = "add") {
                    QuickAdd(
                        onAdd = vm::add,
                        modifier = Modifier.padding(top = 6.dp, bottom = 4.dp)
                    )
                }
                if (state.loaded && state.open.isEmpty()) {
                    item(key = "empty") {
                        Text(
                            text = "All clear. Enjoy it.",
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
                items(state.open, key = { it.id }) { task ->
                    SwipeableTaskRow(
                        task = task,
                        onComplete = onComplete,
                        onDelete = onDelete,
                        onEdit = { editing = it },
                        modifier = Modifier.animateItemPlacement()
                    )
                }
                if (state.doneToday.isNotEmpty()) {
                    item(key = "done-header") {
                        Text(
                            text = "DONE TODAY · ${state.doneToday.size}",
                            style = MaterialTheme.typography.labelLarge.tabular(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(top = 14.dp, start = 4.dp)
                                .animateItemPlacement()
                        )
                    }
                    items(state.doneToday, key = { "done-" + it.id }) { task ->
                        DoneRow(
                            task = task,
                            onUndo = { vm.uncomplete(task.id) },
                            modifier = Modifier.animateItemPlacement()
                        )
                    }
                }
                item(key = "build") {
                    Text(
                        text = "Build ${BuildConfig.GIT_SHA}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 24.dp)
                    )
                }
            }
        }

        levelUp?.let { level ->
            LevelUpOverlay(level = level, onDismiss = { levelUp = null })
        }
        ConfettiLayer(bursts) { id -> bursts = bursts.filterNot { it.id == id } }
        XpPopLayer(pops) { id -> pops = pops.filterNot { it.id == id } }
    }

    editing?.let { task ->
        EditTaskDialog(
            task = task,
            onSave = { title, priority ->
                vm.update(task.id, title, priority)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun QuickAdd(onAdd: (String, Priority) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    var priority by rememberSaveable { mutableStateOf(Priority.NORMAL) }

    val submit = {
        if (text.isNotBlank()) {
            onAdd(text.trim(), priority)
            text = ""
            priority = Priority.NORMAL
        }
    }

    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = 8.dp)
        ) {
            TextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Drop a task…") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                    imeAction = ImeAction.Done
                ),
                // Keeps the keyboard up so several tasks can go in back to back.
                keyboardActions = KeyboardActions(onDone = { submit() }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                modifier = Modifier.weight(1f)
            )
            PriorityToggle(priority = priority, onClick = { priority = priority.next() })
            Spacer(Modifier.width(4.dp))
            FilledIconButton(
                onClick = submit,
                enabled = text.isNotBlank(),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add task")
            }
        }
    }
}

@Composable
private fun PriorityToggle(priority: Priority, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(CircleShape)
            .clickable(onClickLabel = "Change priority", onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(priority.accent())
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "${priority.xp} XP",
            style = MaterialTheme.typography.labelMedium.tabular(),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableTaskRow(
    task: Task,
    onComplete: (Task, Offset) -> Unit,
    onDelete: (Task) -> Unit,
    onEdit: (Task) -> Unit,
    modifier: Modifier = Modifier
) {
    val dismissState = rememberSwipeToDismissBoxState()
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) onDelete(task)
    }
    SwipeToDismissBox(
        state = dismissState,
        modifier = modifier,
        backgroundContent = { ClearBackground(dismissState) }
    ) {
        TaskCard(task = task, onComplete = onComplete, onEdit = onEdit)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClearBackground(state: SwipeToDismissBoxState) {
    val alignment = if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
        Alignment.CenterEnd
    } else {
        Alignment.CenterStart
    }
    Box(
        contentAlignment = alignment,
        modifier = Modifier
            .fillMaxSize()
            .clip(CardShape)
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
private fun TaskCard(
    task: Task,
    onComplete: (Task, Offset) -> Unit,
    onEdit: (Task) -> Unit
) {
    var checkCenter by remember { mutableStateOf(Offset.Zero) }
    var ticked by remember { mutableStateOf(false) }
    val accent = task.priority.accent()

    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // Left accent bar encodes priority.
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxHeight()
                    .background(accent)
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClickLabel = "Edit task") { onEdit(task) }
                    .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .onGloballyPositioned { checkCenter = it.boundsInRoot().center }
                        .clickable(enabled = !ticked, onClickLabel = "Mark done") {
                            ticked = true
                            onComplete(task, checkCenter)
                        }
                ) {
                    CheckCircle(checked = ticked, color = accent)
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 8.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "${task.priority.xp} XP",
                    style = MaterialTheme.typography.labelSmall.tabular(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean, color: Color) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .then(
                if (checked) Modifier.background(color)
                else Modifier.border(2.dp, color, CircleShape)
            )
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun DoneRow(task: Task, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = CardShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 11.dp, end = 14.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(onClickLabel = "Mark not done", onClick = onUndo)
            ) {
                CheckCircle(checked = true, color = MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "+${task.xpAwarded} XP",
                style = MaterialTheme.typography.labelSmall.tabular(),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditTaskDialog(
    task: Task,
    onSave: (String, Priority) -> Unit,
    onDismiss: () -> Unit
) {
    var title by rememberSaveable { mutableStateOf(task.title) }
    var priority by rememberSaveable { mutableStateOf(task.priority) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit task", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Priority.entries.forEach { option ->
                        FilterChip(
                            selected = option == priority,
                            onClick = { priority = option },
                            label = { Text(option.label) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(option.accent())
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, priority) }, enabled = title.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
