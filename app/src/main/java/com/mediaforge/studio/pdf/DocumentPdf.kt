package com.mediaforge.studio.pdf

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BlockStyle { TITLE, H1, H2, H3, BODY, BULLET, QUOTE, CODE, RULE }

/** One paragraph-level unit of a document. `text` may carry bold / italic / mono spans. */
class DocBlock(val style: BlockStyle, val text: CharSequence)

// ------------------------------------------------------------------ Parsers

object DocxParser {

    /**
     * Reads `word/document.xml` out of a .docx (a zip) with [ZipInputStream] + [XmlPullParser].
     * Text, bold/italic runs, headings, bullets, tabs, breaks and simple tables are preserved.
     */
    fun parse(ctx: Context, uri: Uri): List<DocBlock> {
        val input = ctx.contentResolver.openInputStream(uri) ?: throw PdfException("Cannot open the .docx file")
        input.use { raw ->
            ZipInputStream(raw.buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == "word/document.xml") return parseXml(zip)
                    entry = zip.nextEntry
                }
            }
        }
        throw PdfException("This is not a valid .docx (word/document.xml not found)")
    }

    private fun parseXml(stream: java.io.InputStream): List<DocBlock> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(stream, "UTF-8")

        val blocks = ArrayList<DocBlock>()
        var para = SpannableStringBuilder()
        var style = BlockStyle.BODY
        var inRun = false
        var bold = false
        var italic = false
        var inText = false
        var inPPr = false
        var inRPr = false
        var tableDepth = 0
        val rowCells = ArrayList<String>()
        var cell = StringBuilder()

        fun isOn(p: XmlPullParser): Boolean {
            val v = p.getAttributeValue(null, "w:val") ?: return true
            return v != "0" && v != "false" && v != "off"
        }

        fun endParagraph() {
            val text = para
            if (tableDepth > 0) {
                if (cell.isNotEmpty() && text.isNotEmpty()) cell.append(' ')
                cell.append(text.toString().trim())
            } else if (text.isNotBlank() || style == BlockStyle.BODY) {
                blocks.add(DocBlock(style, text))
            }
            para = SpannableStringBuilder()
            style = BlockStyle.BODY
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "w:p" -> { para = SpannableStringBuilder(); style = BlockStyle.BODY }
                    "w:pPr" -> inPPr = true
                    "w:pStyle" -> if (inPPr) {
                        val v = (parser.getAttributeValue(null, "w:val") ?: "").lowercase()
                        style = when {
                            v == "title" -> BlockStyle.TITLE
                            v.startsWith("heading1") || v == "h1" -> BlockStyle.H1
                            v.startsWith("heading2") || v == "h2" -> BlockStyle.H2
                            v.startsWith("heading3") || v.startsWith("heading4") || v == "h3" -> BlockStyle.H3
                            v.contains("list") -> BlockStyle.BULLET
                            v.contains("quote") -> BlockStyle.QUOTE
                            else -> style
                        }
                    }
                    "w:numPr" -> if (inPPr && style == BlockStyle.BODY) style = BlockStyle.BULLET
                    "w:r" -> { inRun = true; bold = false; italic = false }
                    "w:rPr" -> if (inRun) inRPr = true
                    "w:b" -> if (inRPr) bold = isOn(parser)
                    "w:i" -> if (inRPr) italic = isOn(parser)
                    "w:t" -> inText = true
                    "w:tab" -> if (inRun && !inRPr && !inPPr) para.append("    ")
                    "w:br", "w:cr" -> if (inRun) para.append('\n')
                    "w:tbl" -> tableDepth++
                    "w:tr" -> rowCells.clear()
                    "w:tc" -> cell = StringBuilder()
                }
                XmlPullParser.TEXT -> if (inText && inRun) {
                    val start = para.length
                    para.append(parser.text)
                    val end = para.length
                    if (bold && italic) para.setSpan(StyleSpan(Typeface.BOLD_ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else if (bold) para.setSpan(StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    else if (italic) para.setSpan(StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "w:t" -> inText = false
                    "w:r" -> inRun = false
                    "w:rPr" -> inRPr = false
                    "w:pPr" -> inPPr = false
                    "w:p" -> endParagraph()
                    "w:tc" -> rowCells.add(cell.toString().trim())
                    "w:tr" -> if (tableDepth > 0 && rowCells.any { it.isNotEmpty() }) {
                        blocks.add(DocBlock(BlockStyle.BODY, rowCells.joinToString("   |   ")))
                    }
                    "w:tbl" -> tableDepth = (tableDepth - 1).coerceAtLeast(0)
                }
            }
            event = parser.next()
        }
        // Collapse runs of empty body paragraphs to a single blank line.
        val cleaned = ArrayList<DocBlock>()
        blocks.forEach { b ->
            val blank = b.style == BlockStyle.BODY && b.text.isBlank()
            if (!(blank && cleaned.lastOrNull()?.let { it.style == BlockStyle.BODY && it.text.isBlank() } == true)) cleaned.add(b)
        }
        if (cleaned.none { it.text.isNotBlank() }) throw PdfException("The document contains no readable text")
        return cleaned
    }
}

