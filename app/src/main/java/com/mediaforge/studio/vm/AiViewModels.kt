package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiPreference
import com.mediaforge.studio.ai.AiRouting
import com.mediaforge.studio.ai.CopyLanguage
import com.mediaforge.studio.ai.DocTask
import com.mediaforge.studio.ai.GeminiContent
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.ProductCopy
import com.mediaforge.studio.container
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.data.DEFAULT_MODEL
import com.mediaforge.studio.data.DEFAULT_MODELS
import com.mediaforge.studio.data.ThemeMode
import com.mediaforge.studio.data.db.AiHistoryEntity
import com.mediaforge.studio.data.db.AiHistoryKind
import com.mediaforge.studio.pdf.LocalOcrEngine
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.pdf.PdfPageRenderer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ============================================================ 1. Product Photo Copywriter (Streaming + 768px/75% JPEG)

class CopywriterViewModel(app: Application) : ToolViewModel(app) {
    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview
    private val _payloadSize = MutableStateFlow(0L)
    val payloadSize: StateFlow<Long> = _payloadSize
    private val _language = MutableStateFlow(CopyLanguage.ENGLISH)
    val language: StateFlow<CopyLanguage> = _language
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText
    private val _isStreaming = MutableStateFlow(false)
    val isStreaming: StateFlow<Boolean> = _isStreaming
    private val _liveStatus = MutableStateFlow<String?>(null)
    val liveStatus: StateFlow<String?> = _liveStatus
    private val _activeEngine = MutableStateFlow<AiEngine?>(null)
    val activeEngine: StateFlow<AiEngine?> = _activeEngine
    private val _result = MutableStateFlow<ProductCopy?>(null)
    val result: StateFlow<ProductCopy?> = _result

    private var compressedJpeg: ByteArray? = null
    private var streamJob: Job? = null

    fun pick(uri: Uri) {
        streamJob?.cancel()
        _isStreaming.value = false
        _activeEngine.value = null
        _liveStatus.value = "Mengompresi gambar lokal (768px)..."
        _uri.value = uri
        _result.value = null
        _streamingText.value = ""
        compressedJpeg = null
        _payloadSize.value = 0L

        viewModelScope.launch(Dispatchers.IO) {
            // Downscale in memory to max 768px and compress to 75% JPEG before Base64 encoding.
            val bmp = BitmapUtils.decode(ctx, uri, 768)
            if (bmp == null) {
                _liveStatus.value = null
                showError("Could not open that image.")
            } else {
                val jpeg = BitmapUtils.jpegBytes(bmp, 75)
                compressedJpeg = jpeg
                _payloadSize.value = jpeg.size.toLong()
                _preview.value = bmp
                _liveStatus.value = null
            }
        }
    }

    fun setLanguage(l: CopyLanguage) { _language.value = l }
    fun setNotes(n: String) { _notes.value = n.take(300) }

    fun stop() {
        streamJob?.cancel()
        _isStreaming.value = false
        _liveStatus.value = null
        _activeEngine.value = null
    }

    fun generate() {
        val jpeg = compressedJpeg ?: run { showError("Pick a product photo first."); return }
        if (!container.connectivity.online.value) {
            showError("Offline Mode active – connect to the internet to use AI Studio.")
            return
        }
        streamJob?.cancel()
        _result.value = null
        _streamingText.value = ""
        _isStreaming.value = true
        _liveStatus.value = "Menyiapkan mesin AI..."

        streamJob = viewModelScope.launch {
            val groqKey = container.settings.groqApiKey.value
            val geminiKey = container.settings.apiKey.value
            val geminiModel = container.settings.model.first()
            val pref = container.settings.aiPreference.first()
            val lang = _language.value
            val sellerNotes = _notes.value
            try {
                val outcome = container.aiRouter.streamProductCopy(
                    preference = pref,
                    groqKey = groqKey,
                    geminiKey = geminiKey,
                    geminiModel = geminiModel,
                    imageJpeg = jpeg,
                    language = lang,
                    notes = sellerNotes,
                    onStatus = { engine, _, status ->
                        _activeEngine.value = engine
                        _liveStatus.value = status
                    },
                    onDelta = { text ->
                        _streamingText.value = text
                        _result.value = ProductCopy.parseStreaming(text)
                    }
                )
                val finalCopy = ProductCopy.parseStreaming(outcome.text)
                _result.value = finalCopy
                if (finalCopy.title.isNotBlank() || finalCopy.caption.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        container.aiHistory.insert(
                            AiHistoryEntity(
                                kind = AiHistoryKind.COPYWRITING,
                                title = finalCopy.title.ifBlank { "Product Copywriting" },
                                subtitle = "${lang.label} • ${outcome.modelId}",
                                content = finalCopy.asPlainText().trim(),
                                model = outcome.modelId
                            )
                        )
                    }
                }
            } catch (_: CancellationException) {
                // User cancelled or picked another photo
            } catch (e: GeminiException) {
                showError(e.message ?: "AI engine error")
            } catch (e: Exception) {
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                _isStreaming.value = false
                _liveStatus.value = null
            }
        }
    }
}

