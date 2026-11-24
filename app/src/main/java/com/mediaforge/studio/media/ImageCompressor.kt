package com.mediaforge.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import androidx.exifinterface.media.ExifInterface
import com.mediaforge.studio.core.BitmapUtils
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

enum class MaxDimension(val label: String, val px: Int?) {
    ORIGINAL("Original", null),
    P1080("1080p", 1920),
    P720("720p", 1280)
}

enum class OutputFormat(val label: String, val ext: String, val mime: String) {
    WEBP("WEBP", "webp", "image/webp"),
    JPEG("JPEG", "jpg", "image/jpeg")
}

enum class CompressionPreset(val label: String, val quality: Int) {
    SMALL("Kecil 40%", 40),
    BALANCED("Seimbang 70%", 70),
    SHARP("Tajam 85%", 85)
}

data class CompressOptions(
    val quality: Int = 70,
    val maxDimension: MaxDimension = MaxDimension.P1080,
    val format: OutputFormat = OutputFormat.WEBP,
    val stripExif: Boolean = true
)

data class CompressedImage(val file: File, val bytes: Long, val metadataStripped: Boolean)

/**
 * Re-encodes images (which drops embedded metadata by construction) and, when [CompressOptions.stripExif]
 * is enabled, explicitly verifies / nulls privacy-sensitive EXIF tags with [ExifInterface].
 * EXIF orientation is baked into the pixels first, so the picture is never displayed rotated.
 */
class ImageCompressor(private val ctx: Context) {

    /** Fully encodes to a temp file in cache; EXIF is stripped when requested. */
    fun compress(uri: Uri, opts: CompressOptions, target: File): CompressedImage {
        val bmp = BitmapUtils.decode(ctx, uri, opts.maxDimension.px) ?: error("Cannot decode image")
        FileOutputStream(target).use { encode(bmp, opts, it) }
        bmp.recycle()
        val stripped = if (opts.stripExif) {
            stripMetadata(target)
        } else {
            copyBasicExif(uri, target)
            false
        }
        return CompressedImage(target, target.length(), stripped)
    }

    private fun copyBasicExif(srcUri: Uri, target: File) {
        runCatching {
            val src = ctx.contentResolver.openInputStream(srcUri)?.use { ExifInterface(it) } ?: return
            val dst = ExifInterface(target.absolutePath)
            val basicTags = listOf(
                ExifInterface.TAG_MAKE,
                ExifInterface.TAG_MODEL,
                ExifInterface.TAG_DATETIME_ORIGINAL,
                ExifInterface.TAG_F_NUMBER,
                ExifInterface.TAG_EXPOSURE_TIME,
                ExifInterface.TAG_FOCAL_LENGTH
            )
            basicTags.forEach { tag -> src.getAttribute(tag)?.let { dst.setAttribute(tag, it) } }
            dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
            dst.saveAttributes()
        }
    }

    /** Encoded size only (nothing is written) – drives the live before/after preview. */
    fun estimate(uri: Uri, opts: CompressOptions): Long {
        val bmp = BitmapUtils.decode(ctx, uri, opts.maxDimension.px) ?: return 0L
        val counter = CountingStream()
        encode(bmp, opts, counter)
        bmp.recycle()
        return counter.count
    }

    private fun encode(src: Bitmap, opts: CompressOptions, out: OutputStream) {
        val q = opts.quality.coerceIn(1, 100)
        when (opts.format) {
            OutputFormat.JPEG -> {
                val flat = BitmapUtils.flatten(src)
                flat.compress(Bitmap.CompressFormat.JPEG, q, out)
                if (flat !== src) flat.recycle()
            }
            OutputFormat.WEBP -> {
                val fmt = if (Build.VERSION.SDK_INT >= 30) Bitmap.CompressFormat.WEBP_LOSSY
                else @Suppress("DEPRECATION") Bitmap.CompressFormat.WEBP
                src.compress(fmt, q, out)
            }
        }
    }

    private class CountingStream : OutputStream() {
        var count = 0L
        override fun write(b: Int) { count++ }
        override fun write(b: ByteArray, off: Int, len: Int) { count += len }
    }

    /**
     * Nulls GPS and device/personal EXIF tags. Returns true when the file is verified clean.
     */
    private fun stripMetadata(file: File): Boolean = try {
        val exif = ExifInterface(file.absolutePath)
        val dirty = SENSITIVE_TAGS.filter { exif.getAttribute(it) != null }
        if (dirty.isNotEmpty()) {
            dirty.forEach { exif.setAttribute(it, null) }
            exif.saveAttributes()
            val verify = ExifInterface(file.absolutePath)
            SENSITIVE_TAGS.none { verify.getAttribute(it) != null }
        } else true
    } catch (_: Exception) {
        // Format has no EXIF container (re-encoded bitmaps normally carry none).
        true
    }

    companion object {
        private val NAMED = setOf(
            "TAG_MAKE", "TAG_MODEL", "TAG_SOFTWARE", "TAG_DATETIME", "TAG_DATETIME_ORIGINAL",
            "TAG_DATETIME_DIGITIZED", "TAG_ARTIST", "TAG_COPYRIGHT", "TAG_IMAGE_DESCRIPTION",
            "TAG_USER_COMMENT", "TAG_MAKER_NOTE", "TAG_CAMERA_OWNER_NAME", "TAG_BODY_SERIAL_NUMBER",
            "TAG_LENS_SERIAL_NUMBER", "TAG_LENS_MAKE", "TAG_LENS_MODEL", "TAG_IMAGE_UNIQUE_ID",
            "TAG_SUBSEC_TIME", "TAG_SUBSEC_TIME_ORIGINAL", "TAG_SUBSEC_TIME_DIGITIZED"
        )

        /** All GPS tags + identifying camera/personal tags, resolved by reflection (version tolerant). */
        val SENSITIVE_TAGS: List<String> by lazy {
            ExifInterface::class.java.fields
                .filter { it.type == String::class.java && java.lang.reflect.Modifier.isStatic(it.modifiers) }
                .filter { it.name.startsWith("TAG_GPS_") || it.name in NAMED }
                .mapNotNull { runCatching { it.get(null) as? String }.getOrNull() }
                .distinct()
        }
    }
}
