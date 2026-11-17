package com.mediaforge.studio

import android.content.Context
import android.net.Uri
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.core.SavedFile
import com.mediaforge.studio.data.HistoryStore
import com.mediaforge.studio.data.SettingsStore
import com.mediaforge.studio.di.AppScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class IncomingShare(
    val images: List<Uri> = emptyList(),
    val pdfs: List<Uri> = emptyList()
) {
    val isEmpty: Boolean get() = images.isEmpty() && pdfs.isEmpty()
}

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

    fun publish(files: List<SavedFile>) {
        if (files.isEmpty()) return
        scope.launch {
            history.add(files)
            if (settings.autoShowResults.first()) _results.value = files
        }
    }

    fun dismissResults() { _results.value = null }

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
