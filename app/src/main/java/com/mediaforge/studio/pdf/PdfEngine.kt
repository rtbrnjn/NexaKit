package com.mediaforge.studio.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.mediaforge.studio.core.BitmapUtils
import com.tom_roush.pdfbox.multipdf.PDFMergerUtility
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.state.PDExtendedGraphicsState
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.util.Matrix
import java.io.File
import java.io.OutputStream
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class PdfPageMode(val label: String) { A4("A4"), FIT("Fit to image") }

enum class PdfCompressPreset(val label: String, val detail: String, val dpi: Int, val jpegQuality: Float) {
    LOW("Low", "Smallest size • 85 DPI", 85, 0.45f),
    BALANCED("Balanced", "Recommended • 120 DPI", 120, 0.65f),
    HIGH("High", "Sharp text • 175 DPI", 175, 0.82f)
}

enum class PageNumberFormat(val label: String) {
    PAGE_X_OF_Y("Page 1 of N"),
    X_SLASH_Y("1 / N"),
    NUMBER_ONLY("1")
}

enum class PageNumberPosition(val label: String) {
    BOTTOM_CENTER("Bottom Center"),
    BOTTOM_RIGHT("Bottom Right")
}

data class PageNumberOptions(
    val format: PageNumberFormat = PageNumberFormat.PAGE_X_OF_Y,
    val position: PageNumberPosition = PageNumberPosition.BOTTOM_CENTER,
    val fontSize: Float = 10f,
    val startNumber: Int = 1,
    val marginPt: Float = 26f
)

data class WatermarkOptions(
    val text: String,
    /** 0.05 .. 0.8 */
    val opacity: Float = 0.25f,
    val color: Int = 0xFF6B7280.toInt()
)

class PdfException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** All offline PDF operations. Everything is blocking – call from Dispatchers.IO / Default. */
object PdfEngine {

    // ---------------------------------------------------------------- Images → PDF

    fun imagesToPdf(
        ctx: Context,
        images: List<Uri>,
        mode: PdfPageMode,
        jpegQuality: Float,
        out: OutputStream,
        onProgress: (Float) -> Unit = {}
    ) {
        if (images.isEmpty()) throw PdfException("Select at least one image")
        PDDocument().use { doc ->
            images.forEachIndexed { i, uri ->
                val decoded = BitmapUtils.decode(ctx, uri, 2600)
                    ?: throw PdfException("Image ${i + 1} could not be decoded")
                val bmp = BitmapUtils.flatten(decoded)
                val xobj = JPEGFactory.createFromImage(doc, bmp, jpegQuality)

                val margin = 18f
                val rect: PDRectangle
                val x: Float
                val y: Float
                val dw: Float
                val dh: Float
                if (mode == PdfPageMode.A4) {
                    val landscape = bmp.width > bmp.height
                    rect = if (landscape) PDRectangle(PDRectangle.A4.height, PDRectangle.A4.width) else PDRectangle.A4
                    val availW = rect.width - margin * 2
                    val availH = rect.height - margin * 2
                    val s = minOf(availW / bmp.width, availH / bmp.height)
                    dw = bmp.width * s
                    dh = bmp.height * s
                    x = (rect.width - dw) / 2
                    y = (rect.height - dh) / 2
                } else {
                    // 150 dpi equivalent keeps pages a sensible physical size
                    val pw = bmp.width * 72f / 150f
                    val ph = bmp.height * 72f / 150f
                    rect = PDRectangle(pw, ph)
                    dw = pw; dh = ph; x = 0f; y = 0f
                }
                val page = PDPage(rect)
                doc.addPage(page)
                PDPageContentStream(doc, page).use { cs -> cs.drawImage(xobj, x, y, dw, dh) }
                bmp.recycle()
                if (decoded !== bmp) decoded.recycle()
                onProgress((i + 1f) / images.size)
            }
            doc.save(out)
        }
    }

    // ---------------------------------------------------------------- Merge

    fun merge(files: List<File>, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        if (files.size < 2) throw PdfException("Select at least two PDFs to merge")
        val merger = PDFMergerUtility()
        files.forEach { merger.addSource(it) }
        merger.destinationStream = out
        onProgress(0.1f)
        try {
            merger.mergeDocuments(MemoryUsageSetting.setupTempFileOnly())
        } catch (e: Exception) {
            throw PdfException("Merge failed – one of the files may be damaged or password protected", e)
        }
        onProgress(1f)
    }

    // ---------------------------------------------------------------- Split / Extract

