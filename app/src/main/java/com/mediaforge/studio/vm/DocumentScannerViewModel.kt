package com.mediaforge.studio.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfPageMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject

/** Manages ML Kit Document Scanner results and exports them as PDF (`Documents/DocPix`) or JPG (`Pictures/DocPix`). */
@HiltViewModel
class DocumentScannerViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {

    private val sessionDir: File
        get() = File(ctx.cacheDir, "docpix_scan_session").apply { if (!exists()) mkdirs() }

    private val _pageFiles = MutableStateFlow<List<File>>(emptyList())
    val pageFiles: StateFlow<List<File>> = _pageFiles

    private val _pdfFile = MutableStateFlow<File?>(null)
    val hasPdf: Boolean get() = _pdfFile.value?.exists() == true

    private val _filePrefix = MutableStateFlow("Scan_${FileUtils.timestamp()}")
    val filePrefix: StateFlow<String> = _filePrefix

    fun setFilePrefix(value: String) {
        _filePrefix.value = value.replace(Regex("[^A-Za-z0-9_\\- ]"), "").take(48)
    }

    fun onScannerError(msg: String) {
        showError(msg)
    }

    fun clearSession() {
        _pageFiles.value = emptyList()
        _pdfFile.value = null
        viewModelScope.launch(Dispatchers.IO) {
            sessionDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun onScanResult(result: GmsDocumentScanningResult?) {
        if (result == null) return
        val pages = result.pages.orEmpty()
        val pdf = result.pdf
        if (pages.isEmpty() && pdf == null) return

        runTask("Memproses halaman hasil scan…") {
            val dir = sessionDir
            dir.listFiles()?.forEach { it.delete() }

            val copiedPages = ArrayList<File>(pages.size)
            pages.forEachIndexed { index, page ->
                val target = File(dir, "scan_page_${index + 1}.jpg")
                if (copyUriToFile(page.imageUri, target)) {
                    copiedPages += target
                }
            }

            val copiedPdf = pdf?.let { docPdf ->
                val target = File(dir, "scan_document.pdf")
                if (copyUriToFile(docPdf.uri, target)) target else null
            }

            _pageFiles.value = copiedPages
            _pdfFile.value = copiedPdf
            _filePrefix.value = "Scan_${FileUtils.timestamp()}"

            val count = copiedPages.size.takeIf { it > 0 } ?: (pdf?.pageCount ?: 1)
            showInfo("$count halaman selesai dipindai. Pilih Simpan PDF atau Gambar.")
        }
    }

    fun saveAsPdf() {
        val pages = _pageFiles.value
        val readyPdf = _pdfFile.value?.takeIf { it.exists() && it.length() > 0L }
        if (pages.isEmpty() && readyPdf == null) {
            showError("Belum ada dokumen yang dipindai.")
            return
        }
        val name = "${sanitizedPrefix()}.pdf"
        runTask("Menyimpan PDF ke Documents/DocPix…", write = true) {
            val saved = if (readyPdf != null) {
                Storage.saveFile(
                    ctx = ctx,
                    kind = OutputKind.DOCUMENT,
                    subFolder = null,
                    displayName = name,
                    mime = "application/pdf",
                    tool = "Document Scanner",
                    source = readyPdf
                )
            } else {
                Storage.save(
                    ctx = ctx,
                    kind = OutputKind.DOCUMENT,
                    subFolder = null,
                    displayName = name,
                    mime = "application/pdf",
                    tool = "Document Scanner"
                ) { out ->
                    PdfEngine.imagesToPdf(
                        ctx = ctx,
                        images = pages.map { Uri.fromFile(it) },
                        mode = PdfPageMode.FIT,
                        jpegQuality = 0.92f,
                        out = out
                    ) { setProgress("Menyusun PDF…", it) }
                }
            }
            publish(listOf(saved))
            showInfo("PDF tersimpan di ${saved.folder}/${saved.name} (${FileUtils.formatBytes(saved.sizeBytes)}).")
        }
    }

    fun saveAsImages() {
        val pages = _pageFiles.value
        if (pages.isEmpty()) {
            showError("Belum ada halaman gambar hasil scan.")
            return
        }
        val base = sanitizedPrefix()
        runTask("Menyimpan gambar JPG ke Pictures/DocPix…", write = true) {
            val savedList = ArrayList<SavedFile>(pages.size)
            pages.forEachIndexed { idx, file ->
                setProgress("Menyimpan halaman ${idx + 1}/${pages.size}…", (idx + 1f) / pages.size)
                val pageName = if (pages.size == 1) "$base.jpg" else "${base}_p${idx + 1}.jpg"
                savedList += Storage.saveFile(
                    ctx = ctx,
                    kind = OutputKind.IMAGE,
                    subFolder = null,
                    displayName = pageName,
                    mime = "image/jpeg",
                    tool = "Document Scanner",
                    source = file
                )
            }
            publish(savedList)
            showInfo("${savedList.size} gambar JPG tersimpan di Pictures/DocPix.")
        }
    }

    private fun sanitizedPrefix(): String =
        _filePrefix.value.trim().replace(Regex("\\s+"), "_").ifBlank { "Scan_${FileUtils.timestamp()}" }

    private fun copyUriToFile(uri: Uri, target: File): Boolean = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output, 64 * 1024)
            }
        } != null
    }.getOrDefault(false)
}
