package com.mediaforge.studio.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object BitmapUtils {

    /** Reads the EXIF orientation of `uri`; NORMAL if unreadable. */
    fun readOrientation(ctx: Context, uri: Uri): Int = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use {
            ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }
    }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

    fun hasGps(ctx: Context, uri: Uri): Boolean = runCatching {
        ctx.contentResolver.openInputStream(uri)?.use { ExifInterface(it).latLong != null }
    }.getOrNull() ?: false

    private fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return null
        }
        return m
    }

    /** Pixel size of the image on disk (ignores EXIF rotation). */
    fun readBounds(ctx: Context, uri: Uri): Pair<Int, Int>? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        return if (o.outWidth > 0 && o.outHeight > 0) o.outWidth to o.outHeight else null
    }

    /**
     * Decodes `uri` memory-efficiently so that its longest edge is at most `maxDim`
     * (null = original), applying the EXIF orientation to the pixels.
     */
    fun decode(ctx: Context, uri: Uri, maxDim: Int?): Bitmap? {
        val (w, h) = readBounds(ctx, uri) ?: return null
        val longest = max(w, h)
        var sample = 1
        if (maxDim != null && maxDim > 0) {
            while (longest / (sample * 2) >= maxDim) sample *= 2
        }
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            ?: return null

        val matrix = orientationMatrix(readOrientation(ctx, uri))
        if (matrix != null) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }
        return if (maxDim != null && maxDim > 0) scaleToMax(bmp, maxDim) else bmp
    }

    fun decodeFile(file: File, maxDim: Int?): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        if (maxDim != null) while (max(o.outWidth, o.outHeight) / (sample * 2) >= maxDim) sample *= 2
        val bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        return if (maxDim != null) scaleToMax(bmp, maxDim) else bmp
    }

    /** Down-scales so the longest edge ≤ maxDim. Never up-scales. */
    fun scaleToMax(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val s = maxDim.toFloat() / longest
        val out = Bitmap.createScaledBitmap(
            src, (src.width * s).roundToInt().coerceAtLeast(1), (src.height * s).roundToInt().coerceAtLeast(1), true
        )
        if (out !== src) src.recycle()
        return out
    }

    /** Flattens transparency onto `color` (needed before JPEG encoding). */
    fun flatten(src: Bitmap, color: Int = Color.WHITE): Bitmap {
        if (!src.hasAlpha()) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(color)
        c.drawBitmap(src, 0f, 0f, null)
        return out
    }

    fun jpegBytes(bmp: Bitmap, quality: Int): ByteArray {
        val flat = flatten(bmp)
        val bos = java.io.ByteArrayOutputStream(256 * 1024)
        flat.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        if (flat !== bmp) flat.recycle()
        return bos.toByteArray()
    }
}
