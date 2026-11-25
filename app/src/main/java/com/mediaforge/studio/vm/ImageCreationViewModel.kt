package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.lifecycle.viewModelScope
import com.mediaforge.studio.AppCoordinator
import com.mediaforge.studio.ai.GeminiClient
import com.mediaforge.studio.ai.GeminiException
import com.mediaforge.studio.ai.GeneratedImageResult
import com.mediaforge.studio.ai.ImageAspectRatio
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.Connectivity
import com.mediaforge.studio.data.SettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Gemini / Imagen AI Image Creation ViewModel with gallery export (`Pictures/DocPix`) and quick share.
 */
@HiltViewModel
class ImageCreationViewModel @Inject constructor(
    app: Application,
    coordinator: AppCoordinator,
    private val settings: SettingsStore,
    private val connectivity: Connectivity,
    private val gemini: GeminiClient
) : ToolViewModel(app, coordinator) {

    private val _prompt = MutableStateFlow("")
    val prompt: StateFlow<String> = _prompt

    private val _negativePrompt = MutableStateFlow("")
    val negativePrompt: StateFlow<String> = _negativePrompt

    private val _aspectRatio = MutableStateFlow(ImageAspectRatio.SQUARE)
    val aspectRatio: StateFlow<ImageAspectRatio> = _aspectRatio

    private val _generatedBitmap = MutableStateFlow<Bitmap?>(null)
    val generatedBitmap: StateFlow<Bitmap?> = _generatedBitmap

    private val _generatedResult = MutableStateFlow<GeneratedImageResult?>(null)
    val generatedResult: StateFlow<GeneratedImageResult?> = _generatedResult

    private val _savedFile = MutableStateFlow<SavedFile?>(null)
    val savedFile: StateFlow<SavedFile?> = _savedFile

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _liveStatus = MutableStateFlow<String?>(null)
    val liveStatus: StateFlow<String?> = _liveStatus

    private var genJob: Job? = null

    fun setPrompt(text: String) {
        _prompt.value = text
    }

    fun setNegativePrompt(text: String) {
        _negativePrompt.value = text
    }

    fun setAspectRatio(ratio: ImageAspectRatio) {
        _aspectRatio.value = ratio
    }

    fun applyFromPromptGenerator(mainPrompt: String, negativePrompt: String, ratio: ImageAspectRatio) {
        _prompt.value = mainPrompt
        _negativePrompt.value = negativePrompt
        _aspectRatio.value = ratio
        dismissMessage()
        showInfo("Prompt dari Prompt Generator siap digenerate (${ratio.label}).")
    }

    fun stop() {
        genJob?.cancel()
        _isGenerating.value = false
        _liveStatus.value = null
    }

    fun generate() {
        val text = _prompt.value.trim()
        if (text.isEmpty()) {
            showError("Masukkan deskripsi / prompt gambar terlebih dahulu.")
            return
        }
        if (!connectivity.online.value) {
            showError("Mode Offline aktif — sambungkan internet untuk membuat gambar AI.")
            return
        }
        val geminiKey = settings.apiKey.value.trim()
        if (geminiKey.isEmpty()) {
            showError("Tambahkan Google Gemini API Key (AIza...) di Settings untuk fitur Image Creation.")
            return
        }

        dismissMessage()
        genJob?.cancel()
        _isGenerating.value = true
        _savedFile.value = null
        _liveStatus.value = "Menghubungkan ke Gemini Image Engine (${_aspectRatio.value.label})..."

        val ratio = _aspectRatio.value
        val neg = _negativePrompt.value.trim()
        genJob = viewModelScope.launch {
            try {
                val generated = gemini.generateImage(
                    apiKey = geminiKey,
                    prompt = text,
                    aspectRatio = ratio,
                    negativePrompt = neg
                )
                val bmp = withContext(Dispatchers.Default) {
                    BitmapFactory.decodeByteArray(generated.bytes, 0, generated.bytes.size)
                }
                if (bmp == null) {
                    showError("Gambar diterima tetapi gagal didekode. Silakan coba generate ulang.")
                } else {
                    _generatedResult.value = generated
                    _generatedBitmap.value = bmp
                    showInfo("Gambar berhasil dibuat via ${generated.modelId} (${bmp.width}×${bmp.height}).")
                }
            } catch (_: CancellationException) {
                // Cancelled by user
            } catch (e: GeminiException) {
                showError(e.message ?: "Gagal membuat gambar AI.")
            } catch (e: Exception) {
                showError(e.message ?: e.javaClass.simpleName)
            } finally {
                _isGenerating.value = false
                _liveStatus.value = null
            }
        }
    }

    fun saveToGallery() {
        val res = _generatedResult.value ?: return
        runTask("Menyimpan gambar ke Pictures/DocPix…", write = true) {
            val saved = ensureSaved(res)
            publish(listOf(saved))
            showInfo("Tersimpan di ${saved.folder}/${saved.name}")
        }
    }

    fun shareImage(onReady: (SavedFile) -> Unit) {
        val res = _generatedResult.value ?: return
        val existing = _savedFile.value
        if (existing != null) {
            onReady(existing)
            return
        }
        runTask("Menyiapkan gambar untuk dibagikan…", write = true) {
            val saved = ensureSaved(res)
            withContext(Dispatchers.Main) {
                onReady(saved)
            }
        }
    }

    private fun ensureSaved(res: GeneratedImageResult): SavedFile {
        _savedFile.value?.let { return it }
        val name = "DocPix_AI_${FileUtils.timestamp()}.${res.fileExtension}"
        val saved = Storage.saveBytes(
            ctx = ctx,
            kind = OutputKind.IMAGE,
            subFolder = null,
            displayName = name,
            mime = res.mimeType,
            tool = "AI Image Creation",
            bytes = res.bytes
        )
        _savedFile.value = saved
        return saved
    }
}
