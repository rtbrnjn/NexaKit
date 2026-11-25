package com.mediaforge.studio.core

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class OutputKind { IMAGE, DOCUMENT }

/** A file that DocPix has written to shared storage. */
data class SavedFile(
    val uri: Uri,
    val name: String,
    val mime: String,
    val sizeBytes: Long,
    /** Human readable folder, e.g. `Pictures/DocPix/PDF_Export`. */
    val folder: String,
    val tool: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val isImage get() = mime.startsWith("image/")
    val isPdf get() = mime == "application/pdf"
    val isZip get() = mime == "application/zip" || name.endsWith(".zip", ignoreCase = true)
}

/**
 * Saves outputs through MediaStore (scoped storage on API 29+) into
 * `Pictures/DocPix[/sub]` and `Documents/DocPix[/sub]`.
 * Legacy devices (API 26-28) fall back to direct file writes + media scan.
 */
object Storage {
    const val ROOT = "DocPix"

    fun folderPath(kind: OutputKind, subFolder: String? = null): String {
        val base = if (kind == OutputKind.IMAGE) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOCUMENTS
        return listOfNotNull(base, ROOT, subFolder).joinToString("/")
    }

    /** Blocking – call from an IO dispatcher. */
    fun save(
        ctx: Context,
        kind: OutputKind,
        subFolder: String?,
        displayName: String,
        mime: String,
        tool: String,
        write: (OutputStream) -> Unit
    ): SavedFile {
        val rel = folderPath(kind, subFolder)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveScoped(ctx, kind, rel, displayName, mime, tool, write)
        } else {
            saveLegacy(ctx, rel, displayName, mime, tool, write)
        }
    }

    fun saveBytes(
        ctx: Context, kind: OutputKind, subFolder: String?, displayName: String,
        mime: String, tool: String, bytes: ByteArray
    ): SavedFile = save(ctx, kind, subFolder, displayName, mime, tool) { it.write(bytes) }

    fun saveFile(
        ctx: Context, kind: OutputKind, subFolder: String?, displayName: String,
        mime: String, tool: String, source: File
    ): SavedFile = save(ctx, kind, subFolder, displayName, mime, tool) { out ->
        source.inputStream().use { it.copyTo(out, 64 * 1024) }
    }

    private fun saveScoped(
        ctx: Context, kind: OutputKind, rel: String, name: String, mime: String, tool: String,
        write: (OutputStream) -> Unit
    ): SavedFile {
        val resolver = ctx.contentResolver
        val collection = if (kind == OutputKind.IMAGE) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, rel)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(collection, values) ?: error("MediaStore refused to create $name")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                write(out)
                out.flush()
            } ?: error("Cannot open output stream for $name")
            val done = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        } catch (t: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw t
        }
        var finalName = name
        var size = 0L
        resolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                finalName = c.getString(0) ?: name
                size = c.getLong(1)
            }
        }
        return SavedFile(uri, finalName, mime, size, rel, tool)
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(
        ctx: Context, rel: String, name: String, mime: String, tool: String,
        write: (OutputStream) -> Unit
    ): SavedFile {
        val publicRoot = Environment.getExternalStorageDirectory()
        val dir = File(publicRoot, rel)
        if (!dir.exists() && !dir.mkdirs()) error("Cannot create folder $rel (storage permission granted?)")
        var target = File(dir, name)
        var n = 1
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        while (target.exists()) {
            target = File(dir, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            n++
        }
        FileOutputStream(target).use { out ->
            write(out)
            out.flush()
        }
        MediaScannerConnection.scanFile(ctx, arrayOf(target.absolutePath), arrayOf(mime), null)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", target)
        return SavedFile(uri, target.name, mime, target.length(), rel, tool)
    }
}
