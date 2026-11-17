package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.ai.AiEngine
import com.mediaforge.studio.ai.AiRouter
import com.mediaforge.studio.ai.CopyLanguage
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.ProductCopy
import com.mediaforge.studio.core.BitmapUtils
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.data.db.AiHistoryDao
import com.mediaforge.studio.data.db.AiHistoryEntity
import com.mediaforge.studio.data.db.AiHistoryKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class CopywriterViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val aiRouter: AiRouter,
    private val aiHistory: AiHistoryDao
) : ToolViewModel(app, coordinator) {
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
        if (!connectivity.online.value) {
            showError("Offline Mode active – connect to the internet to use AI Studio.")
            return
        }
        streamJob?.cancel()
        _result.value = null
        _streamingText.value = ""
        _isStreaming.value = true
        _liveStatus.value = "Menyiapkan mesin AI..."

        streamJob = viewModelScope.launch {
            val groqKey = settings.groqApiKey.value
            val geminiKey = settings.apiKey.value
            val geminiModel = settings.model.first()
            val pref = settings.aiPreference.first()
            val lang = _language.value
            val sellerNotes = _notes.value
            try {
                val outcome = aiRouter.streamProductCopy(
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
                        aiHistory.insert(
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