    /**
     * Parses `1-3, 5, 8-` (1-based) into a sorted, distinct list of 0-based page indexes.
     */
    fun parseRanges(spec: String, pageCount: Int): List<Int> {
        val cleaned = spec.trim().lowercase().replace(Regex("\\s*-\\s*"), "-")
        if (cleaned.isEmpty()) throw PdfException("Enter pages to extract, e.g. 1-3, 5")
        if (cleaned == "all") return (0 until pageCount).toList()
        val pages = sortedSetOf<Int>()
        cleaned.split(',', ';', ' ').filter { it.isNotBlank() }.forEach { token ->
            val t = token.trim()
            try {
                if ('-' in t) {
                    val (a, b) = t.split('-', limit = 2)
                    val start = if (a.isBlank()) 1 else a.trim().toInt()
                    val end = if (b.isBlank()) pageCount else b.trim().toInt()
                    if (start < 1 || end < start) throw PdfException("Invalid range \"$t\"")
                    if (end > pageCount) throw PdfException("Page $end is out of range (document has $pageCount)")
                    for (p in start..end) pages.add(p - 1)
                } else {
                    val p = t.toInt()
                    if (p < 1 || p > pageCount) throw PdfException("Page $p is out of range (document has $pageCount)")
                    pages.add(p - 1)
                }
            } catch (_: NumberFormatException) {
                throw PdfException("Cannot read \"$t\" – use numbers like 1-3, 5")
            }
        }
        return pages.toList()
    }

    /** Keeps only `keep` (0-based, document order) and saves. */
    fun extractPages(source: File, keep: Collection<Int>, out: OutputStream) {
        if (keep.isEmpty()) throw PdfException("No pages selected")
        open(source).use { doc ->
            val keepSet = keep.toSet()
            for (i in doc.numberOfPages - 1 downTo 0) if (i !in keepSet) doc.removePage(i)
            doc.save(out)
        }
    }

    // ---------------------------------------------------------------- Compress (re-render)

    /**
     * Rasterises every page at `dpi` and re-embeds it as JPEG at `quality`.
     * Huge savings for scans / photo PDFs; vector text becomes image (not selectable).
     */
    fun compress(source: File, dpi: Int, quality: Float, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        val renderer = try { PdfPageRenderer(source) } catch (e: Exception) {
            throw PdfException("Cannot open PDF (password protected or damaged)", e)
        }
        renderer.use { r ->
            PDDocument().use { doc ->
                for (i in 0 until r.pageCount) {
                    val (wPt, hPt) = r.pageSize(i)
                    val bmp = r.renderAtDpi(i, dpi, maxSide = 3600)
                    val xobj = JPEGFactory.createFromImage(doc, bmp, quality)
                    bmp.recycle()
                    val page = PDPage(PDRectangle(wPt.toFloat(), hPt.toFloat()))
                    doc.addPage(page)
                    PDPageContentStream(doc, page).use { cs -> cs.drawImage(xobj, 0f, 0f, wPt.toFloat(), hPt.toFloat()) }
                    onProgress((i + 1f) / r.pageCount)
                }
                doc.save(out)
            }
        }
    }

    // ---------------------------------------------------------------- Rotate / Delete / Watermark

    /**
     * @param rotations additional clockwise degrees (90/180/270) per 0-based page index
     * @param deleted   0-based page indexes to drop
     */
    fun edit(
        source: File,
        rotations: Map<Int, Int>,
        deleted: Set<Int>,
        watermark: WatermarkOptions?,
        out: OutputStream
    ) {
        open(source).use { doc ->
            if (deleted.size >= doc.numberOfPages) throw PdfException("You cannot delete every page")
            for (i in 0 until doc.numberOfPages) {
                val extra = rotations[i] ?: 0
                if (extra != 0 && i !in deleted) {
                    val page = doc.getPage(i)
                    page.rotation = ((page.rotation + extra) % 360 + 360) % 360
                }
            }
            for (i in doc.numberOfPages - 1 downTo 0) if (i in deleted) doc.removePage(i)
            if (watermark != null && watermark.text.isNotBlank()) {
                for (i in 0 until doc.numberOfPages) stamp(doc, doc.getPage(i), watermark)
            }
            doc.save(out)
        }
    }

