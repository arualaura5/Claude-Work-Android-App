@file:OptIn(ExperimentalMaterial3Api::class)

package com.arabicflashcards.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.arabicflashcards.app.audio.LocalSoundPlayer
import com.arabicflashcards.app.audio.SoundPlayer
import com.arabicflashcards.app.data.GameStats
import com.arabicflashcards.app.ui.theme.MashrabiyaBackground
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun FlashcardApp(viewModel: FlashcardViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    var selectedTab by rememberSaveable { mutableStateOf(0) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val soundPlayer = remember { SoundPlayer(context) }
    DisposableEffect(Unit) {
        onDispose { soundPlayer.release() }
    }
    LaunchedEffect(state.soundEnabled) {
        soundPlayer.enabled = state.soundEnabled
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val json = viewModel.exportBackupJson()
        coroutineScope.launch {
            val success = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }.isSuccess
            }
            if (success) viewModel.notifyBackupSaved() else viewModel.notifyBackupFailed()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                }.getOrNull()
            }
            if (text != null) viewModel.importBackup(text) else viewModel.notifyBackupFailed()
        }
    }

    LaunchedEffect(statusMessage) {
        statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeStatusMessage()
        }
    }

    CompositionLocalProvider(LocalSoundPlayer provides soundPlayer) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Arabic Flashcards", fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    actions = {
                        IconButton(onClick = { viewModel.setSoundEnabled(!state.soundEnabled) }) {
                            Icon(
                                if (state.soundEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                                contentDescription = if (state.soundEnabled) "Mute sounds" else "Unmute sounds",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        IconButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
                            Icon(Icons.Default.Upload, contentDescription = "Import backup", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = { exportLauncher.launch("arabic-flashcards-backup.json") }) {
                            Icon(Icons.Default.Download, contentDescription = "Export backup", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
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
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.MenuBook, contentDescription = "Lessons") },
                    label = { Text("Lessons") }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            MashrabiyaBackground(modifier = Modifier.fillMaxSize())
            when (selectedTab) {
                0 -> StudyScreen(
                    state = state,
                    onFlip = viewModel::flip,
                    onSwipeLeft = viewModel::next,
                    onSwipeRight = viewModel::previous,
                    onDirectionChange = viewModel::setDirection,
                    onGrade = viewModel::gradeCurrent,
                    onCelebrationDone = viewModel::consumeCelebration,
                    onToggleTag = viewModel::toggleTagFilter,
                    onClearTagFilter = viewModel::clearTagFilter,
                    onGoToCards = { selectedTab = 1 }
                )
                1 -> ManageCardsScreen(
                    cards = state.cards,
                    allTags = state.allTags,
                    direction = state.direction,
                    onAdd = viewModel::addCard,
                    onEdit = viewModel::updateCard,
                    onDelete = viewModel::deleteCard,
                    onCreateTag = viewModel::addTag,
                    onDeleteTag = viewModel::deleteTag
                )
                else -> LessonsScreen(
                    state = state,
                    onCreateLesson = viewModel::createLesson,
                    onRenameLesson = viewModel::renameLesson,
                    onDeleteLesson = viewModel::deleteLesson,
                    onSetLessonCards = viewModel::setLessonCards,
                    onReorderLessons = viewModel::reorderLessons,
                    onGradeCard = viewModel::gradeCard,
                    onCelebrationDone = viewModel::consumeCelebration
                )
            }
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
        StatChip(icon = Icons.Default.LocalFireDepartment, label = "${stats.currentStreak} day streak", tint = MaterialTheme.colorScheme.tertiary)
        StatChip(icon = Icons.Default.Star, label = "${stats.totalPoints} pts", tint = MaterialTheme.colorScheme.secondary)
        Text(
            text = "Lv. ${stats.level}",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun StatChip(icon: ImageVector, label: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.height(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}
