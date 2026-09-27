package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.coach.CoachPayload
import com.laurasheehan.royalmiles.data.coach.chat.ChatMessage
import com.laurasheehan.royalmiles.data.coach.chat.ChatUsage
import com.laurasheehan.royalmiles.data.coach.chat.MemoryKind
import com.laurasheehan.royalmiles.data.coach.chat.MemoryNote
import com.laurasheehan.royalmiles.data.coach.chat.MemoryProposal
import com.laurasheehan.royalmiles.ui.sync.openUrl
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import com.laurasheehan.royalmiles.ui.theme.ComebackGold

@Composable
fun ChatScreen(viewModel: ChatViewModel, onBack: () -> Unit) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    ChatContent(
        state = state,
        onBack = onBack,
        onSend = viewModel::send,
        onToggleResearch = viewModel::toggleResearch,
        onAccept = viewModel::accept,
        onDismiss = viewModel::dismiss,
        onConnect = viewModel::connect,
        onClear = viewModel::clearConversation,
        onOpenLink = { openUrl(context, it) },
        onRemember = viewModel::remember,
        onNotNow = viewModel::notNow,
        onLoadMemory = viewModel::loadMemory,
        onForget = viewModel::forget,
        onRetry = viewModel::retry,
        onUnremember = viewModel::unremember,
        onRewrite = viewModel::rewrite,
    )
}

private val SuggestedQuestions = listOf(
    "How should I run tomorrow?",
    "Am I recovering well this week?",
    "What should I eat before my long run?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatContent(
    state: ChatUiState,
    onBack: () -> Unit,
    onSend: (String) -> Unit,
    onToggleResearch: () -> Unit,
    onAccept: (ChatMessage) -> Unit,
    onDismiss: (ChatMessage) -> Unit,
    onConnect: (String, String) -> Unit,
    onClear: () -> Unit,
    onOpenLink: (String) -> Unit,
    onRemember: (ChatMessage, MemoryProposal) -> Unit,
    onNotNow: (ChatMessage) -> Unit,
    onLoadMemory: () -> Unit,
    onForget: (MemoryNote) -> Unit,
    onRetry: (ChatMessage) -> Unit = {},
    onUnremember: (ChatMessage) -> Unit = {},
    onRewrite: (ChatMessage, MemoryProposal) -> Unit = { _, _ -> },
    initialShowMemory: Boolean = false,
) {
    var showMemory by remember { mutableStateOf(initialShowMemory) }
    if (showMemory) {
        MemoryPanel(
            notes = state.memoryNotes,
            error = state.memoryError,
            onLoad = onLoadMemory,
            onForget = onForget,
            onBack = { showMemory = false },
        )
        return
    }
    var showConnect by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.sending) {
        val last = state.messages.size + (if (state.sending) 1 else 0) - 1
        if (last >= 0) listState.animateScrollToItem(last)
    }
    // Close the dialog once a connection succeeds; keep it open, showing why, if it failed.
    LaunchedEffect(state.connectionVersion) { if (state.connectionVersion > 0) showConnect = false }

    if (showConnect) {
        ChatConnectDialog(
            suggestedAddress = state.suggestedAddress,
            suggestedKey = state.suggestedKey,
            error = state.connectError,
            onDismiss = { showConnect = false },
            onConnect = onConnect,
        )
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear this conversation?") },
            text = { Text("It's only stored on this phone, so it can't be brought back.") },
            confirmButton = { TextButton(onClick = { confirmClear = false; onClear() }) { Text("Clear") } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Keep it") } },
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                title = {
                    Column {
                        Text("Speak to coach")
                        state.usage?.let { UsageLine(it) }
                    }
                },
                actions = {
                    if (state.connected) {
                        IconButton(onClick = { showMemory = true }) {
                            Icon(Icons.Filled.Psychology, contentDescription = "What your coach knows")
                        }
                    }
                    if (state.messages.isNotEmpty()) {
                        IconButton(onClick = { confirmClear = true }) {
                            Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear conversation")
                        }
                    }
                    IconButton(onClick = { showConnect = true }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Chat connection")
                    }
                },
            )
        },
        bottomBar = {
            if (state.connected) {
                Composer(
                    draft = draft,
                    onDraftChange = { draft = it },
                    sending = state.sending,
                    researchMode = state.researchMode,
                    researchEnabled = state.usage?.researchEnabled != false,
                    onToggleResearch = onToggleResearch,
                    onSend = {
                        onSend(draft)
                        draft = ""
                    },
                )
            }
        },
    ) { padding ->
        if (!state.connected) {
            NotConnected(onConnect = { showConnect = true }, modifier = Modifier.fillMaxSize().padding(padding))
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.messages.isEmpty()) {
                item { EmptyThread(onAsk = { onSend(it) }, enabled = !state.sending) }
            }
            val retryable = com.laurasheehan.royalmiles.data.coach.chat.ChatStore.failedQuestion(state.messages)?.noticeId
            items(state.messages, key = { it.id }) { message ->
                when (message.role) {
                    ChatMessage.Role.USER -> UserBubble(message)
                    ChatMessage.Role.COACH -> CoachBubble(
                        message,
                        onAccept = { onAccept(message) },
                        onDismiss = { onDismiss(message) },
                        onRemember = { onRemember(message, it) },
                        onNotNow = { onNotNow(message) },
                        onUndo = { onUnremember(message) },
                        onRewrite = { onRewrite(message, it) },
                        onOpenLink = onOpenLink,
                    )
                    ChatMessage.Role.RESEARCH -> ResearchCard(message, onOpenLink)
                    ChatMessage.Role.NOTICE -> Notice(
                        message.text,
                        onRetry = if (message.id == retryable && !state.sending) ({ onRetry(message) }) else null,
                    )
                }
            }
            if (state.sending) {
                item(key = "thinking") { Thinking(state.researchMode, state.usage?.laptopConnected == true) }
            }
        }
    }
}

