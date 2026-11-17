package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.pdf.DocBlock
import com.mediaforge.studio.pdf.DocumentPdfRenderer
import com.mediaforge.studio.pdf.DocxParser
import com.mediaforge.studio.pdf.MarkdownParser
import com.mediaforge.studio.pdf.PageNumberFormat
import com.mediaforge.studio.pdf.PageNumberOptions
import com.mediaforge.studio.pdf.PageNumberPosition
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.pdf.PdfPageRenderer
import com.mediaforge.studio.pdf.WatermarkOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.Closeable
import java.io.File
import java.io.IOException

/** A PDF copied into app cache, validated and ready to process. */
data class PdfSource(val file: File, val name: String, val sizeBytes: Long, val pageCount: Int)

data class LockedPdfSource(val file: File, val name: String, val sizeBytes: Long, val isEncrypted: Boolean)

object PdfInputs {
    /** Blocking; run on IO. Copies the Uri to cache and checks that PdfRenderer can open it. */
    fun load(app: Application, uri: Uri): PdfSource {
        val name = FileUtils.queryName(app, uri)
        val file = FileUtils.copyToCache(app, uri, "pdf_in", name)
        val pages = try {
            PdfPageRenderer(file).use { it.pageCount }
        } catch (e: SecurityException) {
            file.delete()
            throw PdfException("\"$name\" is password protected. Use Unlock PDF first.", e)
        } catch (e: IOException) {
            file.delete()
            throw PdfException("\"$name\" is not a valid or readable PDF.", e)
        }
        if (pages <= 0) throw PdfException("\"$name\" has no pages.")
        return PdfSource(file, name, file.length(), pages)
    }

    /** Blocking; copies Uri to cache and inspects encryption status without failing on locked PDFs. */
    fun loadForUnlock(app: Application, uri: Uri): LockedPdfSource {
        val name = FileUtils.queryName(app, uri)
        val file = FileUtils.copyToCache(app, uri, "pdf_unlock", name)
        val encrypted = try {
            PdfEngine.isEncrypted(file)
        } catch (e: PdfException) {
            file.delete()
            throw e
        }
        return LockedPdfSource(file, name, file.length(), encrypted)
    }
}

/** Lazy, cached page thumbnails for the page pickers (Split / Rotate & Delete). */
class PdfThumbs(file: File) : Closeable {
    private val renderer = PdfPageRenderer(file)
    private val cache = object : LruCache<Int, Bitmap>(64) {}

    suspend fun get(index: Int, widthPx: Int = 320): Bitmap? {
        cache.get(index)?.let { return it }
        val bmp = runCatching { renderer.thumbnail(index, widthPx) }.getOrNull()
        if (bmp != null) cache.put(index, bmp)
        return bmp
    }

    override fun close() { renderer.close() }
}

// ============================================================ 1. PDF → Word (.docx)

class PdfToDocxViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _pageBreaks = MutableStateFlow(true)
    val pageBreaks: StateFlow<Boolean> = _pageBreaks

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setPageBreaks(v: Boolean) { _pageBreaks.value = v }

    fun export() {
        val src = _source.value ?: return
        val breaks = _pageBreaks.value
        runTask("Converting PDF to Word…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_${FileUtils.timestamp()}.docx"
            val mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, mime, "PDF to Word") { out ->
                PdfEngine.pdfToDocx(src.file, breaks, out) { setProgress("Extracting text…", it) }
            }
            publish(listOf(saved))
            showInfo("Word document saved to Documents/NexaKit (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }
}

// ============================================================ 2. Word (.docx) & Text → PDF

class TextToPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _text = MutableStateFlow("")
    val text: StateFlow<String> = _text
    private val _markdown = MutableStateFlow(true)
    val markdown: StateFlow<Boolean> = _markdown
    private val _docx = MutableStateFlow<List<DocBlock>?>(null)
    val docx: StateFlow<List<DocBlock>?> = _docx
    private val _fileName = MutableStateFlow<String?>(null)
    val fileName: StateFlow<String?> = _fileName

    fun setText(t: String) { _text.value = t }
    fun setMarkdown(v: Boolean) { _markdown.value = v }
    fun clearFile() { _docx.value = null; _fileName.value = null }

    fun loadFile(uri: Uri) {
        runTask("Reading document…") {
            val name = FileUtils.queryName(ctx, uri)
            val lower = name.lowercase()
            if (lower.endsWith(".docx")) {
                val blocks = DocxParser.parse(ctx, uri)
                _docx.value = blocks
                _text.value = ""
            } else {
                val body = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?: throw PdfException("Cannot open $name")
                _docx.value = null
                _text.value = body
                _markdown.value = lower.endsWith(".md") || lower.endsWith(".markdown")
            }
            _fileName.value = name
        }
    }

    fun export() {
        val docxBlocks = _docx.value
        val raw = _text.value
        val md = _markdown.value
        val title = _fileName.value?.let { FileUtils.baseName(it) } ?: "Document"
        if (docxBlocks == null && raw.isBlank()) { showError("Import a .docx file or enter text first."); return }
        runTask("Typesetting A4 PDF…", write = true) {
            val blocks = docxBlocks ?: if (md) MarkdownParser.parse(raw) else MarkdownParser.plain(raw)
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, "${title}_${FileUtils.timestamp()}.pdf", "application/pdf", "Word to PDF") { out ->
                DocumentPdfRenderer.render(blocks, out) { setProgress("Paginating…", it) }
            }
            publish(listOf(saved))
            showInfo("PDF created (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }
}

// ============================================================ 3. Images → PDF

data class ImageEntry(val uri: Uri, val name: String)

class ImagesToPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _images = MutableStateFlow<List<ImageEntry>>(emptyList())
    val images: StateFlow<List<ImageEntry>> = _images
    private val _mode = MutableStateFlow(PdfPageMode.A4)
    val mode: StateFlow<PdfPageMode> = _mode
    private val _quality = MutableStateFlow(85)
    val quality: StateFlow<Int> = _quality

    init {
        viewModelScope.launch {
            _mode.value = container.settings.defaultPdfPageMode.first()
            _quality.value = container.settings.defaultQuality.first().coerceIn(50, 100)
        }
    }

    fun add(uris: List<Uri>) {
        viewModelScope.launch(Dispatchers.IO) {
            val entries = uris.map { ImageEntry(it, FileUtils.queryName(ctx, it)) }
            _images.update { it + entries }
        }
    }
    fun remove(i: Int) = _images.update { l -> l.filterIndexed { idx, _ -> idx != i } }
    fun move(i: Int, delta: Int) = _images.update { l ->
        val j = i + delta
        if (j !in l.indices) l else l.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
    fun clear() { _images.value = emptyList() }
    fun setMode(m: PdfPageMode) { _mode.value = m }
    fun setQuality(q: Int) { _quality.value = q }

    fun export() {
        val uris = _images.value.map { it.uri }
        if (uris.isEmpty()) { showError("Pick some images first."); return }
        val mode = _mode.value
        val q = _quality.value / 100f
        runTask("Building PDF…", write = true) {
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, "Images_${FileUtils.timestamp()}.pdf", "application/pdf", "Images to PDF") { out ->
                PdfEngine.imagesToPdf(ctx, uris, mode, q, out) { setProgress("Adding pages…", it) }
            }
            publish(listOf(saved))
            showInfo("PDF with ${uris.size} page(s) created (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }
}

// ============================================================ 4. PDF → Images

enum class RasterFormat(val label: String, val ext: String, val mime: String, val format: Bitmap.CompressFormat) {
    PNG("PNG", "png", "image/png", Bitmap.CompressFormat.PNG),
    JPG("JPG", "jpg", "image/jpeg", Bitmap.CompressFormat.JPEG)
}

class PdfToImagesViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _format = MutableStateFlow(RasterFormat.PNG)
    val format: StateFlow<RasterFormat> = _format
    private val _dpi = MutableStateFlow(200)
    val dpi: StateFlow<Int> = _dpi
    private val _pageRange = MutableStateFlow("all")
    val pageRange: StateFlow<String> = _pageRange

    fun pick(uri: Uri) {
        runTask("Reading PDF…") {
            _source.value = PdfInputs.load(getApplication(), uri)
            _pageRange.value = "all"
        }
    }
    fun setFormat(f: RasterFormat) { _format.value = f }
    fun setDpi(d: Int) { _dpi.value = d }
    fun setPageRange(r: String) { _pageRange.value = r }

    fun export() {
        val src = _source.value ?: return
        val fmt = _format.value
        val dpi = _dpi.value
        val spec = _pageRange.value.ifBlank { "all" }
        val pages = try {
            PdfEngine.parseRanges(spec, src.pageCount)
        } catch (e: PdfException) {
            showError(e.message ?: "Invalid page selection")
            return
        }
        runTask("Rendering pages…", write = true) {
            val base = FileUtils.baseName(src.name)
            val saved = ArrayList<SavedFile>()
            PdfPageRenderer(src.file).use { r ->
                pages.forEachIndexed { idx, pageIdx ->
                    setProgress("Page ${pageIdx + 1} (${idx + 1}/${pages.size})", idx.toFloat() / pages.size)
                    val bmp = r.renderAtDpi(pageIdx, dpi)
                    val name = "${base}_p${(pageIdx + 1).toString().padStart(3, '0')}.${fmt.ext}"
                    saved += Storage.save(ctx, OutputKind.IMAGE, "PDF_Export", name, fmt.mime, "PDF to Images") {
                        bmp.compress(fmt.format, 95, it)
                    }
                    bmp.recycle()
                }
            }
            publish(saved)
            showInfo("${saved.size} image(s) saved to Pictures/NexaKit/PDF_Export.")
        }
    }
}

// ============================================================ 5. Merge PDFs

class MergePdfViewModel(app: Application) : ToolViewModel(app) {
    private val _sources = MutableStateFlow<List<PdfSource>>(emptyList())
    val sources: StateFlow<List<PdfSource>> = _sources

    fun add(uris: List<Uri>) {
        runTask("Reading PDFs…") {
            val loaded = uris.mapIndexed { i, u ->
                setProgress("Reading ${i + 1} / ${uris.size}", i.toFloat() / uris.size)
                PdfInputs.load(getApplication(), u)
            }
            _sources.update { it + loaded }
        }
    }
    fun remove(i: Int) = _sources.update { l -> l.filterIndexed { idx, _ -> idx != i } }
    fun move(i: Int, delta: Int) = _sources.update { l ->
        val j = i + delta
        if (j !in l.indices) l else l.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
    }
    fun clear() { _sources.value = emptyList() }

    fun export() {
        val list = _sources.value
        if (list.size < 2) { showError("Add at least two PDFs."); return }
        runTask("Merging PDFs…", write = true) {
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, "Merged_${FileUtils.timestamp()}.pdf", "application/pdf", "Merge PDFs") { out ->
                PdfEngine.merge(list.map { it.file }, out) { setProgress("Merging…", it) }
            }
            publish(listOf(saved))
            showInfo("Merged ${list.sumOf { it.pageCount }} pages (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }
}

// ============================================================ 6. Split / Extract

class SplitPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _selected = MutableStateFlow<Set<Int>>(emptySet())
    val selected: StateFlow<Set<Int>> = _selected
    private val _rangeText = MutableStateFlow("")
    val rangeText: StateFlow<String> = _rangeText

    var thumbs: PdfThumbs? = null
        private set

    fun pick(uri: Uri) {
        runTask("Reading PDF…") {
            val src = PdfInputs.load(getApplication(), uri)
            thumbs?.close()
            thumbs = PdfThumbs(src.file)
            _selected.value = emptySet()
            _rangeText.value = ""
            _source.value = src
        }
    }

    fun toggle(index: Int) {
        _selected.update { if (index in it) it - index else it + index }
        _rangeText.value = formatRanges(_selected.value)
    }

    fun setRangeText(text: String) {
        _rangeText.value = text
        val src = _source.value ?: return
        runCatching { PdfEngine.parseRanges(text, src.pageCount) }.onSuccess { _selected.value = it.toSet() }
    }

    fun selectAll() {
        val src = _source.value ?: return
        _selected.value = (0 until src.pageCount).toSet()
        _rangeText.value = formatRanges(_selected.value)
    }
    fun clearSelection() { _selected.value = emptySet(); _rangeText.value = "" }

    fun export() {
        val src = _source.value ?: return
        val pages = try { PdfEngine.parseRanges(_rangeText.value, src.pageCount) } catch (e: PdfException) {
            showError(e.message ?: "Invalid range"); return
        }
        runTask("Extracting pages…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_pages_${FileUtils.timestamp()}.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Split / Extract") { out ->
                PdfEngine.extractPages(src.file, pages, out)
            }
            publish(listOf(saved))
            showInfo("Extracted ${pages.size} page(s) (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }

    override fun onCleared() { thumbs?.close(); super.onCleared() }

    companion object {
        /** {0,1,2,4} → "1-3, 5" */
        fun formatRanges(set: Set<Int>): String {
            val s = set.sorted()
            if (s.isEmpty()) return ""
            val parts = ArrayList<String>()
            var start = s[0]; var prev = s[0]
            for (i in 1..s.size) {
                val cur = s.getOrNull(i)
                if (cur == null || cur != prev + 1) {
                    parts += if (start == prev) "${start + 1}" else "${start + 1}-${prev + 1}"
                    if (cur != null) start = cur
                }
                if (cur != null) prev = cur
            }
            return parts.joinToString(", ")
        }
    }
}

// ============================================================ 7. Compress PDF

data class CompressPdfResult(val before: Long, val after: Long)

class CompressPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _preset = MutableStateFlow(PdfCompressPreset.BALANCED)
    val preset: StateFlow<PdfCompressPreset> = _preset
    private val _result = MutableStateFlow<CompressPdfResult?>(null)
    val result: StateFlow<CompressPdfResult?> = _result

    fun pick(uri: Uri) { runTask("Reading PDF…") { _result.value = null; _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setPreset(p: PdfCompressPreset) { _preset.value = p }

    fun export() {
        val src = _source.value ?: return
        val p = _preset.value
        runTask("Compressing PDF…", write = true) {
            val tmp = File(ctx.cacheDir, "pdf_compress_${System.nanoTime()}.pdf")
            try {
                tmp.outputStream().use { out ->
                    PdfEngine.compress(src.file, p.dpi, p.jpegQuality, out) { setProgress("Optimising pages…", it) }
                }
                if (tmp.length() >= src.sizeBytes) {
                    _result.value = CompressPdfResult(src.sizeBytes, tmp.length())
                    showInfo("This PDF is already compact – try the Low preset for smaller scans.")
                    return@runTask
                }
                val name = "${FileUtils.baseName(src.name)}_compressed.pdf"
                val saved = Storage.saveFile(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Compress PDF", tmp)
                val delta = (src.sizeBytes - saved.sizeBytes).coerceAtLeast(0L)
                container.settings.addBytesSaved(delta)
                _result.value = CompressPdfResult(src.sizeBytes, saved.sizeBytes)
                publish(listOf(saved))
            } finally {
                tmp.delete()
            }
        }
    }
}

// ============================================================ 8. Rotate & Delete Pages

class EditPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _rotations = MutableStateFlow<Map<Int, Int>>(emptyMap())
    val rotations: StateFlow<Map<Int, Int>> = _rotations
    private val _deleted = MutableStateFlow<Set<Int>>(emptySet())
    val deleted: StateFlow<Set<Int>> = _deleted

    var thumbs: PdfThumbs? = null
        private set

    fun pick(uri: Uri) {
        runTask("Reading PDF…") {
            val src = PdfInputs.load(getApplication(), uri)
            thumbs?.close()
            thumbs = PdfThumbs(src.file)
            _rotations.value = emptyMap()
            _deleted.value = emptySet()
            _source.value = src
        }
    }

    fun rotate(index: Int, degrees: Int) = _rotations.update { m ->
        val v = (((m[index] ?: 0) + degrees) % 360 + 360) % 360
        if (v == 0) m - index else m + (index to v)
    }
    fun rotateAll(degrees: Int) {
        val n = _source.value?.pageCount ?: return
        _rotations.update { m ->
            val out = HashMap(m)
            for (i in 0 until n) {
                val v = (((m[i] ?: 0) + degrees) % 360 + 360) % 360
                if (v == 0) out.remove(i) else out[i] = v
            }
            out
        }
    }
    fun toggleDelete(index: Int) = _deleted.update { if (index in it) it - index else it + index }
    fun reset() { _rotations.value = emptyMap(); _deleted.value = emptySet() }

    fun export() {
        val src = _source.value ?: return
        val rot = _rotations.value
        val del = _deleted.value
        if (rot.isEmpty() && del.isEmpty()) { showError("Rotate or delete at least one page first."); return }
        runTask("Applying changes…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_edited.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Rotate & Delete") { out ->
                PdfEngine.edit(src.file, rot, del, null, out)
            }
            publish(listOf(saved))
        }
    }

    override fun onCleared() { thumbs?.close(); super.onCleared() }
}

// ============================================================ 9. Add Page Numbers

class PageNumbersViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _options = MutableStateFlow(PageNumberOptions())
    val options: StateFlow<PageNumberOptions> = _options

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setFormat(f: PageNumberFormat) = _options.update { it.copy(format = f) }
    fun setPosition(p: PageNumberPosition) = _options.update { it.copy(position = p) }
    fun setFontSize(s: Float) = _options.update { it.copy(fontSize = s) }
    fun setStartNumber(n: Int) = _options.update { it.copy(startNumber = n.coerceAtLeast(1)) }

    fun export() {
        val src = _source.value ?: return
        val opts = _options.value
        runTask("Stamping page numbers…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_numbered.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Add Page Numbers") { out ->
                PdfEngine.addPageNumbers(src.file, opts, out) { setProgress("Numbering pages…", it) }
            }
            publish(listOf(saved))
            showInfo("Added page numbers to ${src.pageCount} page(s).")
        }
    }
}

// ============================================================ 10. Add Text Watermark

class WatermarkPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _text = MutableStateFlow("CONFIDENTIAL")
    val text: StateFlow<String> = _text
    private val _opacity = MutableStateFlow(0.22f)
    val opacity: StateFlow<Float> = _opacity
    private val _color = MutableStateFlow(0xFF64748B.toInt())
    val color: StateFlow<Int> = _color

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setText(v: String) { _text.value = v.take(40) }
    fun setOpacity(v: Float) { _opacity.value = v }
    fun setColor(c: Int) { _color.value = c }

    fun export() {
        val src = _source.value ?: return
        val txt = _text.value.trim()
        if (txt.isEmpty()) { showError("Enter watermark text first."); return }
        val wm = WatermarkOptions(txt, _opacity.value, _color.value)
        runTask("Applying watermark…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_watermarked.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Text Watermark") { out ->
                PdfEngine.watermarkPdf(src.file, wm, out) { setProgress("Watermarking…", it) }
            }
            publish(listOf(saved))
            showInfo("Watermark stamped onto ${src.pageCount} page(s).")
        }
    }
}

// ============================================================ 11. Protect / Encrypt PDF

class ProtectPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _userPassword = MutableStateFlow("")
    val userPassword: StateFlow<String> = _userPassword
    private val _ownerPassword = MutableStateFlow("")
    val ownerPassword: StateFlow<String> = _ownerPassword
    private val _allowPrint = MutableStateFlow(true)
    val allowPrint: StateFlow<Boolean> = _allowPrint
    private val _allowCopy = MutableStateFlow(false)
    val allowCopy: StateFlow<Boolean> = _allowCopy

    fun pick(uri: Uri) { runTask("Reading PDF…") { _source.value = PdfInputs.load(getApplication(), uri) } }
    fun setUserPassword(v: String) { _userPassword.value = v }
    fun setOwnerPassword(v: String) { _ownerPassword.value = v }
    fun setAllowPrint(v: Boolean) { _allowPrint.value = v }
    fun setAllowCopy(v: Boolean) { _allowCopy.value = v }

    fun export() {
        val src = _source.value ?: return
        val pass = _userPassword.value
        if (pass.length < 4) { showError("Use at least 4 characters for the password."); return }
        runTask("Encrypting PDF…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_protected.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Protect PDF") { out ->
                PdfEngine.protect(src.file, pass, _ownerPassword.value, _allowPrint.value, _allowCopy.value, out)
            }
            publish(listOf(saved))
            showInfo("PDF encrypted with 128-bit password protection.")
        }
    }
}

// ============================================================ 12. Unlock / Decrypt PDF

class UnlockPdfViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<LockedPdfSource?>(null)
    val source: StateFlow<LockedPdfSource?> = _source
    private val _password = MutableStateFlow("")
    val password: StateFlow<String> = _password

    fun pick(uri: Uri) {
        runTask("Inspecting PDF…") {
            _password.value = ""
            _source.value = PdfInputs.loadForUnlock(getApplication(), uri)
        }
    }
    fun setPassword(v: String) { _password.value = v }

    fun export() {
        val src = _source.value ?: return
        if (!src.isEncrypted) {
            showInfo("This PDF is not password-encrypted.")
            return
        }
        val pass = _password.value
        runTask("Decrypting PDF…", write = true) {
            val name = "${FileUtils.baseName(src.name)}_unlocked.pdf"
            val saved = Storage.save(ctx, OutputKind.DOCUMENT, null, name, "application/pdf", "Unlock PDF") { out ->
                PdfEngine.unlock(src.file, pass, out)
            }
            publish(listOf(saved))
            showInfo("Password restrictions removed.")
        }
    }
}
