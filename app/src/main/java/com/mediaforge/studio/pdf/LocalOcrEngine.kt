package com.mediaforge.studio.pdf

import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrResult(
    val text: String,
    val pagesProcessed: Int,
    val totalRequested: Int,
    val wasTruncated: Boolean
)

/**
 * On-device OCR engine backed by ML Kit Text Recognition (Latin).
 * Used automatically for Scanned PDFs so raw page images don't have to travel over cellular data.
 */
object LocalOcrEngine {

    suspend fun ocrPages(
        file: File,
        pageIndices: List<Int>,
        maxChars: Int = 30_000,
        onPageProgress: (current: Int, total: Int) -> Unit = { _, _ -> }
    ): OcrResult = withContext(Dispatchers.Default) {
        if (pageIndices.isEmpty()) return@withContext OcrResult("", 0, 0, false)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val sb = StringBuilder()
        var processed = 0
        var truncated = false
        try {
            PdfPageRenderer(file).use { renderer ->
                val validPages = pageIndices.filter { it in 0 until renderer.pageCount }
                validPages.forEachIndexed { idx, pageIdx ->
                    onPageProgress(idx + 1, validPages.size)
                    val bmp = renderer.renderToWidth(pageIdx, 1400)
                    val recognized = try {
                        val image = InputImage.fromBitmap(bmp, 0)
                        recognizer.process(image).awaitResult().text.trim()
                    } finally {
                        bmp.recycle()
                    }
                    processed++
                    if (recognized.isNotEmpty()) {
                        if (validPages.size > 1) {
                            sb.append("--- Page ").append(pageIdx + 1).append(" (OCR) ---\n")
                        }
                        sb.append(recognized).append("\n\n")
                    }
                    if (sb.length >= maxChars) {
                        truncated = true
                        return@forEachIndexed
                    }
                }
            }
        } finally {
            runCatching { recognizer.close() }
        }
        val raw = sb.toString().trim()
        val finalText = if (raw.length > maxChars) {
            truncated = true
            raw.take(maxChars) + "\n\n[…OCR truncated at $maxChars chars]"
        } else raw
        OcrResult(finalText, processed, pageIndices.size, truncated)
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
}