@Composable
private fun UsageLine(usage: ChatUsage) {
    val text = if (!usage.chatEnabled) {
        "Switched off · no model calls"
    } else {
        listOfNotNull(
            "Laptop on".takeIf { usage.laptopConnected },
            "Today ${usage.callsToday} of ${usage.dailyCap}",
            "$${"%.2f".format(usage.costMonthUsd)} of $${"%.2f".format(usage.monthlyBudgetUsd)}",
        ).joinToString(" · ")
    }
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun UserBubble(message: ChatMessage) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                message.text,
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CoachBubble(
    message: ChatMessage,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
    onRemember: (MemoryProposal) -> Unit,
    onNotNow: () -> Unit,
    onUndo: () -> Unit,
    onRewrite: (MemoryProposal) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth(0.92f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                MarkdownText(message.text, onOpenLink = onOpenLink)
                message.basis?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        val proposal = message.proposal
        if (proposal != null) {
            ProposalCard(proposal, message.proposalState, onAccept, onDismiss)
        }
        val memory = message.memory
        if (memory != null && message.memoryState != ChatMessage.MemoryState.NONE) {
            RememberCard(memory, message.memoryState, message.memoryNoteId != null, onRemember, onNotNow, onUndo, onRewrite)
        }
    }
}

/** The coach offering to remember something. Nothing is kept unless she taps Save. */
@Composable
private fun RememberCard(
    memory: MemoryProposal,
    state: ChatMessage.MemoryState,
    undoable: Boolean,
    onRemember: (MemoryProposal) -> Unit,
    onNotNow: () -> Unit,
    onUndo: () -> Unit,
    onRewrite: (MemoryProposal) -> Unit,
) {
    var editing by remember { mutableStateOf(false) }
    val kept = state == ChatMessage.MemoryState.SAVED && undoable
    if (editing) {
        EditMemoryDialog(
            memory,
            onDismiss = { editing = false },
            onSave = { edited -> editing = false; if (kept) onRewrite(edited) else onRemember(edited) },
        )
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Text(
                    if (kept) "Remembered · ${memory.kind.label}" else "Remember this? · ${memory.kind.label}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(memory.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            memory.expires?.let {
                Text("Until $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                ChatMessage.MemoryState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onRemember(memory) }) { Text("Save") }
                    OutlinedButton(onClick = { editing = true }) { Text("Edit") }
                    TextButton(onClick = onNotNow) { Text("Not now") }
                }
                ChatMessage.MemoryState.SAVED -> if (kept) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Your coach will keep this in mind.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { editing = true }) { Text("Edit") }
                        TextButton(onClick = onUndo) { Text("Undo") }
                    }
                } else {
                    StateLine("Saved to what your coach knows", MaterialTheme.colorScheme.primary)
                }
                ChatMessage.MemoryState.DISMISSED -> StateLine("Not saved", MaterialTheme.colorScheme.onSurfaceVariant)
                ChatMessage.MemoryState.NONE -> Unit
            }
        }
    }
}

