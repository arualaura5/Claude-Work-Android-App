package com.laurasheehan.nextread.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.laurasheehan.nextread.data.Notebook
import com.laurasheehan.nextread.data.ShelfBook
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

fun LazyListScope.savedTab(vm: AppViewModel, notebook: Notebook, libby: Map<String, LibbyUi>) {
    item {
        Column {
            Text("Saved for later", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Books you liked the sound of. Check Libby or the Idea Store catalogue, then add them to Goodreads when you're ready.",
                style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted,
            )
        }
    }
    if (notebook.saved.isEmpty()) {
        item { Panel { Text("Nothing saved yet. Tap Save on any suggestion and it lands here.") } }
    }
    items(notebook.saved, key = { it.id }) { s ->
        val shelf = notebook.shelfBookTitled(s.title)
        BookCard(
            book = CardBook(
                title = s.title, author = s.author, plot = s.plot, why = s.why, tags = s.tags,
                grRating = s.grRating ?: shelf?.grRating, grCount = if (s.grRating != null) s.grCount else shelf?.grCount, grId = s.grId,
            ),
            libby = libby[vm.libbyKey(s.title, s.author)],
            loadLibby = { vm.libbyFor(s.title, s.author) },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (s.done) Button(onClick = { vm.toggleDone(s.id) }) { Text("✓ On Goodreads") }
                else OutlinedButton(onClick = { vm.toggleDone(s.id) }) { Text("Mark added to Goodreads") }
                TextButton(onClick = { vm.removeSaved(s.id) }) { Text("Remove") }
            }
        }
    }
}

@Composable
fun Panel(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { content() }
    }
}

@Composable
private fun Heading(text: String) =
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = LocalAccents.current.muted)

private fun prettyDate(iso: String): String = runCatching {
    DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.UK).format(Instant.parse(iso).atZone(ZoneId.systemDefault()))
}.getOrDefault("")

fun LazyListScope.shelvesTab(vm: AppViewModel, notebook: Notebook, profileJob: JobUi) {
    val read = notebook.books.filter { it.isRead }.sortedByDescending { it.myRating }
    val wanted = notebook.books.filter { it.isWantToRead }
    item {
        val context = LocalContext.current
        val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                context.contentResolver.openInputStream(uri)?.use { vm.importFile(it.bufferedReader().readText()) }
            }
        }
        val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            if (uri != null) {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(vm.exportText().toByteArray(Charsets.UTF_8)) }
                vm.exported()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("Your reading", style = MaterialTheme.typography.headlineSmall)
                val from = prettyDate(notebook.importedAt)
                Text(
                    if (notebook.books.isEmpty()) "No books yet. Import your Goodreads export or a Next Read backup below."
                    else "From your Goodreads export" + if (from.isNotEmpty()) " of $from." else ".",
                    style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Stat(read.size.toString(), "read", Modifier.weight(1f))
                Stat(wanted.size.toString(), "want to read", Modifier.weight(1f))
                val rated = notebook.books.filter { it.myRating > 0 }
                val avg = if (rated.isEmpty()) "–" else String.format(Locale.UK, "%.1f", rated.map { it.myRating }.average())
                Stat(avg, "your avg ★", Modifier.weight(1f))
            }
            Panel {
                Heading("What you want from a book")
                var taste by rememberSaveable(notebook.taste) { mutableStateOf(notebook.taste) }
                Text("In your own words. Claude treats this as its most important guide.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = taste, onValueChange = { taste = it }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Button(onClick = { vm.saveTaste(taste) }, enabled = taste.trim() != notebook.taste) { Text("Save") }
            }
            Panel {
                Heading("Your reading style")
                Text(notebook.profile.ifBlank { "No summary yet. Claude can read your shelves and notes and describe your taste." })
                Button(onClick = { vm.describeStyle() }, enabled = !profileJob.running && notebook.books.isNotEmpty()) {
                    Text("Describe my style")
                }
                JobStatus(profileJob) { vm.stop(AppViewModel.PROFILE) }
            }
            Panel {
                Heading("Import and back up")
                Text(
                    "Import a Goodreads export (My Books → Import and export → Export Library) or a Next Read backup. " +
                        "A Goodreads import keeps your notes, saved books and ratings.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { importer.launch(arrayOf("*/*")) }) { Text("Import a file") }
                    OutlinedButton(onClick = { exporter.launch("next-read-backup.json") }) { Text("Back up") }
                }
            }
            if (notebook.notForMe.isNotEmpty()) {
                Panel {
                    Heading("Not for you (${notebook.notForMe.size})")
                    notebook.notForMe.forEachIndexed { i, r ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(r.title)
                                Text(listOf(r.author, r.why).filter { it.isNotBlank() }.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted)
                            }
                            TextButton(onClick = { vm.undoReject(i) }) { Text("Undo") }
                        }
                    }
                }
            }
            Heading("Read · tell Claude what you thought")
        }
    }
    items(read, key = { "read-" + it.key }) { b -> ReadRow(vm, notebook, b) }
    item { Column(Modifier.padding(top = 12.dp)) { Heading("Want to read (${wanted.size})") } }
    items(wanted, key = { "want-" + it.key }) { b -> SimpleRow(b) }
}

@Composable
private fun Stat(value: String, label: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.displaySmall)
            Text(label, style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted)
        }
    }
}

@Composable
private fun BookHead(b: ShelfBook) {
    Row(verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(b.title, style = MaterialTheme.typography.bodyLarge)
            val gr = b.grRating?.let { " · ★ " + String.format(Locale.UK, "%.2f", it) + " Goodreads" }.orEmpty()
            Text(b.author + gr, style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted)
        }
        if (b.myRating > 0) {
            Text("★".repeat(b.myRating), color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun ReadRow(vm: AppViewModel, notebook: Notebook, b: ShelfBook) {
    var editing by remember { mutableStateOf(false) }
    val note = notebook.noteFor(b)
    Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BookHead(b)
        if (editing) {
            var draft by rememberSaveable(b.key) { mutableStateOf(note) }
            OutlinedTextField(
                value = draft, onValueChange = { draft = it }, modifier = Modifier.fillMaxWidth(), minLines = 2,
                label = { Text("Your thoughts") },
                placeholder = { Text("Pace, tone, writing style, length… what worked and what didn't") },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveNote(b, draft); editing = false }) { Text("Save") }
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        } else {
            if (note.isNotBlank()) Text(note, style = NoteStyle)
            TextButton(onClick = { editing = true }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(if (note.isBlank()) "Add your thoughts" else "Edit thoughts")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SimpleRow(b: ShelfBook) {
    Column(Modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BookHead(b)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