/** Minimal Markdown (headings, bullets, quotes, fenced code, **bold**, *italic*, `code`) → blocks. */
object MarkdownParser {

    fun parse(source: String): List<DocBlock> {
        val blocks = ArrayList<DocBlock>()
        val paragraph = StringBuilder()
        var inFence = false
        val fence = StringBuilder()

        fun flushParagraph() {
            if (paragraph.isNotBlank()) blocks.add(DocBlock(BlockStyle.BODY, inline(paragraph.toString().trim())))
            paragraph.clear()
        }

        for (rawLine in source.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            val line = rawLine.trimEnd()
            if (line.trimStart().startsWith("```")) {
                if (inFence) {
                    blocks.add(DocBlock(BlockStyle.CODE, fence.toString().trimEnd()))
                    fence.clear()
                } else flushParagraph()
                inFence = !inFence
                continue
            }
            if (inFence) { fence.appendLine(rawLine); continue }

            val t = line.trimStart()
            when {
                t.isEmpty() -> flushParagraph()
                t.startsWith("### ") -> { flushParagraph(); blocks.add(DocBlock(BlockStyle.H3, inline(t.drop(4)))) }
                t.startsWith("## ") -> { flushParagraph(); blocks.add(DocBlock(BlockStyle.H2, inline(t.drop(3)))) }
                t.startsWith("# ") -> { flushParagraph(); blocks.add(DocBlock(BlockStyle.H1, inline(t.drop(2)))) }
                t == "---" || t == "***" || t == "___" -> { flushParagraph(); blocks.add(DocBlock(BlockStyle.RULE, "")) }
                t.startsWith("- ") || t.startsWith("* ") || t.startsWith("+ ") -> {
                    flushParagraph(); blocks.add(DocBlock(BlockStyle.BULLET, inline(t.drop(2))))
                }
                Regex("^\\d+[.)]\\s+.*").matches(t) -> {
                    flushParagraph(); blocks.add(DocBlock(BlockStyle.BODY, inline(t)))
                }
                t.startsWith("> ") -> { flushParagraph(); blocks.add(DocBlock(BlockStyle.QUOTE, inline(t.drop(2)))) }
                else -> { if (paragraph.isNotEmpty()) paragraph.append(' '); paragraph.append(t) }
            }
        }
        if (inFence && fence.isNotBlank()) blocks.add(DocBlock(BlockStyle.CODE, fence.toString().trimEnd()))
        flushParagraph()
        if (blocks.none { it.text.isNotBlank() }) throw PdfException("There is no text to convert")
        return blocks
    }

    /** Plain text: keep every line break. */
    fun plain(source: String): List<DocBlock> {
        val blocks = source.replace("\r\n", "\n").replace('\r', '\n').split('\n').map { DocBlock(BlockStyle.BODY, it) }
        if (blocks.none { it.text.isNotBlank() }) throw PdfException("There is no text to convert")
        return blocks
    }

    private fun inline(text: String): CharSequence {
        val out = SpannableStringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("**", i) || text.startsWith("__", i) -> {
                    val marker = text.substring(i, i + 2)
                    val end = text.indexOf(marker, i + 2)
                    if (end > 0) {
                        val s = out.length
                        out.append(text.substring(i + 2, end))
                        out.setSpan(StyleSpan(Typeface.BOLD), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 2
                    } else { out.append(text[i]); i++ }
                }
                text[i] == '*' || text[i] == '_' -> {
                    val end = text.indexOf(text[i], i + 1)
                    if (end > i + 1 && (text[i] == '*' || (i == 0 || !text[i - 1].isLetterOrDigit()))) {
                        val s = out.length
                        out.append(text.substring(i + 1, end))
                        out.setSpan(StyleSpan(Typeface.ITALIC), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else { out.append(text[i]); i++ }
                }
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i) {
                        val s = out.length
                        out.append(text.substring(i + 1, end))
                        out.setSpan(TypefaceSpan("monospace"), s, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        i = end + 1
                    } else { out.append(text[i]); i++ }
                }
                else -> { out.append(text[i]); i++ }
            }
        }
        return out
    }
}

