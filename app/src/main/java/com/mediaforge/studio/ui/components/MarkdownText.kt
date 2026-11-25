package com.mediaforge.studio.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.mediaforge.studio.ui.theme.Glass

/**
 * Lightweight, streaming-safe Markdown-to-[AnnotatedString] parser for AI Studio chat & summary replies.
 * Renders:
 * - `**bold**` -> [FontWeight.Bold] (without literal `**`)
 * - `*italic*` -> [FontStyle.Italic] (without literal `*`)
 * - `***bold italic***` -> [FontWeight.Bold] + [FontStyle.Italic]
 * - `` `inline code` `` -> monospace pill with subtle tinted background
 * - `- ` / `* ` bullet list items -> clean `• ` bullets with tidy indentation
 * - `#` / `##` / `###` headings -> emphasized bold section headings
 * - Fenced code blocks (` ``` `) -> monospace lines without backtick fence clutter
 */
fun parseMarkdownToAnnotatedString(
    markdown: String,
    codeBg: Color = Color.White.copy(alpha = 0.12f),
    codeColor: Color = Color(0xFF38BDF8),
    bulletColor: Color = Color(0xFF38BDF8)
): AnnotatedString = buildAnnotatedString {
    val lines = markdown.replace("\r\n", "\n").split('\n')
    var inCodeBlock = false
    var consecutiveBlanks = 0

    lines.forEachIndexed { idx, rawLine ->
        val trimmed = rawLine.trim()
        if (trimmed.startsWith("```")) {
            inCodeBlock = !inCodeBlock
            return@forEachIndexed
        }

        if (trimmed.isEmpty()) {
            consecutiveBlanks++
            if (consecutiveBlanks <= 1 && length > 0) {
                append('\n')
            }
            return@forEachIndexed
        }
        consecutiveBlanks = 0

        if (length > 0 && !toString().endsWith("\n")) {
            append('\n')
        }

        if (inCodeBlock) {
            pushStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBg,
                    color = codeColor,
                    fontSize = 12.5.sp
                )
            )
            append("  $rawLine  ")
            pop()
            return@forEachIndexed
        }

        // Heading (# / ## / ###)
        val headingMatch = HEADING_REGEX.matchEntire(trimmed)
        if (headingMatch != null) {
            val level = headingMatch.groupValues[1].length
            val headingBody = headingMatch.groupValues[2].trim()
            pushStyle(
                SpanStyle(
                    fontWeight = FontWeight.Bold,
                    fontSize = when (level) {
                        1 -> 16.5.sp
                        2 -> 15.5.sp
                        else -> 14.5.sp
                    }
                )
            )
            appendInlineMarkdown(headingBody, codeBg, codeColor)
            pop()
            if (idx < lines.lastIndex) append('\n')
            return@forEachIndexed
        }

        // Unordered bullet list (- item / * item / • item)
        val bulletMatch = BULLET_REGEX.matchEntire(rawLine)
        if (bulletMatch != null) {
            val indentSpaces = bulletMatch.groupValues[1].length
            val itemBody = bulletMatch.groupValues[2].trim()
            val prefix = if (indentSpaces >= 2) "    ◦  " else "  •  "
            pushStyle(SpanStyle(color = bulletColor, fontWeight = FontWeight.Bold))
            append(prefix)
            pop()
            appendInlineMarkdown(itemBody, codeBg, codeColor)
            return@forEachIndexed
        }

        // Numbered list (1. item)
        val numberedMatch = NUMBERED_REGEX.matchEntire(rawLine)
        if (numberedMatch != null) {
            val indentSpaces = numberedMatch.groupValues[1].length
            val numLabel = numberedMatch.groupValues[2]
            val itemBody = numberedMatch.groupValues[3].trim()
            val pad = if (indentSpaces >= 2) "    " else "  "
            pushStyle(SpanStyle(color = bulletColor, fontWeight = FontWeight.SemiBold))
            append("$pad$numLabel ")
            pop()
            appendInlineMarkdown(itemBody, codeBg, codeColor)
            return@forEachIndexed
        }

        // Blockquote (> quote)
        if (trimmed.startsWith("> ")) {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
            append("│ ")
            appendInlineMarkdown(trimmed.removePrefix("> ").trim(), codeBg, codeColor)
            pop()
            return@forEachIndexed
        }

        appendInlineMarkdown(rawLine.trimEnd(), codeBg, codeColor)
    }
}

