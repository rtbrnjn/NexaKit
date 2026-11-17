package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.pdf.DocBlock
import com.mediaforge.studio.pdf.DocumentPdfRenderer
import com.mediaforge.studio.pdf.DocxParser
import com.mediaforge.studio.pdf.MarkdownParser
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.pdf.PdfPageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ============================================================ 1. PDF → Word (.docx)

@HiltViewModel
class PdfToDocxViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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

@HiltViewModel
class TextToPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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

@HiltViewModel
class ImagesToPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore
) : ToolViewModel(app, coordinator) {
    private val _images = MutableStateFlow<List<ImageEntry>>(emptyList())
    val images: StateFlow<List<ImageEntry>> = _images
    private val _mode = MutableStateFlow(PdfPageMode.A4)
    val mode: StateFlow<PdfPageMode> = _mode
    private val _quality = MutableStateFlow(85)
    val quality: StateFlow<Int> = _quality

    init {
        viewModelScope.launch {
            _mode.value = settings.defaultPdfPageMode.first()
            _quality.value = settings.defaultQuality.first().coerceIn(50, 100)
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

@HiltViewModel
class PdfToImagesViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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
