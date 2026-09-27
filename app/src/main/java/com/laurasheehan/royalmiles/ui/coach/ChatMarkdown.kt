package com.laurasheehan.royalmiles.ui.coach

/**
 * The small slice of Markdown that coach and research answers use: paragraphs, headings, bullet
 * and numbered lists, **bold**, *italic*, `code`, [links](https://…) and [1]-style citations.
 *
 * Deliberately forgiving: anything it doesn't recognise is shown as the text it is, so an answer
 * is never hidden because of how it was formatted. Pure Kotlin, so it can be tested on the JVM.
 */
object ChatMarkdown {

    sealed interface Block {
        val runs: List<Run>

        data class Paragraph(override val runs: List<Run>) : Block
        data class Heading(override val runs: List<Run>) : Block
        data class Bullet(override val runs: List<Run>, val depth: Int) : Block
        data class Numbered(val number: String, override val runs: List<Run>, val depth: Int) : Block
        data class Quote(override val runs: List<Run>) : Block
    }

    data class Run(
        val text: String,
        val bold: Boolean = false,
        val italic: Boolean = false,
        val code: Boolean = false,
        /** The source number for a [n] citation. */
        val citation: Int? = null,
        /** Where a [text](url) link points; only http(s). */
        val url: String? = null,
    )

    private val HEADING = Regex("""^#{1,6}\s+(.*)$""")
    private val BULLET = Regex("""^(\s*)[-*+•]\s+(.*)$""")
    private val NUMBERED = Regex("""^(\s*)(\d{1,3})[.)]\s+(.*)$""")
    private val QUOTE = Regex("""^>\s?(.*)$""")
    private val RULE = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val LINK = Regex("""^\[([^\]\n]+)]\((https?://[^)\s]+)\)""")
    private val CITATIONS = Regex("""^\[(\d{1,2}(?:\s*,\s*\d{1,2})*)]""")

    fun parse(text: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()

        fun flush() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(inline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val line = raw.trimEnd()
            if (line.isBlank()) {
                flush()
                continue
            }
            if (RULE.matches(line)) {
                flush()
                continue
            }
            val heading = HEADING.find(line)
            val bullet = BULLET.find(line)
            val numbered = NUMBERED.find(line)
            val quote = QUOTE.find(line)
            if (heading == null && bullet == null && numbered == null && quote == null) {
                // Each line is its own paragraph: coach answers put one paragraph on a line, and
                // run together they read as one wall of text.
                paragraph += line.trimStart()
                flush()
                continue
            }
            flush()
            blocks += when {
                heading != null -> Block.Heading(inline(heading.groupValues[1].trim().trimEnd('#').trim()))
                bullet != null -> Block.Bullet(inline(bullet.groupValues[2]), depth(bullet.groupValues[1]))
                numbered != null -> Block.Numbered(numbered.groupValues[2], inline(numbered.groupValues[3]), depth(numbered.groupValues[1]))
                else -> Block.Quote(inline(quote!!.groupValues[1]))
            }
        }
        flush()
        return blocks
    }

    private fun depth(indent: String): Int = (indent.replace("\t", "    ").length / 2).coerceAtMost(3)

    /** Inline styling. A marker without a partner later in the text is shown as itself. */
    fun inline(text: String): List<Run> {
        val runs = mutableListOf<Run>()
        val buffer = StringBuilder()
        var bold = false
        var italic = false
        var i = 0

        fun emit() {
            if (buffer.isNotEmpty()) {
                runs += Run(buffer.toString(), bold = bold, italic = italic)
                buffer.clear()
            }
        }

        while (i < text.length) {
            val c = text[i]
            val rest = text.substring(i)
            when {
                c == '\\' && i + 1 < text.length && text[i + 1] in "\\`*_[]#" -> {
                    buffer.append(text[i + 1])
                    i += 2
                }
                c == '`' && text.indexOf('`', i + 1) > i + 1 -> {
                    val end = text.indexOf('`', i + 1)
                    emit()
                    runs += Run(text.substring(i + 1, end), bold = bold, italic = italic, code = true)
                    i = end + 1
                }
                rest.startsWith("**") || rest.startsWith("__") -> {
                    val marker = rest.take(2)
                    if (bold || text.indexOf(marker, i + 2) > i + 2) {
                        emit()
                        bold = !bold
                    } else {
                        buffer.append(marker)
                    }
                    i += 2
                }
                c == '*' || c == '_' -> {
                    val opens = !italic && i + 1 < text.length && !text[i + 1].isWhitespace() &&
                        (c == '*' || i == 0 || !text[i - 1].isLetterOrDigit()) &&
                        closingItalic(text, i + 1, c) > 0
                    val closes = italic && i > 0 && !text[i - 1].isWhitespace() &&
                        (c == '*' || i + 1 >= text.length || !text[i + 1].isLetterOrDigit())
                    if (opens || closes) {
                        emit()
                        italic = !italic
                    } else {
                        buffer.append(c)
                    }
                    i += 1
                }
                c == '[' -> {
                    val link = LINK.find(rest)
                    val cites = CITATIONS.find(rest)
                    when {
                        link != null -> {
                            emit()
                            runs += Run(link.groupValues[1], bold = bold, italic = italic, url = link.groupValues[2])
                            i += link.value.length
                        }
                        cites != null -> {
                            emit()
                            cites.groupValues[1].split(',').map { it.trim().toInt() }.forEach { number ->
                                runs += Run("[$number]", citation = number)
                            }
                            i += cites.value.length
                        }
                        else -> {
                            buffer.append(c)
                            i += 1
                        }
                    }
                }
                else -> {
                    buffer.append(c)
                    i += 1
                }
            }
        }
        emit()
        return runs.filter { it.text.isNotEmpty() }
    }

    private fun closingItalic(text: String, from: Int, marker: Char): Int {
        var j = from
        while (j < text.length) {
            if (text[j] == marker && !text[j - 1].isWhitespace() && !(j + 1 < text.length && text[j + 1] == marker && marker == '*')) {
                if (marker == '*' || j + 1 >= text.length || !text[j + 1].isLetterOrDigit()) return j
            }
            j += 1
        }
        return -1
    }
}
