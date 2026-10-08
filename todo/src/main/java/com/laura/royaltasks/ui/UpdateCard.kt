package com.laura.royaltasks.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.laura.royaltasks.update.AvailableUpdate
import com.laura.royaltasks.update.UpdateState

/** "Update available" under the hero on the Tasks tab. Shows nothing when there's no update. */
@Composable
fun UpdateCard(
    state: UpdateState,
    onInstall: () -> Unit,
    onAllowInstalls: () -> Unit,
    onLater: () -> Unit
) {
    val update: AvailableUpdate = when (state) {
        UpdateState.None, UpdateState.Checking, UpdateState.UpToDate, UpdateState.Unreachable -> return
        is UpdateState.Available -> state.update
        is UpdateState.Downloading -> state.update
        is UpdateState.NeedsPermission -> state.update
        is UpdateState.Installing -> state.update
        is UpdateState.Failed -> state.update
    }
    val buttonColors = ButtonDefaults.buttonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary
    )
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    when (state) {
                        is UpdateState.Downloading -> "Downloading the update…"
                        is UpdateState.NeedsPermission -> "One-time permission needed"
                        is UpdateState.Installing -> "Finish in Android's install screen"
                        is UpdateState.Failed -> "The update didn't install"
                        else -> "Update available"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                when (state) {
                    is UpdateState.NeedsPermission ->
                        "Android asks once whether Royal Tasks may install its own updates. Tap Allow, " +
                            "switch it on, come back, and tap Install."
                    is UpdateState.Installing -> "Tap Update there. Your tasks, ideas and XP are kept."
                    is UpdateState.Failed -> state.message
                    else -> "Build ${update.sha}${update.builtAt?.let { " · ${it.take(10)}" } ?: ""}. " +
                        "Installs over this one; your tasks, ideas and XP are kept."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state is UpdateState.Downloading) {
                val progress = state.progress
                if (progress != null) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    when (state) {
                        is UpdateState.NeedsPermission -> Button(onClick = onAllowInstalls, colors = buttonColors) { Text("Allow") }
                        is UpdateState.Installing -> TextButton(onClick = onInstall) { Text("Open installer again") }
                        is UpdateState.Failed -> Button(onClick = onInstall, colors = buttonColors) { Text("Try again") }
                        else -> Button(onClick = onInstall, colors = buttonColors) { Text("Install") }
                    }
                    if (state is UpdateState.NeedsPermission) {
                        TextButton(onClick = onInstall) { Text("Install") }
                    }
                    TextButton(onClick = onLater) { Text("Later") }
                }
            }
        }
    }
}

/** Whether the state is something the card shows. */
val UpdateState.showsCard: Boolean
    get() = this is UpdateState.Available || this is UpdateState.Downloading ||
        this is UpdateState.NeedsPermission || this is UpdateState.Installing || this is UpdateState.Failed

/** The tappable build line at the bottom of the Tasks tab. */
fun buildLineStatus(state: UpdateState): String = when (state) {
    UpdateState.Checking -> "Checking…"
    UpdateState.UpToDate -> "Up to date"
    UpdateState.Unreachable -> "Couldn't reach GitHub"
    else -> "Check for updates"
}
