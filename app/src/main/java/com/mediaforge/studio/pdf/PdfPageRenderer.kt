package com.mediaforge.studio.pdf

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import kotlin.math.roundToInt

/**
 * Thread-safe wrapper over [PdfRenderer] (which only allows one open page at a time).
 * Throws [SecurityException] for password protected files and [java.io.IOException] for corrupt ones.
 */
class PdfPageRenderer(file: File) : Closeable {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer: PdfRenderer = try {
        PdfRenderer(pfd)
    } catch (t: Throwable) {
        pfd.close()
        throw t
    }
    private var closed = false

    val pageCount: Int = renderer.pageCount

    /** Page size in PDF points (1/72"). */
    fun pageSize(index: Int): Pair<Int, Int> = synchronized(this) {
        renderer.openPage(index).use { it.width to it.height }
    }

    /** Renders `index` so its width is `widthPx` (height follows the page aspect ratio). */
    fun renderToWidth(index: Int, widthPx: Int): Bitmap =
        renderToWidthAdaptive(index, widthPx, maxHeightPx = Int.MAX_VALUE, reuse = null)

    /**
     * Renders `index` at up to `widthPx` width and `maxHeightPx` height.
     * When [reuse] matches the computed dimensions and is mutable ARGB_8888, it is cleared and
     * rewritten in-place to eliminate per-page bitmap allocation churn during batch OCR.
     */
    fun renderToWidthAdaptive(
        index: Int,
        widthPx: Int,
        maxHeightPx: Int = 2200,
        reuse: Bitmap? = null
    ): Bitmap = synchronized(this) {
        check(!closed) { "Renderer closed" }
        renderer.openPage(index).use { page ->
            val safePageW = page.width.coerceAtLeast(1)
            val safePageH = page.height.coerceAtLeast(1)
            val targetW = widthPx.coerceAtLeast(16)
            val rawH = (targetW.toLong() * safePageH / safePageW).toInt().coerceAtLeast(16)
            val (w, h) = if (rawH > maxHeightPx) {
                val clampedW = (maxHeightPx.toLong() * safePageW / safePageH).toInt().coerceAtLeast(16)
                clampedW to maxHeightPx
            } else {
                targetW to rawH
            }
            val bmp = obtainBitmap(w, h, reuse)
            drawPage(page, bmp)
            bmp
        }
    }

    /** Renders at `dpi`, never exceeding `maxSide` pixels on the longest edge. */
    fun renderAtDpi(index: Int, dpi: Int, maxSide: Int = 4500): Bitmap = synchronized(this) {
        check(!closed) { "Renderer closed" }
        renderer.openPage(index).use { page ->
            var scale = dpi / 72f
            val longest = maxOf(page.width, page.height) * scale
            if (longest > maxSide) scale *= maxSide / longest
            val w = (page.width * scale).roundToInt().coerceAtLeast(16)
            val h = (page.height * scale).roundToInt().coerceAtLeast(16)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            drawPage(page, bmp)
            bmp
        }
    }

    suspend fun thumbnail(index: Int, widthPx: Int): Bitmap =
        withContext(Dispatchers.Default) { renderToWidth(index, widthPx) }

    private fun obtainBitmap(w: Int, h: Int, reuse: Bitmap?): Bitmap {
        if (reuse != null && !reuse.isRecycled && reuse.isMutable &&
            reuse.width == w && reuse.height == h && reuse.config == Bitmap.Config.ARGB_8888
        ) {
            return reuse
        }
        reuse?.let { if (!it.isRecycled) it.recycle() }
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    }

    private fun drawPage(page: PdfRenderer.Page, bmp: Bitmap) {
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
    }

    override fun close() = synchronized(this) {
        if (!closed) {
            closed = true
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}
