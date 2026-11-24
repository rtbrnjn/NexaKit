package com.mediaforge.studio.core

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/** Open / Share / folder shortcuts for finished outputs. */
object Actions {

    private fun shareableUri(ctx: Context, uri: Uri): Uri {
        if (uri.scheme.equals("file", ignoreCase = true)) {
            val path = uri.path ?: return uri
            return runCatching {
                FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", File(path))
            }.getOrDefault(uri)
        }
        return uri
    }

    fun open(ctx: Context, file: SavedFile) {
        val safeUri = shareableUri(ctx, file.uri)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(safeUri, file.mime)
            clipData = ClipData.newRawUri(file.name, safeUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No app found to open ${file.mime}", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(ctx: Context, files: List<SavedFile>) {
        if (files.isEmpty()) return
        val safeUris = files.map { shareableUri(ctx, it.uri) }
        val mimes = files.map { it.mime }.distinct()
        val type = when {
            mimes.size == 1 -> mimes.first()
            mimes.all { it.startsWith("image/") } -> "image/*"
            else -> "*/*"
        }
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).apply { putExtra(Intent.EXTRA_STREAM, safeUris.first()) }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList<Uri>(safeUris))
            }
        }
        intent.apply {
            this.type = type
            val clip = ClipData.newRawUri(files.first().name, safeUris.first())
            safeUris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
            clipData = clip
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share via").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(chooser)
    }

    /** Best-effort: asks the system file manager to show `relativePath` (e.g. `Pictures/MediaForge`). */
    fun openFolder(ctx: Context, relativePath: String) {
        val docId = "primary:" + relativePath
        val uri = DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", docId)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "vnd.android.document/directory")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(ctx, "Open your Files app → $relativePath", Toast.LENGTH_LONG).show()
        }
    }

    /** Opens an external URL (e.g. Groq Console or Google AI Studio) in the default browser. */
    fun openUrl(ctx: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(ctx, "No browser found to open $url", Toast.LENGTH_SHORT).show()
        }
    }
}