// ------------------------------------------------------------------ Renderer

/** Paginates blocks onto A4 pages using [PdfDocument] + [StaticLayout]. */
object DocumentPdfRenderer {
    private const val PAGE_W = 595
    private const val PAGE_H = 842
    private const val MARGIN = 54f
    private const val FOOTER = 34f

    private fun paintFor(style: BlockStyle): TextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(24, 28, 38)
        when (style) {
            BlockStyle.TITLE -> { textSize = 26f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            BlockStyle.H1 -> { textSize = 21f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            BlockStyle.H2 -> { textSize = 17f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            BlockStyle.H3 -> { textSize = 14.5f; typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) }
            BlockStyle.CODE -> { textSize = 9.5f; typeface = Typeface.MONOSPACE; color = Color.rgb(40, 50, 70) }
            BlockStyle.QUOTE -> { textSize = 11.5f; typeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC); color = Color.rgb(70, 78, 95) }
            else -> { textSize = 11.5f; typeface = Typeface.SANS_SERIF }
        }
    }

    private fun spaceBefore(style: BlockStyle) = when (style) {
        BlockStyle.TITLE -> 4f
        BlockStyle.H1 -> 12f
        BlockStyle.H2 -> 10f
        BlockStyle.H3 -> 8f
        BlockStyle.CODE, BlockStyle.QUOTE -> 4f
        else -> 0f
    }

    private fun spaceAfter(style: BlockStyle) = when (style) {
        BlockStyle.TITLE -> 12f
        BlockStyle.H1, BlockStyle.H2, BlockStyle.H3 -> 4f
        BlockStyle.BULLET -> 2f
        BlockStyle.CODE -> 8f
        BlockStyle.QUOTE -> 6f
        else -> 5f
    }

    fun render(blocks: List<DocBlock>, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        val doc = PdfDocument()
        try {
            var pageNo = 0
            var page: PdfDocument.Page? = null
            var canvas: Canvas? = null
            var y = MARGIN
            val bottom = PAGE_H - MARGIN - FOOTER / 2
            val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.rgb(140, 146, 160); textSize = 9f; textAlign = Paint.Align.CENTER
            }

            fun newPage() {
                page?.let {
                    canvas!!.drawText("${pageNo}", PAGE_W / 2f, PAGE_H - MARGIN / 2f, footerPaint)
                    doc.finishPage(it)
                }
                pageNo++
                page = doc.startPage(PdfDocument.PageInfo.Builder(PAGE_W, PAGE_H, pageNo).create())
                canvas = page!!.canvas
                y = MARGIN
            }
            newPage()

            blocks.forEachIndexed { index, block ->
                onProgress(index.toFloat() / blocks.size)
                if (block.style == BlockStyle.RULE) {
                    if (y + 14f > bottom) newPage()
                    val p = Paint().apply { color = Color.rgb(200, 205, 215); strokeWidth = 0.8f }
                    canvas!!.drawLine(MARGIN, y + 6f, PAGE_W - MARGIN, y + 6f, p)
                    y += 14f
                    return@forEachIndexed
                }
                val indent = when (block.style) {
                    BlockStyle.BULLET -> 16f
                    BlockStyle.QUOTE -> 14f
                    BlockStyle.CODE -> 8f
                    else -> 0f
                }
                val width = (PAGE_W - MARGIN * 2 - indent).toInt()
                val paint = paintFor(block.style)
                val text: CharSequence = if (block.text.isEmpty()) " " else block.text
                val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
                    .setLineSpacing(0f, if (block.style == BlockStyle.CODE) 1.1f else 1.28f)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setIncludePad(false)
                    .build()

                y += spaceBefore(block.style)
                var line = 0
                var first = true
                while (line < layout.lineCount) {
                    if (y + (layout.getLineBottom(line) - layout.getLineTop(line)) > bottom && y > MARGIN) {
                        newPage()
                    }
                    val startLine = line
                    val top = layout.getLineTop(startLine)
                    var endLine = startLine
                    while (endLine < layout.lineCount &&
                        y + (layout.getLineBottom(endLine) - top) <= bottom
                    ) endLine++
                    if (endLine == startLine) endLine = startLine + 1 // always progress
                    val blockBottom = layout.getLineBottom(endLine - 1)

                    val c = canvas!!
                    c.save()
                    c.translate(MARGIN + indent, y - top)
                    c.clipRect(-8f, top.toFloat(), width + 8f, blockBottom.toFloat())
                    if (block.style == BlockStyle.CODE) {
                        val bg = Paint().apply { color = Color.rgb(238, 241, 247) }
                        c.drawRect(-8f, top.toFloat(), width + 8f, blockBottom.toFloat(), bg)
                    }
                    layout.draw(c)
                    c.restore()

                    if (first && block.style == BlockStyle.BULLET) {
                        c.drawText("•", MARGIN + 3f, y + paint.textSize, paint)
                    }
                    if (block.style == BlockStyle.QUOTE) {
                        val bar = Paint().apply { color = Color.rgb(34, 211, 238); strokeWidth = 2.5f }
                        c.drawLine(MARGIN + 3f, y, MARGIN + 3f, y + (blockBottom - top), bar)
                    }
                    y += (blockBottom - top)
                    line = endLine
                    first = false
                }
                y += spaceAfter(block.style)
            }
            page?.let {
                canvas!!.drawText("$pageNo", PAGE_W / 2f, PAGE_H - MARGIN / 2f, footerPaint)
                doc.finishPage(it)
            }
            onProgress(1f)
            doc.writeTo(out)
        } finally {
            doc.close()
        }
    }
}

