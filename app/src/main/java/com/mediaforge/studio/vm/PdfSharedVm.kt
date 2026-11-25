package com.mediaforge.studio.vm

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import com.mediaforge.studio.core.FileUtils
import com.mediaforge.studio.pdf.PdfEngine
import com.mediaforge.studio.pdf.PdfException
import com.mediaforge.studio.pdf.PdfPageRenderer
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import java.io.Closeable
import java.io.File
import java.io.IOException

/** A PDF copied into app cache, validated and ready to process. */
data class PdfSource(val file: File, val name: String, val sizeBytes: Long, val pageCount: Int)

data class LockedPdfSource(val file: File, val name: String, val sizeBytes: Long, val isEncrypted: Boolean)

object PdfInputs {
    /** Blocking; run on IO. Copies the Uri to cache and checks that PdfRenderer can open it. */
    fun load(app: Application, uri: Uri): PdfSource {
        runCatching { PDFBoxResourceLoader.init(app.applicationContext) }
        val name = FileUtils.queryName(app, uri)
        val file = FileUtils.copyToCache(app, uri, "pdf_in", name)
        val pages = try {
            PdfPageRenderer(file).use { it.pageCount }
        } catch (e: SecurityException) {
            file.delete()
            throw PdfException("\"$name\" is password protected. Use Unlock PDF first.", e)
        } catch (e: IOException) {
            file.delete()
            throw PdfException("\"$name\" is not a valid or readable PDF.", e)
        }
        if (pages <= 0) throw PdfException("\"$name\" has no pages.")
        return PdfSource(file, name, file.length(), pages)
    }

    /** Blocking; copies Uri to cache and inspects encryption status without failing on locked PDFs. */
    fun loadForUnlock(app: Application, uri: Uri): LockedPdfSource {
        runCatching { PDFBoxResourceLoader.init(app.applicationContext) }
        val name = FileUtils.queryName(app, uri)
        val file = FileUtils.copyToCache(app, uri, "pdf_unlock", name)
        val encrypted = try {
            PdfEngine.isEncrypted(file)
        } catch (e: PdfException) {
            file.delete()
            throw e
        }
        return LockedPdfSource(file, name, file.length(), encrypted)
    }
}

/** Lazy, cached page thumbnails for the page pickers (Split / Rotate & Delete). */
class PdfThumbs(file: File) : Closeable {
    private val renderer = PdfPageRenderer(file)
    private val cache = object : LruCache<Int, Bitmap>(64) {}

    suspend fun get(index: Int, widthPx: Int = 320): Bitmap? {
        cache.get(index)?.let { return it }
        val bmp = runCatching { renderer.thumbnail(index, widthPx) }.getOrNull()
        if (bmp != null) cache.put(index, bmp)
        return bmp
    }

    override fun close() { renderer.close() }
}
