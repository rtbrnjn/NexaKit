package com.mediaforge.studio.vm

import android.app.Application
import android.net.Uri
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.pdf.PdfCompressPreset
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import javax.inject.Inject

// ============================================================ 5. Merge PDFs

@HiltViewModel
class MergePdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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

@HiltViewModel
class SplitPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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

@HiltViewModel
class CompressPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore
) : ToolViewModel(app, coordinator) {
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
                settings.addBytesSaved(delta)
                _result.value = CompressPdfResult(src.sizeBytes, saved.sizeBytes)
                publish(listOf(saved))
            } finally {
                tmp.delete()
            }
        }
    }
}

// ============================================================ 8. Rotate & Delete Pages

@HiltViewModel
class EditPdfViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator
) : ToolViewModel(app, coordinator) {
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
