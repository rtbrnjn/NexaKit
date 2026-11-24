package com.mediaforge.studio

import android.content.Context
import android.net.Uri
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.OutputKind
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.core.Storage
import com.mediaforge.studio.data.HistoryStore
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingShare(
    val images: List<Uri> = emptyList(),
    val pdfs: List<Uri> = emptyList()
) {
    val isEmpty: Boolean get() = images.isEmpty() && pdfs.isEmpty()
}

data class PendingPdfSave(
    val id: Long = System.nanoTime(),
    val tempFile: File,
    val defaultStem: String,
    val toolName: String,
    val onSaved: (suspend (SavedFile) -> Unit)? = null,
    val onFailed: ((String) -> Unit)? = null
)

enum class ShareAction {
    COMPRESS_IMAGES,
    MOCKUP_IMAGE,
    COLLAGE_IMAGES,
    IMAGES_TO_PDF,
    AI_COPYWRITER,
    AI_PDF_SUMMARY,
    PDF_TO_WORD,
    PDF_TO_IMAGES,
    MERGE_PDFS,
    SPLIT_PDF,
    COMPRESS_PDF
}

data class PendingShareDispatch(
    val action: ShareAction,
    val images: List<Uri>,
    val pdfs: List<Uri>,
    val id: Long = System.nanoTime()
)

/** Singleton coordinator managing exported result banners and Android Share target routing. */
@Singleton
class AppCoordinator @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val history: HistoryStore,
    private val settings: SettingsStore,
    @param:AppScope private val scope: CoroutineScope
) {
    private val _results = MutableStateFlow<List<SavedFile>?>(null)
    /** Non-null while the "Open / Share" action sheet should be showing. */
    val results: StateFlow<List<SavedFile>?> = _results

    private val _incomingShare = MutableStateFlow<IncomingShare?>(null)
    val incomingShare: StateFlow<IncomingShare?> = _incomingShare

    private val _shareDispatch = MutableStateFlow<PendingShareDispatch?>(null)
    val shareDispatch: StateFlow<PendingShareDispatch?> = _shareDispatch

    private val _pendingPdfSave = MutableStateFlow<PendingPdfSave?>(null)
    val pendingPdfSave: StateFlow<PendingPdfSave?> = _pendingPdfSave

    private val _savingPendingPdf = MutableStateFlow(false)
    val savingPendingPdf: StateFlow<Boolean> = _savingPendingPdf

    private val _completionHapticTick = MutableStateFlow(0L)
    /** Increments whenever an export or PDF processing operation completes for haptic confirmation. */
    val completionHapticTick: StateFlow<Long> = _completionHapticTick

    fun publish(files: List<SavedFile>) {
        if (files.isEmpty()) return
        _completionHapticTick.value += 1L
        scope.launch {
            history.add(files)
            if (settings.autoShowResults.first()) _results.value = files
        }
    }

    fun dismissResults() { _results.value = null }

    fun stagePdfSave(save: PendingPdfSave) {
        _pendingPdfSave.value?.tempFile?.delete()
        _pendingPdfSave.value = save
        _completionHapticTick.value += 1L
    }

    fun cancelPendingPdfSave() {
        val current = _pendingPdfSave.value
        _pendingPdfSave.value = null
        current?.tempFile?.let { file -> scope.launch(Dispatchers.IO) { file.delete() } }
    }

    fun commitPendingPdfSave(rawFileName: String) {
        val pending = _pendingPdfSave.value ?: return
        if (_savingPendingPdf.value) return
        _savingPendingPdf.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val finalName = FileUtils.normalizePdfFilename(rawFileName, pending.defaultStem)
                val saved = Storage.saveFile(
                    ctx = appContext,
                    kind = OutputKind.DOCUMENT,
                    subFolder = null,
                    displayName = finalName,
                    mime = "application/pdf",
                    tool = pending.toolName,
                    source = pending.tempFile
                )
                pending.tempFile.delete()
                _pendingPdfSave.value = null
                pending.onSaved?.invoke(saved)
                publish(listOf(saved))
            } catch (e: Exception) {
                pending.onFailed?.invoke(e.message ?: "Gagal menyimpan file PDF.")
            } finally {
                _savingPendingPdf.value = false
            }
        }
    }

    fun receiveSharedUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val images = ArrayList<Uri>()
        val pdfs = ArrayList<Uri>()
        uris.distinct().forEach { uri ->
            val mime = appContext.contentResolver.getType(uri)?.lowercase().orEmpty()
            val name = FileUtils.queryName(appContext, uri).lowercase()
            when {
                mime == "application/pdf" || name.endsWith(".pdf") -> pdfs += uri
                mime.startsWith("image/") || name.endsWith(".jpg") || name.endsWith(".jpeg") ||
                    name.endsWith(".png") || name.endsWith(".webp") -> images += uri
            }
        }
        val share = IncomingShare(images, pdfs)
        if (!share.isEmpty) _incomingShare.value = share
    }

    fun dismissIncomingShare() { _incomingShare.value = null }

    fun dispatchShareAction(action: ShareAction) {
        val share = _incomingShare.value ?: return
        _incomingShare.value = null
        _shareDispatch.value = PendingShareDispatch(action, share.images, share.pdfs)
    }

    fun consumeShareDispatch() { _shareDispatch.value = null }
}
