package com.mediaforge.studio.vm

import android.app.Application
import android.net.Uri
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.pdf.LocalOcrEngine
import com.mediaforge.studio.pdf.PdfEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Offline PDF text extractor (selectable PDF text via PDFBox + automatic on-device ML Kit OCR
 * fallback for scanned documents), with 1-tap handoff to AI Studio.
 */
@HiltViewModel
class ExtractPdfTextViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val localOcr: LocalOcrEngine
) : ToolViewModel(app, coordinator) {

    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source

    private val _pageRange = MutableStateFlow("all")
    val pageRange: StateFlow<String> = _pageRange

    private val _extractedText = MutableStateFlow("")
    val extractedText: StateFlow<String> = _extractedText

    private val _usedOcr = MutableStateFlow(false)
    val usedOcr: StateFlow<Boolean> = _usedOcr

    private val _summary = MutableStateFlow("")
    val summary: StateFlow<String> = _summary

    fun pick(uri: Uri) {
        runTask("Memuat & mengekstrak teks PDF…") {
            val src = PdfInputs.load(getApplication(), uri)
            val initialRange = if (src.pageCount <= 10) "all" else "1-10"
            _source.value = src
            _pageRange.value = initialRange
            extractInternal(src, initialRange, forceOcr = false)
        }
    }

    fun setPageRange(range: String) {
        _pageRange.value = range
    }

    fun extract(forceOcr: Boolean = false) {
        val src = _source.value ?: return
        runTask(if (forceOcr) "Menjalankan OCR lokal offline…" else "Mengekstrak teks PDF…") {
            extractInternal(src, _pageRange.value, forceOcr = forceOcr)
        }
    }

    fun clear() {
        _source.value = null
        _pageRange.value = "all"
        _extractedText.value = ""
        _usedOcr.value = false
        _summary.value = ""
        dismissMessage()
    }

    private suspend fun extractInternal(src: PdfSource, rangeSpec: String, forceOcr: Boolean) {
        val effectiveRange = rangeSpec.ifBlank { "all" }
        withContext(Dispatchers.IO) {
            runCatching { PDFBoxResourceLoader.init(ctx.applicationContext) }
        }

        if (!forceOcr) {
            setProgress("Membaca lapisan teks PDF…")
            val extracted = withContext(Dispatchers.IO) {
                PdfEngine.extractText(src.file, effectiveRange, MAX_EXTRACT_CHARS)
            }
            if (extracted.text.isNotBlank()) {
                _extractedText.value = extracted.text
                _usedOcr.value = false
                val truncNote = if (extracted.wasTruncated) " (batas ${MAX_EXTRACT_CHARS / 1000}K)" else ""
                _summary.value = "${extracted.text.length} karakter • ${extracted.extractedPages}/${extracted.totalPages} hal • Teks PDF$truncNote"
                showInfo("Berhasil mengekstrak ${extracted.text.length} karakter dari ${extracted.extractedPages} halaman.")
                return
            }
        }

        // Scanned PDF (no selectable text) or user forced OCR: run offline ML Kit OCR.
        val pages = PdfEngine.parseRanges(effectiveRange, src.pageCount)
        val ocr = localOcr.ocrPages(src.file, pages, MAX_EXTRACT_CHARS) { cur, tot ->
            setProgress("OCR lokal halaman $cur / $tot…", cur.toFloat() / tot)
        }
        _usedOcr.value = true
        _extractedText.value = ocr.text
        if (ocr.text.isNotBlank()) {
            val truncNote = if (ocr.wasTruncated) " (batas ${MAX_EXTRACT_CHARS / 1000}K)" else ""
            _summary.value = "OCR lokal • ${ocr.text.length} karakter • ${ocr.pagesProcessed} hal$truncNote"
            showInfo("OCR lokal selesai: ${ocr.text.length} karakter dari ${ocr.pagesProcessed} halaman.")
        } else {
            _summary.value = "Tidak ada teks terbaca pada halaman terpilih."
            showError("Tidak ada teks yang terbaca pada rentang halaman ini.")
        }
    }

    companion object {
        const val MAX_EXTRACT_CHARS = 120_000
    }
}
