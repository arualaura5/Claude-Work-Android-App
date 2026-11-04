package com.laurasheehan.nextread.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.laurasheehan.nextread.data.shortTitle
import com.laurasheehan.nextread.net.Libby
import com.laurasheehan.nextread.net.LibbyFormat
import java.net.URLEncoder
import java.text.NumberFormat
import java.util.Locale

/** Everything a card shows, whether it came from Discover, Check or the Saved list. */
data class CardBook(
    val title: String,
    val author: String,
    val number: Int? = null,
    val verdict: String = "",
    val fit: Int? = null,
    val onShelf: String = "",
    val meta: String = "",
    val plot: String = "",
    val why: String = "",
    val watchOut: String = "",
    val tags: List<String> = emptyList(),
    val reception: String = "",
    val grRating: Double? = null,
    val grCount: Long? = null,
    val grId: String? = null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookCard(
    book: CardBook,
    libby: LibbyUi?,
    loadLibby: () -> Unit,
    actions: @Composable () -> Unit,
) {
    val accents = LocalAccents.current
    LaunchedEffect(book.title, book.author) { loadLibby() }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (book.verdict.isNotEmpty()) VerdictRow(book) else if (book.number != null) {
                Text("#${book.number}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Column {
                Text(book.title, style = MaterialTheme.typography.headlineSmall)
                if (book.author.isNotBlank()) Text(book.author, style = MaterialTheme.typography.bodyMedium, color = accents.muted)
            }
            if (book.meta.isNotBlank()) Text(book.meta.uppercase(), style = MaterialTheme.typography.labelSmall, color = accents.muted)
            RatingLine(book)
            if (book.plot.isNotBlank()) Labelled("The story", book.plot)
            if (book.why.isNotBlank()) Labelled("For you", book.why)
            if (book.watchOut.isNotBlank()) {
                Text(
                    "Watch out: ${book.watchOut}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.warn,
                    modifier = Modifier.fillMaxWidth()
                        .background(accents.pinkSoft, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 9.dp),
                )
            }
            if (book.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    book.tags.forEach { tag ->
                        Text(
                            tag,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }
            if (book.reception.isNotBlank()) Text(book.reception, style = MaterialTheme.typography.bodySmall, color = accents.muted)
            LibbyLine(libby)
            Links(book)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            actions()
        }
    }
}

@Composable
private fun VerdictRow(book: CardBook) {
    val accents = LocalAccents.current
    val (label, bg, fg) = when (book.verdict) {
        "read" -> Triple("Read it", accents.good, MaterialTheme.colorScheme.background)
        "skip" -> Triple("Skip it", accents.warn, MaterialTheme.colorScheme.background)
        "maybe" -> Triple("Maybe", accents.apricot, accents.onApricot)
        else -> Triple("Not sure which book", accents.apricot, accents.onApricot)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
            modifier = Modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
        )
        book.fit?.let { Text("$it/10 match", style = MaterialTheme.typography.bodySmall, color = accents.muted) }
        when (book.onShelf) {
            "read" -> Text("you've read this", style = MaterialTheme.typography.bodySmall, color = accents.muted)
            "want-to-read" -> Text("on your want-to-read", style = MaterialTheme.typography.bodySmall, color = accents.muted)
        }
    }
}

@Composable
private fun Labelled(label: String, text: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = LocalAccents.current.muted)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Only ever a real Goodreads figure; when there isn't one, it says so rather than guessing. */
@Composable
private fun RatingLine(book: CardBook) {
    val accents = LocalAccents.current
    val rating = book.grRating
    if (rating == null) {
        Text("No Goodreads rating found. Tap Goodreads to look it up.", style = MaterialTheme.typography.bodySmall, color = accents.muted)
        return
    }
    val count = book.grCount?.let { " · " + NumberFormat.getIntegerInstance(Locale.UK).format(it) + " ratings" }.orEmpty()
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)) {
                append("★ " + String.format(Locale.UK, "%.2f", rating))
            }
            withStyle(SpanStyle(color = accents.muted)) { append(" on Goodreads$count") }
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun LibbyLine(libby: LibbyUi?) {
    val accents = LocalAccents.current
    val (text, color) = when (libby) {
        null, LibbyUi.Loading -> "Checking Idea Store's Libby…" to accents.muted
        LibbyUi.Missing -> "Libby: not in Idea Store's ebook or audiobook collection" to accents.muted
        LibbyUi.Unavailable -> "Libby: couldn't check just now" to accents.muted
        is LibbyUi.Found -> ("Libby: " + libby.formats.joinToString(" · ") { describe(it) }) to
            (if (libby.formats.any { it.available }) accents.good else accents.muted)
    }
    Text(text, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.SemiBold)
}

private fun describe(f: LibbyFormat): String {
    val kind = if (f.type == "audiobook") "audiobook" else "eBook"
    return when {
        f.available -> "$kind available now"
        f.ownedCopies == 0 -> "$kind waitlist, no copies yet"
        f.waitDays != null -> "$kind ~${f.waitDays} day wait"
        else -> "$kind on hold"
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Links(book: CardBook) {
    val uri = LocalUriHandler.current
    val accents = LocalAccents.current
    val q = URLEncoder.encode("${shortTitle(book.title)} ${book.author}".trim(), "UTF-8")
    val goodreads = book.grId?.let { "https://www.goodreads.com/book/show/$it" } ?: "https://www.goodreads.com/search?q=$q"
    val ideaStore = "https://ideastore.towerhamlets.gov.uk/search?term=" +
        URLEncoder.encode(shortTitle(book.title), "UTF-8") + "&field=TITLE"
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilledTonalButton(onClick = { uri.openUri(Libby.searchUrl(book.title, book.author)) }) { Text("Libby") }
        AssistChip(onClick = { uri.openUri(ideaStore) }, label = { Text("Idea Store", color = accents.apricotText) })
        AssistChip(onClick = { uri.openUri(goodreads) }, label = { Text("Goodreads", color = accents.apricotText) })
    }
}

/** Save and "Not for me", used on Discover and Check. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SuggestionActions(saved: Boolean, onSave: () -> Unit, onReject: (String) -> Unit) {
    var asking by remember { mutableStateOf(false) }
    if (asking) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Why isn't it for you?", style = MaterialTheme.typography.bodySmall, color = LocalAccents.current.muted)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("Already read it", "Too dark", "Too long", "Not my style", "Seen enough like this").forEach { reason ->
                    AssistChip(onClick = { onReject(reason) }, label = { Text(reason) })
                }
                TextButton(onClick = { asking = false }) { Text("Cancel") }
            }
        }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (saved) Text("✓ Saved", color = LocalAccents.current.good, fontWeight = FontWeight.SemiBold)
        else Button(onClick = onSave) { Text("Save") }
        OutlinedButton(onClick = { asking = true }) { Text("Not for me") }
    }
}
