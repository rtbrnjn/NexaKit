package com.mediaforge.studio.vm

import android.app.Application
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiRouter
import com.mediaforge.studio.ai.DocTask
import com.mediaforge.studio.ai.GeminiContent
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.db.AiHistoryDao
import com.mediaforge.studio.data.db.AiHistoryEntity
import com.mediaforge.studio.data.db.AiHistoryKind
import com.mediaforge.studio.pdf.LocalOcrEngine
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfPageRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

enum class ExtractDepth(val label: String, val maxChars: Int) {
    FAST("Fast · 12K", 12_000),
    STANDARD("Standard · 30K", 30_000),
    DEEP("Deep · 80K", 80_000)
}

data class ChatMessage(
    val fromUser: Boolean,
    val text: String,
    val label: String? = null,
    val isStreaming: Boolean = false,
    val streamPhase: String? = null
)

@HiltViewModel
class PdfAiViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val aiRouter: AiRouter,
    private val aiHistory: AiHistoryDao,
    private val localOcr: LocalOcrEngine
) : ToolViewModel(app, coordinator) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _pageRange = MutableStateFlow("1-10")
    val pageRange: StateFlow<String> = _pageRange
    private val _depth = MutableStateFlow(ExtractDepth.STANDARD)
    val depth: StateFlow<ExtractDepth> = _depth
    private val _visionMode = MutableStateFlow(false)
    val visionMode: StateFlow<Boolean> = _visionMode
    private val _extractInfo = MutableStateFlow("")
    val extractInfo: StateFlow<String> = _extractInfo
    private val _isScannedPdf = MutableStateFlow(false)
    val isScannedPdf: StateFlow<Boolean> = _isScannedPdf
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming
    private val _activeEngine = MutableStateFlow<AiEngine?>(null)
    val activeEngine: StateFlow<AiEngine?> = _activeEngine

    private var docText: String? = null
    private var pageImages: List<ByteArray> = emptyList()
    private val history = ArrayList<GeminiContent>()
    private var streamJob: Job? = null

    fun pick(uri: Uri) {
        streamJob?.cancel()
        _isStreaming.value = false
        _activeEngine.value = null
        runTask("Mengekstrak teks lokal...") {
            val src = PdfInputs.load(getApplication(), uri)
            val initialRange = if (src.pageCount <= 10) "all" else "1-10"
            _pageRange.value = initialRange
            _source.value = src
            history.clear()
            _messages.value = emptyList()
            extractNow(src, initialRange, _depth.value, _visionMode.value)
        }
    }

    /** Loads pre-extracted PDF text directly from the PDF Suite Extractor and optionally summarizes it. */
    fun loadExtractedDocument(
        src: PdfSource,
        text: String,
        usedOcr: Boolean,
        range: String = "all",
        autoSummarize: Boolean = false
    ) {
        streamJob?.cancel()
        _isStreaming.value = false
        _activeEngine.value = null
        history.clear()
        _messages.value = emptyList()
        _source.value = src
        _pageRange.value = range.ifBlank { "all" }
        _visionMode.value = false
        _isScannedPdf.value = usedOcr
        docText = text
        pageImages = emptyList()
        _extractInfo.value = if (usedOcr) {
            "OCR lokal: ${text.length} karakter (dari PDF Suite)"
        } else {
            "${text.length} karakter lokal (dari PDF Suite)"
        }
        if (autoSummarize && text.isNotBlank()) {
            val hasKey = settings.groqApiKey.value.isNotBlank() || settings.apiKey.value.isNotBlank()
            if (connectivity.online.value && hasKey) {
                runDocTask(DocTask.SUMMARY)
            } else {
                showInfo("Teks PDF sudah dimuat (${text.length} karakter). Sambungkan internet & pastikan API Key tersimpan di Settings untuk meringkas.")
            }
        }
    }

    fun setPageRange(range: String) { _pageRange.value = range }

    fun setDepth(d: ExtractDepth) {
        _depth.value = d
        val src = _source.value ?: return
        runTask("Mengekstrak teks lokal...") {
            extractNow(src, _pageRange.value, d, _visionMode.value)
        }
    }

    fun setVisionMode(enabled: Boolean) {
        _visionMode.value = enabled
        val src = _source.value ?: return
        runTask(if (enabled) "Menyiapkan halaman Vision Mode..." else "Menjalankan OCR lokal...") {
            extractNow(src, _pageRange.value, _depth.value, enabled)
        }
    }

    fun applyRange() {
        val src = _source.value ?: return
        runTask("Mengekstrak teks lokal...") {
            extractNow(src, _pageRange.value, _depth.value, _visionMode.value)
        }
    }

    private suspend fun extractNow(
        src: PdfSource,
        rangeSpec: String,
        depth: ExtractDepth,
        useVision: Boolean
    ) {
        val effectiveRange = rangeSpec.ifBlank { "all" }
        setProgress("Mengekstrak teks lokal...")
        val extracted = withContext(Dispatchers.IO) {
            PdfEngine.extractText(src.file, effectiveRange, depth.maxChars)
        }
        if (extracted.text.isNotBlank()) {
            _isScannedPdf.value = false
            docText = extracted.text
            pageImages = emptyList()
            val truncNote = if (extracted.wasTruncated) " (batas ${depth.maxChars / 1000}K)" else ""
            _extractInfo.value = "${extracted.text.length} karakter lokal • ${extracted.extractedPages}/${extracted.totalPages} hal$truncNote"
            return
        }

        // Scanned PDF (no text layer): run Local ML Kit OCR first unless Vision Mode is explicitly enabled.
        _isScannedPdf.value = true
        val pages = PdfEngine.parseRanges(effectiveRange, src.pageCount)
        if (!useVision) {
            val ocr = localOcr.ocrPages(src.file, pages, depth.maxChars) { cur, tot ->
                setProgress("OCR lokal halaman $cur/$tot...", cur.toFloat() / tot)
            }
            if (ocr.text.isNotBlank()) {
                docText = ocr.text
                pageImages = emptyList()
                val truncNote = if (ocr.wasTruncated) " (batas ${depth.maxChars / 1000}K)" else ""
                _extractInfo.value = "OCR lokal: ${ocr.text.length} karakter • ${ocr.pagesProcessed} hal$truncNote"
            } else {
                docText = null
                pageImages = emptyList()
                _extractInfo.value = "Scanned PDF tanpa teks terbaca — aktifkan Vision Mode jika perlu membaca gambar."
            }
        } else {
            val pagesToRender = pages.take(4)
            setProgress("Vision Mode: merender ${pagesToRender.size} halaman...")
            val images = withContext(Dispatchers.Default) {
                ArrayList<ByteArray>(pagesToRender.size).apply {
                    PdfPageRenderer(src.file).use { r ->
                        pagesToRender.forEach { pageIdx ->
                            val bmp = r.renderToWidth(pageIdx, 960)
                            try {
                                add(BitmapUtils.jpegBytes(bmp, 75))
                            } finally {
                                bmp.recycle()
                            }
                        }
                    }
                }
            }
            docText = null
            pageImages = images
            _extractInfo.value = "Vision Mode aktif: ${images.size} gambar halaman siap dikirim."
        }
    }

    fun runDocTask(task: DocTask) = send(task.prompt, task.label)
    fun ask(question: String) { if (question.isNotBlank()) send(question.trim(), null) }

    fun stopStreaming() {
        streamJob?.cancel()
        _isStreaming.value = false
        _activeEngine.value = null
        _messages.update { list ->
            if (list.isNotEmpty() && !list.last().fromUser && list.last().isStreaming) {
                list.dropLast(1) + list.last().copy(isStreaming = false, streamPhase = null)
            } else list
        }
    }

    fun clearChat() {
        streamJob?.cancel()
        _isStreaming.value = false
        _activeEngine.value = null
        history.clear()
        _messages.value = emptyList()
    }

    private fun send(prompt: String, label: String?) {
        val src = _source.value ?: run { showError("Open a PDF first."); return }
        if (docText.isNullOrBlank() && pageImages.isEmpty()) {
            showError("Tidak ada teks yang berhasil diekstrak. Aktifkan Vision Mode untuk membaca gambar.")
            return
        }
        if (!connectivity.online.value) {
            showError("Offline Mode active – connect to the internet to query AI Studio.")
            return
        }
        streamJob?.cancel()
        val display = label ?: prompt
        val turn = GeminiContent.user(prompt)
        val snapshot = history + turn

        _messages.update {
            it + ChatMessage(fromUser = true, text = display, label = label) +
                ChatMessage(fromUser = false, text = "", isStreaming = true, streamPhase = "Menyiapkan mesin AI...")
        }
        _isStreaming.value = true

        streamJob = viewModelScope.launch {
            val groqKey = settings.groqApiKey.value
            val geminiKey = settings.apiKey.value
            val geminiModel = settings.model.first()
            val pref = settings.aiPreference.first()
            try {
                val outcome = aiRouter.streamChatAboutDocument(
                    preference = pref,
                    groqKey = groqKey,
                    geminiKey = geminiKey,
                    geminiModel = geminiModel,
                    documentText = docText,
                    pageImages = pageImages,
                    history = snapshot.takeLast(14),
                    onStatus = { engine, _, phase ->
                        _activeEngine.value = engine
                        _messages.update { list ->
                            if (list.isEmpty()) list
                            else list.dropLast(1) + list.last().copy(streamPhase = phase)
                        }
                    },
                    onDelta = { partial ->
                        _messages.update { list ->
                            if (list.isEmpty()) list
                            else list.dropLast(1) + list.last().copy(text = partial, isStreaming = true)
                        }
                    }
                )
                val finalReply = outcome.text
                history += turn
                history += GeminiContent.model(finalReply)
                _messages.update { list ->
                    if (list.isEmpty()) list
                    else list.dropLast(1) + list.last().copy(text = finalReply, isStreaming = false, streamPhase = null)
                }
                if (finalReply.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        aiHistory.insert(
                            AiHistoryEntity(
                                kind = AiHistoryKind.PDF_SUMMARY,
                                title = src.name,
                                subtitle = "$display • ${outcome.modelId} • Pages ${_pageRange.value}",
                                content = finalReply,
                                model = outcome.modelId
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
                // Handled by stopStreaming / new request
            } catch (e: GeminiException) {
                _messages.update { list -> if (list.size >= 2) list.dropLast(2) else emptyList() }
                showError(e.message ?: "AI engine error")
            } catch (e: Exception) {
                _messages.update { list -> if (list.size >= 2) list.dropLast(2) else emptyList() }
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                _isStreaming.value = false
            }
        }
    }
}
