package com.arabicflashcards.app.ui

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight inline markup for card text: wrap a span in *asterisks* to
 * render it bold, e.g. "C*A*LR *A*- Female" bolds just the root letters.
 * An unmatched trailing '*' is treated as plain text rather than an error.
 */
fun parseCardMarkup(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val start = text.indexOf('*', index)
        if (start == -1) {
            append(text.substring(index))
            break
        }
        val end = text.indexOf('*', start + 1)
        if (end == -1) {
            append(text.substring(index))
            break
        }
        append(text.substring(index, start))
        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            append(text.substring(start + 1, end))
        }
        index = end + 1
    }
}