private fun AnnotatedString.Builder.appendInlineMarkdown(
    text: String,
    codeBg: Color,
    codeColor: Color
) {
    var i = 0
    val n = text.length
    while (i < n) {
        val ch = text[i]

        // 1. Inline code: `code`
        if (ch == '`') {
            val close = text.indexOf('`', i + 1)
            if (close > i + 1) {
                pushStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = codeBg,
                        color = codeColor
                    )
                )
                append(" ")
                append(text.substring(i + 1, close))
                append(" ")
                pop()
                i = close + 1
                continue
            }
        }

        // 2. Bold + Italic: ***text***
        if (ch == '*' && i + 2 < n && text[i + 1] == '*' && text[i + 2] == '*') {
            val close = text.indexOf("***", i + 3)
            if (close > i + 3) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic))
                appendInlineMarkdown(text.substring(i + 3, close), codeBg, codeColor)
                pop()
                i = close + 3
                continue
            }
        }

        // 3. Bold: **text** or __text__
        if ((ch == '*' && i + 1 < n && text[i + 1] == '*') || (ch == '_' && i + 1 < n && text[i + 1] == '_')) {
            val delim = if (ch == '*') "**" else "__"
            val close = text.indexOf(delim, i + 2)
            if (close > i + 2) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                appendInlineMarkdown(text.substring(i + 2, close), codeBg, codeColor)
                pop()
                i = close + 2
                continue
            } else {
                // Gracefully swallow trailing unclosed `**` during live SSE streaming
                i += 2
                continue
            }
        }

        // 4. Italic: *text* (non-space right after opening `*`)
        if (ch == '*' && i + 1 < n && text[i + 1] != ' ' && text[i + 1] != '*') {
            val close = findClosingSingleStar(text, i + 1)
            if (close > i + 1) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineMarkdown(text.substring(i + 1, close), codeBg, codeColor)
                pop()
                i = close + 1
                continue
            }
        }

        // 5. Italic: _text_ (at start or preceded by whitespace/punctuation)
        if (ch == '_' && i + 1 < n && text[i + 1] != ' ' && text[i + 1] != '_' &&
            (i == 0 || !text[i - 1].isLetterOrDigit())
        ) {
            val close = text.indexOf('_', i + 1)
            if (close > i + 1 && (close + 1 == n || !text[close + 1].isLetterOrDigit())) {
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                appendInlineMarkdown(text.substring(i + 1, close), codeBg, codeColor)
                pop()
                i = close + 1
                continue
            }
        }

        append(ch)
        i++
    }
}

private fun findClosingSingleStar(text: String, start: Int): Int {
    var idx = start
    while (idx < text.length) {
        if (text[idx] == '*') {
            val prevIsStar = idx > 0 && text[idx - 1] == '*'
            val nextIsStar = idx + 1 < text.length && text[idx + 1] == '*'
            if (!prevIsStar && !nextIsStar && text[idx - 1] != ' ') {
                return idx
            }
        }
        idx++
    }
    return -1
}

private val HEADING_REGEX = Regex("""^(#{1,3})\s+(.+)$""")
private val BULLET_REGEX = Regex("""^(\s*)[-*•]\s+(.+)$""")
private val NUMBERED_REGEX = Regex("""^(\s*)(\d+[.)])\s+(.+)$""")

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = Glass.TextPrimary,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val isDark = Glass.IsDark
    val codeBg = if (isDark) Color.White.copy(alpha = 0.11f) else Color(0xFF0F172A).copy(alpha = 0.08f)
    val codeColor = Glass.Cyan
    val bulletColor = Glass.Cyan

    val annotated = remember(text, isDark, codeBg, codeColor, bulletColor) {
        parseMarkdownToAnnotatedString(
            markdown = text,
            codeBg = codeBg,
            codeColor = codeColor,
            bulletColor = bulletColor
        )
    }

    Text(
        text = annotated,
        modifier = modifier,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = overflow
    )
}