    private fun stamp(doc: PDDocument, page: PDPage, wm: WatermarkOptions) {
        val font = PDType1Font.HELVETICA_BOLD
        val text = wm.text.trim().map { ch ->
            if (ch.code in 32..126 || ch.code in 160..255) ch else '?'
        }.joinToString("")
        val box = page.cropBox
        val pw = box.width
        val ph = box.height
        val cx = box.lowerLeftX + pw / 2
        val cy = box.lowerLeftY + ph / 2

        // Content is drawn in un-rotated page space; add the page rotation so the stamp
        // always reads bottom-left → top-right on screen.
        val angle = Math.toRadians((45 + page.rotation).toDouble())
        val unit = font.getStringWidth(text) / 1000f
        val diagonal = hypot(pw.toDouble(), ph.toDouble()).toFloat()
        val fontSize = (diagonal * 0.72f / unit).coerceIn(18f, 150f)
        val textWidth = unit * fontSize
        val textHeight = fontSize * 0.7f
        val cos = cos(angle).toFloat()
        val sin = sin(angle).toFloat()
        val startX = cx - (textWidth / 2) * cos + (textHeight / 2) * sin
        val startY = cy - (textWidth / 2) * sin - (textHeight / 2) * cos

        val gs = PDExtendedGraphicsState()
        gs.setNonStrokingAlphaConstant(wm.opacity.coerceIn(0.03f, 1f))
        gs.setStrokingAlphaConstant(wm.opacity.coerceIn(0.03f, 1f))

        PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.setGraphicsStateParameters(gs)
            cs.setNonStrokingColor(
                android.graphics.Color.red(wm.color) / 255f,
                android.graphics.Color.green(wm.color) / 255f,
                android.graphics.Color.blue(wm.color) / 255f
            )
            cs.beginText()
            cs.setFont(font, fontSize)
            cs.setTextMatrix(Matrix.getRotateInstance(angle, startX, startY))
            cs.showText(text)
            cs.endText()
        }
    }

    // ---------------------------------------------------------------- PDF → Word (.docx)

    fun pdfToDocx(
        source: File,
        pageBreaks: Boolean,
        out: OutputStream,
        onProgress: (Float) -> Unit = {}
    ) {
        open(source, forEdit = false).use { doc ->
            val count = doc.numberOfPages.coerceAtLeast(1)
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            val pages = ArrayList<String>(count)
            for (p in 1..count) {
                stripper.startPage = p
                stripper.endPage = p
                pages += stripper.getText(doc)
                onProgress(p.toFloat() / count * 0.85f)
            }
            if (pages.all { it.isBlank() }) {
                throw PdfException("No selectable text found. Scanned image PDFs cannot be converted to editable Word text offline.")
            }
            DocxBuilder.build(pages, out, pageBreaks)
            onProgress(1f)
        }
    }

    // ---------------------------------------------------------------- Page Numbers & Watermark

    fun addPageNumbers(source: File, opts: PageNumberOptions, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        open(source).use { doc ->
            val total = doc.numberOfPages
            val font = PDType1Font.HELVETICA
            for (i in 0 until total) {
                val page = doc.getPage(i)
                val num = opts.startNumber + i
                val totalDisplay = opts.startNumber + total - 1
                val label = when (opts.format) {
                    PageNumberFormat.PAGE_X_OF_Y -> "Page $num of $totalDisplay"
                    PageNumberFormat.X_SLASH_Y -> "$num / $totalDisplay"
                    PageNumberFormat.NUMBER_ONLY -> "$num"
                }
                val box = page.cropBox
                val x0 = box.lowerLeftX
                val y0 = box.lowerLeftY
                val pw = box.width
                val ph = box.height
                val rot = ((page.rotation % 360) + 360) % 360
                val vw = if (rot == 90 || rot == 270) ph else pw

                val textWidth = font.getStringWidth(label) / 1000f * opts.fontSize
                val vx = when (opts.position) {
                    PageNumberPosition.BOTTOM_CENTER -> ((vw - textWidth) / 2f).coerceAtLeast(12f)
                    PageNumberPosition.BOTTOM_RIGHT -> (vw - opts.marginPt - textWidth).coerceAtLeast(12f)
                }
                val vy = opts.marginPt

                val (pdfX, pdfY, angleDeg) = when (rot) {
                    90 -> Triple(x0 + pw - vy, y0 + vx, 90.0)
                    180 -> Triple(x0 + pw - vx, y0 + ph - vy, 180.0)
                    270 -> Triple(x0 + vy, y0 + ph - vx, 270.0)
                    else -> Triple(x0 + vx, y0 + vy, 0.0)
                }

                PDPageContentStream(doc, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
                    cs.setNonStrokingColor(0.28f, 0.33f, 0.41f)
                    cs.beginText()
                    cs.setFont(font, opts.fontSize)
                    cs.setTextMatrix(Matrix.getRotateInstance(Math.toRadians(angleDeg), pdfX, pdfY))
                    cs.showText(label)
                    cs.endText()
                }
                onProgress((i + 1f) / total)
            }
            doc.save(out)
        }
    }

    fun watermarkPdf(source: File, watermark: WatermarkOptions, out: OutputStream, onProgress: (Float) -> Unit = {}) {
        if (watermark.text.isBlank()) throw PdfException("Enter watermark text first")
        open(source).use { doc ->
            val total = doc.numberOfPages
            for (i in 0 until total) {
                stamp(doc, doc.getPage(i), watermark)
                onProgress((i + 1f) / total)
            }
            doc.save(out)
        }
    }

    // ---------------------------------------------------------------- Protect / Unlock

    fun protect(
        source: File,
        userPassword: String,
        ownerPassword: String,
        allowPrint: Boolean,
        allowCopy: Boolean,
        out: OutputStream
    ) {
        if (userPassword.isEmpty()) throw PdfException("Enter a password to protect this PDF")
        open(source, forEdit = false).use { doc ->
            if (doc.isEncrypted) throw PdfException("This PDF is already password protected. Unlock it first.")
            val perms = AccessPermission().apply {
                setCanPrint(allowPrint)
                setCanPrintFaithful(allowPrint)
                setCanExtractContent(allowCopy)
                setCanModify(false)
                setCanModifyAnnotations(false)
                setCanFillInForm(true)
                setCanAssembleDocument(false)
            }
            val owner = ownerPassword.ifEmpty { userPassword }
            val policy = StandardProtectionPolicy(owner, userPassword, perms).apply {
                encryptionKeyLength = 128
            }
            doc.protect(policy)
            doc.save(out)
        }
    }

    fun isEncrypted(file: File): Boolean = try {
        PDDocument.load(file).use { it.isEncrypted }
    } catch (_: InvalidPasswordException) {
        true
    } catch (e: Exception) {
        throw PdfException("Cannot open ${file.name.substringAfter('_')} – it may be damaged", e)
    }

    fun unlock(source: File, password: String, out: OutputStream) {
        val doc = try {
            PDDocument.load(source, password)
        } catch (e: InvalidPasswordException) {
            throw PdfException("Incorrect password for this PDF", e)
        } catch (e: Exception) {
            throw PdfException("Cannot open PDF – it may be damaged", e)
        }
        doc.use {
            it.isAllSecurityToBeRemoved = true
            it.save(out)
        }
    }

    // ---------------------------------------------------------------- Text extraction

    data class ExtractedPdfText(
        val text: String,
        val extractedPages: Int,
        val totalPages: Int,
        val wasTruncated: Boolean
    )

    /** Extracts selectable text from selected pages (`"all"` or `"1-5, 8"`), capped at `maxChars`. */
    fun extractText(
        source: File,
        pageRange: String = "all",
        maxChars: Int = 30_000
    ): ExtractedPdfText = open(source, forEdit = false).use { doc ->
        val total = doc.numberOfPages.coerceAtLeast(1)
        val pages = parseRanges(pageRange.ifBlank { "all" }, total)
        val stripper = PDFTextStripper().apply { sortByPosition = true }
        val sb = StringBuilder()
        var count = 0
        var truncated = false
        for (zeroIdx in pages) {
            stripper.startPage = zeroIdx + 1
            stripper.endPage = zeroIdx + 1
            val chunk = stripper.getText(doc).trim()
            count++
            if (chunk.isNotEmpty()) {
                if (pages.size > 1) sb.append("--- Page ").append(zeroIdx + 1).append(" ---\n")
                sb.append(chunk).append("\n\n")
            }
            if (sb.length >= maxChars) {
                truncated = true
                break
            }
        }
        val raw = sb.toString().trim()
        val finalText = if (raw.length > maxChars) {
            truncated = true
            raw.take(maxChars) + "\n\n[…truncated at $maxChars chars]"
        } else raw
        ExtractedPdfText(finalText, count, total, truncated)
    }

    // ---------------------------------------------------------------- helpers

    private fun open(file: File, forEdit: Boolean = true): PDDocument {
        val doc = try {
            PDDocument.load(file)
        } catch (e: Exception) {
            throw PdfException("Cannot open ${file.name.substringAfter('_')} – it may be password protected or damaged", e)
        }
        if (doc.isEncrypted) {
            if (forEdit) doc.isAllSecurityToBeRemoved = true
        }
        return doc
    }

    fun pageCount(file: File): Int = PdfPageRenderer(file).use { it.pageCount }

    fun bitmapToJpeg(bmp: Bitmap, quality: Int): ByteArray = BitmapUtils.jpegBytes(bmp, quality)
}
