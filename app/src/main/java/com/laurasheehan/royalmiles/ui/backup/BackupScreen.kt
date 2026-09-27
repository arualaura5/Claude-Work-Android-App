package com.laurasheehan.royalmiles.ui.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.laurasheehan.royalmiles.data.backup.BackupPreview
import com.laurasheehan.royalmiles.data.backup.SafetyCopy
import com.laurasheehan.royalmiles.ui.theme.BlushPink
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BackupScreen(viewModel: BackupViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri?.let(viewModel::save)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.check(it) }
    }
    BackupContent(
        state = state,
        onBack = onBack,
        onSave = { saveLauncher.launch(viewModel.suggestedFileName()) },
        onPickFile = { openLauncher.launch(arrayOf("*/*")) },
        onPickCopy = viewModel::check,
        onConfirm = viewModel::confirmRestore,
        onCancel = viewModel::cancelRestore,
    )
}

/** Stateless, so it can be rendered in screenshots. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupContent(
    state: BackupUiState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onPickFile: () -> Unit,
    onPickCopy: (SafetyCopy) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
) {
    state.pending?.let { pending ->
        AlertDialog(
            onDismissRequest = onCancel,
            title = { Text("Replace your training log?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("From ${state.pendingSource}:", style = MaterialTheme.typography.bodyMedium)
                    PreviewLines(pending.preview)
                    Text(
                        "Restoring replaces your whole training log with this one; nothing is merged. " +
                            "A copy of your current log is kept first, so you can undo it.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { Button(onClick = onConfirm) { Text("Replace my log") } },
            dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Back up & restore") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Section("Back up your training log") {
                    Text(
                        "Saves every session, completion, figure and rating, your events and your profile to one file. " +
                            "Keep it somewhere off this phone, like Google Drive. Coach, chat and keys aren't included.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        state.lastSavedAtMillis?.let { "Last saved: ${BackupText.ago(it, nowMillis)}" } ?: "You haven't saved a backup yet.",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (state.lastSavedAtMillis == null) BlushPink else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onSave, enabled = state.working == null) { Text("Save a backup…") }
                }
            }
            item {
                Section("Restore from a backup") {
                    Text(
                        "Checks the file first: that it's complete, undamaged and readable by this version. " +
                            "Older backups are brought up to date the same way the app upgrades itself. " +
                            "You see what's in it before anything changes.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedButton(onClick = onPickFile, enabled = state.working == null) { Text("Choose a backup file…") }
                }
            }
            state.working?.let { label ->
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            state.message?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium) } }
            state.problem?.let { item { Text(it, style = MaterialTheme.typography.bodyMedium, color = BlushPink) } }
            item {
                Section("Copies the app kept for you") {
                    Text(
                        "Made automatically before the app upgrades its database and before any restore. The newest three of each are kept on this phone.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (state.safetyCopies.isEmpty()) {
                        Text("None yet.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            items(state.safetyCopies, key = { it.file.name }) { copy ->
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(copy.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                BackupText.timestamp(copy.createdAtMillis),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { onPickCopy(copy) }, enabled = state.working == null) { Text("Restore…") }
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun PreviewLines(preview: BackupPreview) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(BackupViewModel.describe(preview), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        preview.createdAt?.let {
            Text(
                "Saved ${BackupText.timestamp(runCatching { Instant.parse(it).toEpochMilli() }.getOrDefault(0L))}" +
                    (preview.appBuild?.let { build -> " by build $build" } ?: ""),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        preview.upgradedFrom?.let {
            Text(
                "Made by an older version (database v$it); brought up to date and checked.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal object BackupText {
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val timestampFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

    fun date(date: LocalDate): String = date.format(dateFormat)

    fun timestamp(millis: Long): String = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(timestampFormat)

    fun ago(millis: Long, now: Long): String {
        val days = ((now - millis) / 86_400_000L).toInt()
        return when {
            days <= 0 -> "today"
            days == 1 -> "yesterday"
            days < 30 -> "$days days ago"
            else -> "$days days ago, worth saving a new one"
        }
    }
}
