package com.mediaforge.studio.pdf

import android.graphics.Bitmap
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val text: String,
    val pagesProcessed: Int,
    val totalRequested: Int,
    val wasTruncated: Boolean
)

/**
 * Optimized on-device OCR engine backed by ML Kit Text Recognition (Latin).
 * Reuses the recognizer client and a single mutable page bitmap buffer across pages,
 * caps tall scanned pages to prevent memory spikes, and halts immediately at `maxChars`.
 */
@Singleton
class LocalOcrEngine @Inject constructor() {
    private val mutex = Mutex()
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    suspend fun ocrPages(
        file: File,
        pageIndices: List<Int>,
        maxChars: Int = 30_000,
        onPageProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): OcrResult = withContext(Dispatchers.Default) {
        if (pageIndices.isEmpty() || maxChars <= 0) return@withContext OcrResult("", 0, pageIndices.size, false)
        mutex.withLock {
            val sb = StringBuilder(minOf(maxChars, 16_384))
            var processed = 0
            var truncated = false
            var scratchBitmap: Bitmap? = null
            try {
                PdfPageRenderer(file).use { renderer ->
                    val validPages = pageIndices.filter { it in 0 until renderer.pageCount }
                    for ((idx, pageIdx) in validPages.withIndex()) {
                        coroutineContext.ensureActive()
                        onPageProgress(idx + 1, validPages.size)
                        val bmp = renderer.renderToWidthAdaptive(
                            index = pageIdx,
                            widthPx = TARGET_WIDTH_PX,
                            maxHeightPx = MAX_HEIGHT_PX,
                            reuse = scratchBitmap
                        )
                        scratchBitmap = bmp
                        coroutineContext.ensureActive()
                        val recognized = recognizer.process(InputImage.fromBitmap(bmp, 0)).awaitResult().text.trim()
                        processed++
                        if (recognized.isNotEmpty()) {
                            if (validPages.size > 1) {
                                sb.append("--- Page ").append(pageIdx + 1).append(" (OCR) ---\n")
                            }
                            sb.append(recognized).append("\n\n")
                        }
                        if (sb.length >= maxChars) {
                            truncated = true
                            break
                        }
                    }
                }
            } finally {
                scratchBitmap?.let { if (!it.isRecycled) it.recycle() }
            }
            val raw = sb.toString().trim()
            val finalText = truncateIfNeeded(raw, maxChars)
            if (finalText.length != raw.length) truncated = true
            OcrResult(finalText, processed, pageIndices.size, truncated)
        }
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { e ->
            if (cont.isActive) cont.resumeWithException(e)
        }
        addOnCanceledListener {
            cont.cancel()
        }
    }

    companion object {
        internal const val TARGET_WIDTH_PX = 1120
        internal const val MAX_HEIGHT_PX = 2000

        internal fun truncateIfNeeded(raw: String, maxChars: Int): String {
            if (raw.length <= maxChars) return raw
            return raw.take(maxChars) + "\n\n[…OCR truncated at $maxChars chars]"
        }
    }
}
