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
    fun renderToWidth(index: Int, widthPx: Int): Bitmap = synchronized(this) {
        check(!closed) { "Renderer closed" }
        renderer.openPage(index).use { page ->
            val w = widthPx.coerceAtLeast(16)
            val h = (w.toLong() * page.height / page.width).toInt().coerceAtLeast(16)
            drawPage(page, w, h)
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
            drawPage(page, w, h)
        }
    }

    suspend fun thumbnail(index: Int, widthPx: Int): Bitmap =
        withContext(Dispatchers.Default) { renderToWidth(index, widthPx) }

    private fun drawPage(page: PdfRenderer.Page, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(Color.WHITE)
        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
        return bmp
    }

    override fun close() = synchronized(this) {
        if (!closed) {
            closed = true
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
    }
}
