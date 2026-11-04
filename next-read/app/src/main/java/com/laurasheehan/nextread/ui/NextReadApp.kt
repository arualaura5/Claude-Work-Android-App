package com.laurasheehan.nextread.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.laurasheehan.nextread.BuildConfig
import kotlinx.coroutines.launch

private enum class Tab(val label: String) { DISCOVER("Discover"), CHECK("Check"), SAVED("Saved"), SHELVES("My shelves") }

@Suppress("DEPRECATION") // Outlined.FactCheck / LibraryBooks moved to AutoMirrored; these still draw the same.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NextReadApp(vm: AppViewModel) {
    val notebook by vm.notebook.collectAsState()
    val discover by vm.discover.collectAsState()
    val check by vm.check.collectAsState()
    val profile by vm.profile.collectAsState()
    val libby by vm.libby.collectAsState()
    val connected by vm.connected.collectAsState()
    val message by vm.message.collectAsState()
    var tab by rememberSaveable { mutableStateOf(Tab.DISCOVER) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }
    LaunchedEffect(Unit) { if (!connected) settingsOpen = true }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        buildAnnotatedString {
                            append("Next ")
                            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("Read") }
                        },
                        style = MaterialTheme.typography.displaySmall,
                    )
                },
                actions = { IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Outlined.Settings, "Settings") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
                Tab.entries.forEach { t ->
                    val icon = when (t) {
                        Tab.DISCOVER -> Icons.Outlined.AutoAwesome
                        Tab.CHECK -> Icons.Outlined.FactCheck
                        Tab.SAVED -> Icons.Outlined.BookmarkBorder
                        Tab.SHELVES -> Icons.Outlined.LibraryBooks
                    }
                    val label = if (t == Tab.SAVED && notebook.saved.isNotEmpty()) "Saved ${notebook.saved.size}" else t.label
                    NavigationBarItem(
                        selected = tab == t, onClick = { tab = t },
                        icon = { Icon(icon, contentDescription = null) }, label = { Text(label) },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            when (tab) {
                Tab.DISCOVER -> discoverTab(vm, notebook, libby, discover)
                Tab.CHECK -> checkTab(vm, notebook, libby, check)
                Tab.SAVED -> savedTab(vm, notebook, libby)
                Tab.SHELVES -> shelvesTab(vm, notebook, profile)
            }
        }
    }

    if (settingsOpen) SettingsDialog(vm, connected) { settingsOpen = false }
}

/**
 * Where she connects the app to her Next Read Worker. The key is never built into the app,
 * because this repository and its APKs are public.
 */
@Composable
private fun SettingsDialog(vm: AppViewModel, connected: Boolean, onClose: () -> Unit) {
    var address by rememberSaveable { mutableStateOf(vm.savedAddress) }
    var key by rememberSaveable { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Books service") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (connected) "Connected. Suggestions and checks go to Claude on your plan through your Next Read Worker."
                    else "Paste your Next Read Worker's address and books key. Both are in the setup notes.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = address, onValueChange = { address = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Worker address") }, placeholder = { Text("next-read.<you>.workers.dev") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = key, onValueChange = { key = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (connected) "Books key (leave blank to keep)" else "Books key") },
                    visualTransformation = PasswordVisualTransformation(),
                )
                status?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted)
            }
        },
        confirmButton = {
            Button(enabled = !busy && address.isNotBlank() && (key.isNotBlank() || connected), onClick = {
                busy = true
                status = null
                scope.launch {
                    val problem = vm.connect(address, key)
                    busy = false
                    if (problem == null) onClose() else status = problem
                }
            }) { Text(if (busy) "Connecting…" else "Connect") }
        },
        dismissButton = {
            Row {
                if (connected) TextButton(onClick = { vm.disconnect(); onClose() }) { Text("Disconnect") }
                TextButton(onClick = onClose) { Text("Close") }
            }
        },
    )
}
