package com.laurasheehan.royalmiles.ui.coach

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Coach and research answers with their Markdown drawn rather than shown. A [n] citation opens
 * the nth source when there is one, so it can be tapped where it is used.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    citations: List<String> = emptyList(),
    onOpenLink: (String) -> Unit = {},
) {
    val blocks = remember(text) { ChatMarkdown.parse(text) }
    val accent = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val body = style.copy(lineHeight = 21.sp)

    fun annotated(runs: List<ChatMarkdown.Run>): AnnotatedString = buildAnnotatedString {
        for (run in runs) {
            val link = run.url ?: run.citation?.let { citations.getOrNull(it - 1) }
            val span = SpanStyle(
                fontWeight = if (run.bold) FontWeight.SemiBold else null,
                fontStyle = if (run.italic) FontStyle.Italic else null,
                fontFamily = if (run.code) FontFamily.Monospace else null,
                background = if (run.code) codeBackground else Color.Unspecified,
                color = if (run.citation != null || run.url != null) accent else Color.Unspecified,
                fontSize = if (run.citation != null) 12.sp else TextUnit.Unspecified,
            )
            if (link != null) {
                withLink(LinkAnnotation.Clickable(tag = link, styles = TextLinkStyles(span)) { onOpenLink(link) }) {
                    append(run.text)
                }
            } else {
                withStyle(span) { append(run.text) }
            }
        }
    }

    Column(modifier = modifier) {
        blocks.forEachIndexed { index, block ->
            // Room between paragraphs; list items stay together as one list.
            val listItem = block is ChatMarkdown.Block.Bullet || block is ChatMarkdown.Block.Numbered
            val previous = blocks.getOrNull(index - 1)
            val afterListItem = previous is ChatMarkdown.Block.Bullet || previous is ChatMarkdown.Block.Numbered
            val gap = when {
                index == 0 -> 0.dp
                listItem && afterListItem -> 4.dp
                else -> 12.dp
            }
            if (gap > 0.dp) Spacer(Modifier.height(gap))
            when (block) {
                is ChatMarkdown.Block.Paragraph -> Text(annotated(block.runs), style = body)
                is ChatMarkdown.Block.Heading -> Text(
                    annotated(block.runs),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                is ChatMarkdown.Block.Quote -> Text(
                    annotated(block.runs),
                    style = body.copy(fontStyle = FontStyle.Italic),
                    modifier = Modifier.padding(start = 10.dp),
                )
                is ChatMarkdown.Block.Bullet -> ListItem("•", block.depth, annotated(block.runs), body)
                is ChatMarkdown.Block.Numbered -> ListItem("${block.number}.", block.depth, annotated(block.runs), body)
            }
        }
    }
}

@Composable
private fun ListItem(marker: String, depth: Int, text: AnnotatedString, style: TextStyle) {
    Row(modifier = Modifier.padding(start = (depth * 14).dp)) {
        Text(marker, style = style, modifier = Modifier.width(if (marker == "•") 14.dp else 22.dp))
        Text(text, style = style)
    }
}