// ============================================================ 2. PDF Summariser & Chat (Local OCR First + SSE Streaming)

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

class PdfAiViewModel(app: Application) : ToolViewModel(app) {
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
            val ocr = LocalOcrEngine.ocrPages(src.file, pages, depth.maxChars) { cur, tot ->
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
        if (!container.connectivity.online.value) {
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
            val groqKey = container.settings.groqApiKey.value
            val geminiKey = container.settings.apiKey.value
            val geminiModel = container.settings.model.first()
            val pref = container.settings.aiPreference.first()
            try {
                val outcome = container.aiRouter.streamChatAboutDocument(
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
                        container.aiHistory.insert(
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

// ============================================================ 3. AI History Vault ViewModel

class AiVaultViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = app.container.aiHistory

    val entries: StateFlow<List<AiHistoryEntity>> =
        dao.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun delete(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.deleteById(id) }
    }

    fun clearAll() {
        viewModelScope.launch(Dispatchers.IO) { dao.clearAll() }
    }
}

// ============================================================ 4. Settings ViewModel

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Connected(val modelCount: Int) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val keyInput = MutableStateFlow("")
    private var editedGemini = false

    val groqKeyInput = MutableStateFlow("")
    private var editedGroq = false

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    private val _groqConnection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val groqConnection: StateFlow<ConnectionState> = _groqConnection

    private val _groqStatus = MutableStateFlow<String?>(null)
    val groqStatus: StateFlow<String?> = _groqStatus

    val savedKey: StateFlow<String> = c.settings.apiKey
    val savedGroqKey: StateFlow<String> = c.settings.groqApiKey
    val themeMode: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
    val aiPreference: StateFlow<AiPreference> = c.settings.aiPreference.stateIn(viewModelScope, SharingStarted.Eagerly, AiPreference.AUTO)
    val model: StateFlow<String> = c.settings.model.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MODEL)
    val models: StateFlow<List<String>> = c.settings.knownModels.stateIn(viewModelScope, SharingStarted.Eagerly, DEFAULT_MODELS)
    val autoShow: StateFlow<Boolean> = c.settings.autoShowResults.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val defaultQuality: StateFlow<Int> = c.settings.defaultQuality.stateIn(viewModelScope, SharingStarted.Eagerly, 80)
    val defaultPdfPageMode: StateFlow<PdfPageMode> = c.settings.defaultPdfPageMode.stateIn(viewModelScope, SharingStarted.Eagerly, PdfPageMode.A4)
    val autoStripExif: StateFlow<Boolean> = c.settings.autoStripExif.stateIn(viewModelScope, SharingStarted.Eagerly, true)
    val totalBytesSaved: StateFlow<Long> = c.settings.totalBytesSaved.stateIn(viewModelScope, SharingStarted.Eagerly, 0L)
    val history: StateFlow<List<SavedFile>> = c.history.entries.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val online: StateFlow<Boolean> = c.connectivity.online

    private val _cacheBytes = MutableStateFlow(0L)
    val cacheBytes: StateFlow<Long> = _cacheBytes

    init {
        viewModelScope.launch {
            c.settings.apiKey.collect { if (!editedGemini) keyInput.value = it }
        }
        viewModelScope.launch {
            c.settings.groqApiKey.collect { if (!editedGroq) groqKeyInput.value = it }
        }
        refreshCacheSize()
    }

    fun onKeyChanged(v: String) {
        editedGemini = true
        keyInput.value = v
        _connection.value = ConnectionState.Idle
        _status.value = null
    }

    fun saveKey() {
        viewModelScope.launch {
            c.settings.saveApiKey(keyInput.value)
            _status.value = if (keyInput.value.isBlank()) "Gemini API key removed." else "Gemini API key saved securely on this device."
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            c.settings.saveApiKey("")
            editedGemini = false
            keyInput.value = ""
            _connection.value = ConnectionState.Idle
            _status.value = "Gemini API key removed."
        }
    }

    fun testConnection() = runConnectionCheck(
        key = keyInput.value.trim(),
        stateFlow = _connection,
        save = { c.settings.saveApiKey(it) },
        ping = { k ->
            val available = c.gemini.testConnection(k)
            if (available.isNotEmpty()) {
                c.settings.setKnownModels((DEFAULT_MODELS + available).distinct())
            }
            available.size
        }
    )

    fun onGroqKeyChanged(v: String) {
        editedGroq = true
        groqKeyInput.value = v
        _groqConnection.value = ConnectionState.Idle
        _groqStatus.value = null
    }

    fun saveGroqKey() {
        viewModelScope.launch {
            c.settings.saveGroqApiKey(groqKeyInput.value)
            _groqStatus.value = if (groqKeyInput.value.isBlank()) "Groq API key removed." else "Groq API key saved securely on this device."
        }
    }

    fun clearGroqKey() {
        viewModelScope.launch {
            c.settings.saveGroqApiKey("")
            editedGroq = false
            groqKeyInput.value = ""
            _groqConnection.value = ConnectionState.Idle
            _groqStatus.value = "Groq API key removed."
        }
    }

    fun testGroqConnection() = runConnectionCheck(
        key = groqKeyInput.value.trim(),
        stateFlow = _groqConnection,
        save = { c.settings.saveGroqApiKey(it) },
        ping = { k -> c.groq.testConnection(k).size }
    )

    private fun runConnectionCheck(
        key: String,
        stateFlow: MutableStateFlow<ConnectionState>,
        save: suspend (String) -> Unit,
        ping: suspend (String) -> Int
    ) {
        if (key.isEmpty()) {
            stateFlow.value = ConnectionState.Failed("Enter an API key first.")
            return
        }
        viewModelScope.launch {
            stateFlow.value = ConnectionState.Testing
            try {
                val count = ping(key)
                save(key)
                stateFlow.value = ConnectionState.Connected(count)
            } catch (e: GeminiException) {
                stateFlow.value = ConnectionState.Failed(e.message ?: "Connection failed")
            } catch (e: Exception) {
                stateFlow.value = ConnectionState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { c.settings.setThemeMode(mode) } }
    fun setAiPreference(pref: AiPreference) { viewModelScope.launch { c.settings.setAiPreference(pref) } }
    fun setModel(m: String) { viewModelScope.launch { c.settings.setModel(m) } }
    fun setAutoShow(v: Boolean) { viewModelScope.launch { c.settings.setAutoShowResults(v) } }
    fun setDefaultQuality(q: Int) { viewModelScope.launch { c.settings.setDefaultQuality(q) } }
    fun setDefaultPdfPageMode(m: PdfPageMode) { viewModelScope.launch { c.settings.setDefaultPdfPageMode(m) } }
    fun setAutoStripExif(v: Boolean) { viewModelScope.launch { c.settings.setAutoStripExif(v) } }

    fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheBytes.value = FileUtils.cacheSizeBytes(getApplication())
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            val freed = FileUtils.clearAllCaches(getApplication())
            _cacheBytes.value = FileUtils.cacheSizeBytes(getApplication())
            _status.value = "Cleared ${FileUtils.formatBytes(freed)} of temporary cache."
        }
    }

    fun forget(file: SavedFile, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (deleteFile) runCatching { getApplication<Application>().contentResolver.delete(file.uri, null, null) }
            c.history.remove(file)
        }
    }

    fun clearHistory() { viewModelScope.launch { c.history.clear() } }
}
