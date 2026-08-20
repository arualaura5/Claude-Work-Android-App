package com.arabicflashcards.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Lightweight inline markup for card text:
 *  - *asterisks* render bold, e.g. "C*A*LR" bolds just the root letters.
 *  - _underscores_ render italic, e.g. "_lit._ street".
 *  - Nest them for both, e.g. "*_Female_*" renders bold AND italic.
 * An unmatched trailing marker is treated as plain text rather than an error.
 *
 * Bold spans also get a color accent on top of the font weight, purely for
 * visual pop (transliteration diacritics like ū/ḥ/š render at real weight
 * too — their glyphs are composed into the bundled Tajawal font files from
 * Tajawal's own letterforms and accent marks, since Tajawal itself doesn't
 * include them; see the font-file note in README.md).
 */
fun parseCardMarkup(text: String, boldColor: Color = Color.Unspecified): AnnotatedString =
    buildAnnotatedString {
        appendMarkedUp(text, boldColor)
    }

private fun AnnotatedString.Builder.appendMarkedUp(text: String, boldColor: Color) {
    var index = 0
    while (index < text.length) {
        val nextBold = text.indexOf('*', index)
        val nextItalic = text.indexOf('_', index)
        val start = when {
            nextBold == -1 -> nextItalic
            nextItalic == -1 -> nextBold
            else -> minOf(nextBold, nextItalic)
        }
        if (start == -1) {
            append(text.substring(index))
            return
        }
        val marker = text[start]
        val end = text.indexOf(marker, start + 1)
        if (end == -1) {
            append(text.substring(index))
            return
        }
        append(text.substring(index, start))
        val style = if (marker == '*') {
            SpanStyle(fontWeight = FontWeight.Black, color = boldColor)
        } else {
            SpanStyle(fontStyle = FontStyle.Italic)
        }
        withStyle(style) {
            appendMarkedUp(text.substring(start + 1, end), boldColor)
        }
        index = end + 1
    }
}
