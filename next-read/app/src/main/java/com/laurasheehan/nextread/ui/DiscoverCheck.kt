package com.laurasheehan.nextread.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.laurasheehan.nextread.ai.Prompts
import com.laurasheehan.nextread.ai.Suggestion
import com.laurasheehan.nextread.data.Notebook
import com.laurasheehan.nextread.data.shortTitle
import java.io.File

/** A suggestion as a card. The rating is the Action's Goodreads lookup, or her shelf's stored one. */
fun Suggestion.toCard(index: Int, notebook: Notebook): CardBook {
    val shelf = notebook.shelfBookTitled(title)
    return CardBook(
        title = title,
        author = author,
        number = index + 1,
        verdict = verdict,
        fit = fit,
        onShelf = onShelf,
        meta = listOfNotNull(year?.toString(), pages?.let { "$it pages" }, similarTo.takeIf { it.isNotBlank() }?.let { "like $it" })
            .joinToString(" · "),
        plot = plot,
        why = why,
        watchOut = watchOut,
        tags = tags,
        reception = reception,
        grRating = grRating ?: shelf?.grRating,
        grCount = if (grRating != null) grCount else shelf?.grCount,
        grId = grId ?: shelf?.id?.takeIf { it.isNotBlank() },
    )
}

/** The cards for an answer, shared by Discover and Check. */
fun LazyListScope.answerCards(vm: AppViewModel, job: JobUi, notebook: Notebook, libby: Map<String, LibbyUi>) {
    val answer = job.answer ?: return
    if (answer.intro.isNotBlank()) {
        item { Text(answer.intro, style = NoteStyle) }
    }
    itemsIndexed(answer.books, key = { i, s -> "$i-${s.title}" }) { i, s ->
        BookCard(
            book = s.toCard(i, notebook),
            libby = libby[vm.libbyKey(s.title, s.author)],
            loadLibby = { vm.libbyFor(s.title, s.author) },
        ) {
            SuggestionActions(saved = notebook.saved.any { it.title == s.title && it.author == s.author },
                onSave = { vm.save(s) }, onReject = { vm.reject(s, it) })
        }
    }
}

@Composable
fun JobStatus(job: JobUi, onStop: () -> Unit) {
    if (job.running) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(job.status, style = MaterialTheme.typography.bodyMedium, color = LocalAccents.current.muted, modifier = Modifier.weight(1f))
            TextButton(onClick = onStop) { Text("Stop") }
        }
    }
    job.error?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
fun LazyListScope.discoverTab(vm: AppViewModel, notebook: Notebook, libby: Map<String, LibbyUi>, job: JobUi) {
    item {
        var mood by rememberSaveable { mutableStateOf("") }
        var chosen by rememberSaveable { mutableStateOf(listOf<String>()) }
        var moreLike by rememberSaveable { mutableStateOf("") }
        var menuOpen by remember { mutableStateOf(false) }
        var adjust by rememberSaveable { mutableStateOf("") }
        val readBooks = notebook.books.filter { it.isRead }.sortedByDescending { it.myRating }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("What do you feel like reading?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Describe a mood or style, tap a few chips, or pick a book you loved. Claude reads your shelves and never suggests one you already have.",
                    style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted,
                )
            }
            OutlinedTextField(
                value = mood, onValueChange = { mood = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("In your own words") },
                placeholder = { Text("e.g. a creepy page-turner I can't put down, under 400 pages") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Prompts.CHIPS.forEach { chip ->
                    FilterChip(
                        selected = chip in chosen,
                        onClick = { chosen = if (chip in chosen) chosen - chip else chosen + chip },
                        label = { Text(chip) },
                    )
                }
            }
            ExposedDropdownMenuBox(expanded = menuOpen, onExpandedChange = { menuOpen = it }) {
                OutlinedTextField(
                    value = moreLike.ifEmpty { "Any of my books" }, onValueChange = {}, readOnly = true,
                    label = { Text("More like a book I've read") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuOpen) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Any of my books") }, onClick = { moreLike = ""; menuOpen = false })
                    readBooks.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(shortTitle(b.title) + if (b.myRating > 0) "  " + "★".repeat(b.myRating) else "") },
                            onClick = { moreLike = "${b.title} — ${b.author}"; menuOpen = false },
                        )
                    }
                }
            }
            Button(onClick = { vm.findBooks(mood, chosen.toSet(), moreLike) }, enabled = !job.running) { Text("Find 6 books") }
            JobStatus(job) { vm.stop(AppViewModel.DISCOVER) }
            if (job.answer != null && !job.running && vm.canRefine) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = adjust, onValueChange = { adjust = it }, modifier = Modifier.weight(1f), singleLine = true,
                        label = { Text("Not quite? Adjust them") },
                        placeholder = { Text("darker · shorter · more like #2") },
                    )
                    OutlinedButton(onClick = { vm.refine(adjust); adjust = "" }, enabled = adjust.isNotBlank()) { Text("Adjust") }
                }
            }
        }
    }
    answerCards(vm, job, notebook, libby)
}

@OptIn(ExperimentalLayoutApi::class)
fun LazyListScope.checkTab(vm: AppViewModel, notebook: Notebook, libby: Map<String, LibbyUi>, job: JobUi) {
    item {
        val context = LocalContext.current
        var text by rememberSaveable { mutableStateOf("") }
        var photo by remember { mutableStateOf<ByteArray?>(null) }
        var photoError by remember { mutableStateOf<String?>(null) }
        var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
        fun usePhoto(uri: Uri?) {
            if (uri == null) return
            photo = Photos.prepare(context, uri)
            photoError = if (photo == null) "That photo couldn't be opened. Try another." else null
        }
        val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { usePhoto(it) }
        val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
            if (ok) usePhoto(cameraUri?.let(Uri::parse))
        }

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Should I read it?", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Type a book, paste a list with one per line, or photograph a cover or a whole shelf. Claude checks each one against your taste and notes.",
                    style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted,
                )
            }
            OutlinedTextField(
                value = text, onValueChange = { text = it }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                label = { Text("Books to check") },
                placeholder = { Text("Fahrenheit 451\nThe Silent Patient, Alex Michaelides") },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                    val file = File(dir, "shelf-${System.currentTimeMillis()}.jpg")
                    val uri = FileProvider.getUriForFile(context, context.packageName + ".photos", file)
                    cameraUri = uri.toString()
                    camera.launch(uri)
                }) { Text("Take a photo") }
                OutlinedButton(onClick = {
                    pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("Choose a photo") }
                if (photo != null) TextButton(onClick = { photo = null }) { Text("Remove photo") }
            }
            photo?.let { bytes ->
                remember(bytes) { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }?.let { bmp ->
                    Image(
                        bitmap = bmp.asImageBitmap(), contentDescription = "Your book photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            photoError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(
                onClick = {
                    val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(15)
                    vm.checkBooks(lines, photo)
                },
                enabled = !job.running && (text.isNotBlank() || photo != null),
            ) { Text("Check these") }
            JobStatus(job) { vm.stop(AppViewModel.CHECK) }
        }
    }
    answerCards(vm, job, notebook, libby)
}