@Composable
private fun EditMemoryDialog(memory: MemoryProposal, onDismiss: () -> Unit, onSave: (MemoryProposal) -> Unit) {
    var text by remember { mutableStateOf(memory.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit before saving") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { if (it.length <= 280) text = it },
                label = { Text(memory.kind.label) },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(memory.copy(text = text.trim())) }, enabled = text.trim().length >= 5) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Everything the coach has been allowed to remember, with a way to take any of it back. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MemoryPanel(
    notes: List<MemoryNote>?,
    error: String?,
    onLoad: () -> Unit,
    onForget: (MemoryNote) -> Unit,
    onBack: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    var confirm by remember { mutableStateOf<MemoryNote?>(null) }
    confirm?.let { note ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Forget this?") },
            text = { Text(note.text) },
            confirmButton = { TextButton(onClick = { confirm = null; onForget(note) }) { Text("Forget") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Keep it") } },
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                title = { Text("What your coach knows") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Only notes you saved from chat. Your coach and the morning coaching both read them. Tap the bin to take one back.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            error?.let { item { Text(it, color = BlushPink, style = MaterialTheme.typography.bodySmall) } }
            when {
                notes == null -> item { CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp) }
                notes.isEmpty() -> item { Text("Nothing yet. When your coach offers to remember something in chat, tap Save.") }
                else -> MemoryKind.entries.forEach { kind ->
                    val group = notes.filter { it.kind == kind }
                    if (group.isNotEmpty()) {
                        item(key = kind.name) {
                            Text(kind.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(group, key = { it.id }) { note -> MemoryRow(note, onForget = { confirm = note }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MemoryRow(note: MemoryNote, onForget: () -> Unit) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(note.text, style = MaterialTheme.typography.bodyMedium)
                val detail = listOfNotNull(
                    note.expires?.let { if (note.expired) "Expired $it" else "Until $it" },
                    note.createdAt?.take(10)?.let { "saved $it" },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onForget) { Icon(Icons.Filled.DeleteOutline, contentDescription = "Forget this") }
        }
    }
}

@Composable
private fun ProposalCard(
    proposal: CoachPayload.Coaching.Suggestion,
    state: ChatMessage.ProposalState,
    onAccept: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ComebackGold.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                "Suggested change · ${proposal.date}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(proposal.headline, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            proposal.reason?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            proposal.replaceWith?.let { replacement ->
                val target = listOfNotNull(
                    replacement.targetDurationMin?.let { "$it min" },
                    replacement.targetDistanceKm?.let { "${formatKm(it)} km" },
                ).joinToString(" · ")
                Text(
                    "Becomes: ${replacement.title}${if (target.isNotEmpty()) " · $target" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            when (state) {
                ChatMessage.ProposalState.PENDING -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onAccept) {
                        Text(if (proposal.action == CoachPayload.Coaching.SuggestionAction.SKIP) "Skip it" else "Swap it")
                    }
                    OutlinedButton(onClick = onDismiss) { Text("Keep my plan") }
                }
                ChatMessage.ProposalState.ACCEPTED -> StateLine("Changed in your plan", ComebackGold)
                ChatMessage.ProposalState.DISMISSED -> StateLine("Kept as planned", MaterialTheme.colorScheme.onSurfaceVariant)
                ChatMessage.ProposalState.NONE -> Unit
            }
        }
    }
}

@Composable
private fun StateLine(text: String, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tint)
    }
}

@Composable
private fun ResearchCard(message: ChatMessage, onOpenLink: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth(0.95f),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                Text("Web research · not your coach's own view", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            }
            MarkdownText(message.text, citations = message.citations, onOpenLink = onOpenLink)
            message.citations.forEachIndexed { index, url ->
                Text(
                    "[${index + 1}] ${url.removePrefix("https://").removePrefix("www.").take(60)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenLink(url) },
                )
            }
            message.basis?.let {
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Notice(text: String, onRetry: (() -> Unit)? = null) {
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = BlushPink,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) { Text("Send again") }
        }
    }
}

@Composable
private fun Thinking(research: Boolean, laptop: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(
            when {
                research && laptop -> "Searching the web on your laptop…"
                research -> "Searching the web…"
                laptop -> "Your coach is thinking on your laptop…"
                else -> "Your coach is thinking…"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyThread(onAsk: (String) -> Unit, enabled: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
        Text("Ask your coach anything", style = MaterialTheme.typography.titleMedium)
        Text(
            "Answers use this morning's Garmin data, your plan, your running philosophy and the research you've approved.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        SuggestedQuestions.forEach { question ->
            OutlinedButton(onClick = { onAsk(question) }, enabled = enabled) { Text(question) }
        }
    }
}

@Composable
private fun NotConnected(onConnect: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        Icon(Icons.Filled.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(44.dp))
        Text("Connect the coach chat", style = MaterialTheme.typography.titleLarge)
        Text(
            "Enter the chat Worker's address and key once. The conversation stays on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onConnect) { Text("Connect") }
    }
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    researchMode: Boolean,
    researchEnabled: Boolean,
    onToggleResearch: () -> Unit,
    onSend: () -> Unit,
) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.imePadding()) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (researchEnabled) {
                FilterChip(
                    selected = researchMode,
                    onClick = onToggleResearch,
                    label = { Text(if (researchMode) "Web research on — sends only your question" else "Web research") },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null, modifier = Modifier.size(16.dp)) },
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 2000) onDraftChange(it) },
                    placeholder = { Text(if (researchMode) "Ask the web a question…" else "Ask your coach…") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    maxLines = 5,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (draft.isNotBlank() && !sending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant)
                        .clickable(enabled = draft.isNotBlank() && !sending, onClick = onSend),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = if (draft.isNotBlank() && !sending) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatConnectDialog(
    suggestedAddress: String?,
    suggestedKey: String?,
    error: String?,
    onDismiss: () -> Unit,
    onConnect: (String, String) -> Unit,
) {
    var address by remember { mutableStateOf(suggestedAddress.orEmpty()) }
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Connect the coach chat") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter the chat Worker's address and its key. The key stays on this phone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    placeholder = { Text("https://royal-miles-chat.….workers.dev") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Chat key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (suggestedKey != null) {
                    TextButton(onClick = { key = suggestedKey }) { Text("Use the same key as the coach feed") }
                }
                error?.let { Text(it, color = BlushPink, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(address, key) }, enabled = address.isNotBlank() && key.isNotBlank()) { Text("Connect") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun formatKm(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else String.format("%.1f", value)