// ------------------------------------------------------------------ DOCX Builder

/**
 * Generates a standards-compliant OOXML `.docx` archive from extracted page strings
 * using only `java.util.zip.ZipOutputStream` (no heavyweight POI dependencies).
 */
object DocxBuilder {

    fun build(pagesText: List<String>, out: OutputStream, pageBreaks: Boolean = true) {
        if (pagesText.all { it.isBlank() }) {
            throw PdfException("No selectable text found to export to Word (.docx).")
        }
        ZipOutputStream(out.buffered()).use { zip ->
            writeEntry(zip, "[Content_Types].xml", CONTENT_TYPES_XML)
            writeEntry(zip, "_rels/.rels", RELS_XML)
            writeEntry(zip, "word/_rels/document.xml.rels", DOC_RELS_XML)
            writeEntry(zip, "word/document.xml", buildDocumentXml(pagesText, pageBreaks))
        }
    }

    internal fun buildDocumentXml(pagesText: List<String>, pageBreaks: Boolean): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">""")
        append("<w:body>")
        pagesText.forEachIndexed { pageIndex, raw ->
            val normalized = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
            if (normalized.isNotEmpty()) {
                val paragraphs = normalized.split(Regex("\n{2,}"))
                paragraphs.forEach { para ->
                    val lines = para.split('\n').map { sanitizeXml(it.trimEnd()) }
                    if (lines.any { it.isNotEmpty() }) {
                        append("<w:p><w:r>")
                        lines.forEachIndexed { lineIdx, line ->
                            if (lineIdx > 0) append("<w:br/>")
                            append("""<w:t xml:space="preserve">""")
                            append(escapeXml(line))
                            append("</w:t>")
                        }
                        append("</w:r></w:p>")
                    }
                }
            }
            if (pageBreaks && pageIndex < pagesText.lastIndex) {
                append("""<w:p><w:r><w:br w:type="page"/></w:r></w:p>""")
            }
        }
        append("""<w:sectPr><w:pgSz w:w="11906" w:h="16838"/><w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/></w:sectPr>""")
        append("</w:body></w:document>")
    }

    private fun writeEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    /** Strips XML 1.0 illegal control characters while keeping tab/newline/CR. */
    private fun sanitizeXml(s: String): String = buildString(s.length) {
        for (ch in s) {
            val code = ch.code
            if (code == 0x9 || code == 0xA || code == 0xD || (code >= 0x20 && code <= 0xD7FF) || (code in 0xE000..0xFFFD)) {
                append(ch)
            }
        }
    }

    private fun escapeXml(s: String): String = buildString(s.length) {
        for (ch in s) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&apos;")
                else -> append(ch)
            }
        }
    }

    private const val CONTENT_TYPES_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>""" +
            """</Types>"""

    private const val RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>""" +
            """</Relationships>"""

    private const val DOC_RELS_XML =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"/>"""
}
