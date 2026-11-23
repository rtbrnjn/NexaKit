package com.mediaforge.studio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import kotlin.math.min

enum class CollageStyle(val label: String) {
    SIDE_BY_SIDE("Side by side"),
    STACKED("Stacked"),
    GRID("Grid"),
    FEATURE("Feature")
}

enum class CollageRatioCategory(val label: String) {
    SQUARE("Square"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

enum class CollageAspect(
    val label: String,
    val category: CollageRatioCategory,
    val w: Int,
    val h: Int,
    val description: String
) {
    SQUARE("1:1", CollageRatioCategory.SQUARE, 1, 1, "Standar feed persegi (1:1)"),
    PORTRAIT_4_5("4:5", CollageRatioCategory.PORTRAIT, 4, 5, "Optimal untuk Instagram Feed portrait (4:5)"),
    PORTRAIT_9_16("9:16", CollageRatioCategory.PORTRAIT, 9, 16, "Optimal untuk TikTok, Reels & Shopee Video (9:16)"),
    LANDSCAPE_16_9("16:9", CollageRatioCategory.LANDSCAPE, 16, 9, "Optimal untuk thumbnail YouTube & banner lebar (16:9)"),
    LANDSCAPE_4_3("4:3", CollageRatioCategory.LANDSCAPE, 4, 3, "Optimal untuk presentasi & dokumen klasik (4:3)")
}

enum class CollagePhotoMode(val label: String, val description: String) {
    FILL("Fill (Penuh)", "Penuhi sel grid dengan potongan tengah rapi"),
    FIT("Fit (Utuh)", "Tampilkan seluruh foto tanpa memotong bagian penting")
}

val COLLAGE_BACKGROUNDS = listOf(
    0xFF0B0E13.toInt(),
    0xFFFFFFFF.toInt(),
    0xFF334155.toInt(),
    0xFFFFD1DC.toInt(),
    0xFFF5F0E6.toInt(),
    0xFFDCE8E1.toInt()
)

data class CollageParams(
    val style: CollageStyle = CollageStyle.GRID,
    val aspect: CollageAspect = CollageAspect.SQUARE,
    val photoMode: CollagePhotoMode = CollagePhotoMode.FILL,
    /** px on a 1080-wide canvas (outer margin = spacing, gutters = spacing). */
    val spacing: Float = 16f,
    val cornerRadius: Float = 24f,
    val background: Int = 0xFF0B0E13.toInt()
)

object CollageRenderer {

    fun outputSize(aspect: CollageAspect, outWidth: Int): Pair<Int, Int> =
        outWidth to (outWidth.toLong() * aspect.h / aspect.w).toInt()

    /** Computes an uncropped destination rectangle preserving source aspect ratio centered inside [dest]. */
    internal fun fitRect(srcW: Int, srcH: Int, dest: RectF): RectF {
        val scale = min(dest.width() / srcW.toFloat(), dest.height() / srcH.toFloat())
        val dw = srcW * scale
        val dh = srcH * scale
        return RectF(
            dest.centerX() - dw / 2f,
            dest.centerY() - dh / 2f,
            dest.centerX() + dw / 2f,
            dest.centerY() + dh / 2f
        )
    }

    /** Unit-square cells (0..1) for `n` photos. */
    fun cells(style: CollageStyle, n: Int): List<RectF> {
        fun row(count: Int) = List(count) { RectF(it / count.toFloat(), 0f, (it + 1) / count.toFloat(), 1f) }
        fun col(count: Int) = List(count) { RectF(0f, it / count.toFloat(), 1f, (it + 1) / count.toFloat()) }
        return when (style) {
            CollageStyle.SIDE_BY_SIDE -> row(n)
            CollageStyle.STACKED -> col(n)
            CollageStyle.GRID -> when (n) {
                2 -> row(2)
                3 -> listOf(RectF(0f, 0f, 1f, .5f), RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f))
                else -> listOf(
                    RectF(0f, 0f, .5f, .5f), RectF(.5f, 0f, 1f, .5f),
                    RectF(0f, .5f, .5f, 1f), RectF(.5f, .5f, 1f, 1f)
                )
            }
            CollageStyle.FEATURE -> {
                val big = RectF(0f, 0f, .6f, 1f)
                val rest = n - 1
                listOf(big) + List(rest) {
                    RectF(.6f, it / rest.toFloat(), 1f, (it + 1) / rest.toFloat())
                }
            }
        }
    }

    fun render(photos: List<Bitmap>, p: CollageParams, outWidth: Int): Bitmap {
        require(photos.size in 2..4) { "Collage needs 2–4 photos" }
        val (w, h) = outputSize(p.aspect, outWidth)
        val s = outWidth / 1080f
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(p.background)

        val bgLum = (0.299 * Color.red(p.background) + 0.587 * Color.green(p.background) + 0.114 * Color.blue(p.background)) / 255.0
        val fitSlotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (bgLum > 0.6) Color.argb(14, 15, 23, 42) else Color.argb(18, 255, 255, 255)
        }

        val gap = p.spacing * s
        val inner = RectF(gap, gap, w - gap, h - gap)
        val radius = p.cornerRadius * s
        cells(p.style, photos.size).forEachIndexed { i, u ->
            val r = RectF(
                inner.left + u.left * inner.width() + if (u.left > 0f) gap / 2 else 0f,
                inner.top + u.top * inner.height() + if (u.top > 0f) gap / 2 else 0f,
                inner.left + u.right * inner.width() - if (u.right < 1f) gap / 2 else 0f,
                inner.top + u.bottom * inner.height() - if (u.bottom < 1f) gap / 2 else 0f
            )
            if (r.width() < 2f || r.height() < 2f) return@forEachIndexed
            val bmp = photos[i]
            if (p.photoMode == CollagePhotoMode.FIT) {
                c.drawRoundRect(r, radius, radius, fitSlotPaint)
                val dest = fitRect(bmp.width, bmp.height, r)
                val safeRadius = min(radius, min(dest.width(), dest.height()) * 0.25f)
                c.drawRoundedBitmap(bmp, null, dest, safeRadius)
            } else {
                c.drawRoundedBitmap(bmp, centerCropRect(bmp.width, bmp.height, r), r, radius)
            }
        }
        return out
    }
}
