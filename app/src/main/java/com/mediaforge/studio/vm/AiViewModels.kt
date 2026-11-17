package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfPageMode
import com.mediaforge.studio.pdf.PdfPageRenderer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ============================================================ Copywriter

class CopywriterViewModel(app: Application) : ToolViewModel(app) {
    private val _uri = MutableStateFlow<Uri?>(null)
    val uri: StateFlow<Uri?> = _uri
    private val _preview = MutableStateFlow<Bitmap?>(null)
    val preview: StateFlow<Bitmap?> = _preview
    private val _language = MutableStateFlow(CopyLanguage.ENGLISH)
    val language: StateFlow<CopyLanguage> = _language
    private val _notes = MutableStateFlow("")
    val notes: StateFlow<String> = _notes
    private val _result = MutableStateFlow<ProductCopy?>(null)
    val result: StateFlow<ProductCopy?> = _result

    fun pick(uri: Uri) {
        _uri.value = uri
        _result.value = null
        viewModelScope.launch(Dispatchers.IO) {
            _preview.value = BitmapUtils.decode(ctx, uri, 1024)
            if (_preview.value == null) showError("Could not open that image.")
        }
    }
    fun setLanguage(l: CopyLanguage) { _language.value = l }
    fun setNotes(n: String) { _notes.value = n.take(300) }

    fun generate() {
        val bmp = _preview.value ?: run { showError("Pick a product photo first."); return }
        runTask("Gemini is writing your copy…") {
            val key = container.settings.apiKey.value
            val model = container.settings.model.first()
            val jpeg = BitmapUtils.jpegBytes(bmp, 85)
            _result.value = container.gemini.generateProductCopy(key, model, jpeg, _language.value, _notes.value)
        }
    }
}

// ============================================================ PDF summariser & chat

data class ChatMessage(val fromUser: Boolean, val text: String, val label: String? = null)

class PdfAiViewModel(app: Application) : ToolViewModel(app) {
    private val _source = MutableStateFlow<PdfSource?>(null)
    val source: StateFlow<PdfSource?> = _source
    private val _extractInfo = MutableStateFlow("")
    val extractInfo: StateFlow<String> = _extractInfo
    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private var docText: String? = null
    private var pageImages: List<ByteArray> = emptyList()
    private val history = ArrayList<GeminiContent>()

    fun pick(uri: Uri) {
        runTask("Extracting text locally…") {
            val src = PdfInputs.load(getApplication(), uri)
            val text = PdfEngine.extractText(src.file)
            history.clear()
            _messages.value = emptyList()
            if (text.isBlank()) {
                // Scanned PDF: send the first pages as images instead.
                setProgress("Scanned PDF – rendering pages…")
                val images = ArrayList<ByteArray>()
                PdfPageRenderer(src.file).use { r ->
                    for (i in 0 until minOf(r.pageCount, 5)) {
                        val bmp = r.renderToWidth(i, 1100)
                        images += BitmapUtils.jpegBytes(bmp, 80)
                        bmp.recycle()
                    }
                }
                docText = null
                pageImages = images
                _extractInfo.value = "No selectable text found – Gemini will read the first ${images.size} page image(s)."
            } else {
                docText = text
                pageImages = emptyList()
                _extractInfo.value = "${text.length} characters extracted on-device from ${src.pageCount} page(s)."
            }
            _source.value = src
        }
    }

    fun runDocTask(task: DocTask) = send(task.prompt, task.label)
    fun ask(question: String) { if (question.isNotBlank()) send(question.trim(), null) }

    fun clearChat() { history.clear(); _messages.value = emptyList() }

    private fun send(prompt: String, label: String?) {
        if (_source.value == null) { showError("Open a PDF first."); return }
        if (!container.connectivity.online.value) {
            showError("You are offline – AI features need an internet connection."); return
        }
        val display = label ?: prompt
        runTask("Gemini is reading…") {
            val key = container.settings.apiKey.value
            val model = container.settings.model.first()
            val turn = GeminiContent.user(prompt)
            val snapshot = history + turn
            _messages.update { it + ChatMessage(true, display, label) }
            try {
                val reply = container.gemini.chatAboutDocument(key, model, docText, pageImages, snapshot.takeLast(16))
                history += turn
                history += GeminiContent.model(reply)
                _messages.update { it + ChatMessage(false, reply) }
            } catch (e: Exception) {
                _messages.update { it.dropLast(1) }
                throw e
            }
        }
    }
}

// ============================================================ Settings / Vault

sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState
    data class Connected(val modelCount: Int) : ConnectionState
    data class Failed(val message: String) : ConnectionState
}

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.container

    val keyInput = MutableStateFlow("")
    private var edited = false

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status

    val savedKey: StateFlow<String> = c.settings.apiKey
    val themeMode: StateFlow<ThemeMode> = c.settings.themeMode.stateIn(viewModelScope, SharingStarted.Eagerly, ThemeMode.SYSTEM)
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
            c.settings.apiKey.collect { if (!edited) keyInput.value = it }
        }
        refreshCacheSize()
    }

    fun onKeyChanged(v: String) { edited = true; keyInput.value = v; _connection.value = ConnectionState.Idle; _status.value = null }

    fun saveKey() {
        viewModelScope.launch {
            c.settings.saveApiKey(keyInput.value)
            _status.value = if (keyInput.value.isBlank()) "API key removed." else "API key saved securely on this device."
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            c.settings.saveApiKey("")
            edited = false
            keyInput.value = ""
            _connection.value = ConnectionState.Idle
            _status.value = "API key removed."
        }
    }

    fun testConnection() {
        val key = keyInput.value.trim()
        if (key.isEmpty()) { _connection.value = ConnectionState.Failed("Enter an API key first."); return }
        viewModelScope.launch {
            _connection.value = ConnectionState.Testing
            try {
                val available = c.gemini.testConnection(key)
                c.settings.saveApiKey(key)
                if (available.isNotEmpty()) {
                    val merged = (DEFAULT_MODELS + available).distinct()
                    c.settings.setKnownModels(merged)
                }
                _connection.value = ConnectionState.Connected(available.size)
            } catch (e: GeminiException) {
                _connection.value = ConnectionState.Failed(e.message ?: "Connection failed")
            } catch (e: Exception) {
                _connection.value = ConnectionState.Failed(e.message ?: "Connection failed")
            }
        }
    }

    fun setThemeMode(mode: ThemeMode) { viewModelScope.launch { c.settings.setThemeMode(mode) } }
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
