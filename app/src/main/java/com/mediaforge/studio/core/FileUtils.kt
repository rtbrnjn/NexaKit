package com.mediaforge.studio.core

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FileUtils {

    fun queryName(ctx: Context, uri: Uri): String {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val v = c.getString(0)
                    if (!v.isNullOrBlank()) return v
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    fun querySize(ctx: Context, uri: Uri): Long {
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) return c.getLong(0)
            }
        }
        return runCatching {
            ctx.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length }
        }.getOrNull()?.takeIf { it >= 0 } ?: 0L
    }

    /** Copies a (possibly non-seekable) content Uri into app cache so renderers/parsers can use a real File. */
    fun copyToCache(ctx: Context, uri: Uri, subDir: String = "inputs", suggestedName: String? = null): File {
        val dir = File(ctx.cacheDir, subDir).apply { mkdirs() }
        val name = sanitize(suggestedName ?: queryName(ctx, uri))
        val out = File(dir, "${System.nanoTime()}_$name")
        ctx.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { input.copyTo(it, 64 * 1024) }
        } ?: error("Cannot open $name")
        return out
    }

    fun clearCache(ctx: Context, subDir: String) {
        File(ctx.cacheDir, subDir).deleteRecursively()
    }

    fun cacheSizeBytes(ctx: Context): Long =
        ctx.cacheDir.walkBottomUp().filter { it.isFile }.sumOf { it.length() }

    fun clearAllCaches(ctx: Context): Long {
        val before = cacheSizeBytes(ctx)
        ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
        return before
    }

    fun baseName(name: String): String = sanitize(name.substringBeforeLast('.', name))

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().ifEmpty { "file" }

    fun timestamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val kb = 1024.0
        val mb = kb * 1024
        val gb = mb * 1024
        val df = DecimalFormat("0.#")
        return when {
            bytes >= gb -> "${df.format(bytes / gb)} GB"
            bytes >= mb -> "${df.format(bytes / mb)} MB"
            bytes >= kb -> "${df.format(bytes / kb)} KB"
            else -> "$bytes B"
        }
    }

    fun formatDate(ts: Long): String =
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()).format(Date(ts))

    fun savingsPercent(before: Long, after: Long): Int =
        if (before <= 0) 0 else (((before - after).toDouble() / before) * 100).toInt()
}
