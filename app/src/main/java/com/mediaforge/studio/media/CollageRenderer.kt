package com.mediaforge.studio.media

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF

enum class CollageStyle(val label: String) {
    SIDE_BY_SIDE("Side by side"),
    STACKED("Stacked"),
    GRID("Grid"),
    FEATURE("Feature")
}

enum class CollageAspect(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1", 1, 1),
    PORTRAIT("4:5", 4, 5),
    LANDSCAPE("3:2", 3, 2)
}

val COLLAGE_BACKGROUNDS = listOf(
    0xFFFFFFFF.toInt(), 0xFF0B0F17.toInt(), 0xFF1E293B.toInt(), 0xFFF5F0E6.toInt(), 0xFFD1FAE5.toInt()
)

data class CollageParams(
    val style: CollageStyle = CollageStyle.GRID,
    val aspect: CollageAspect = CollageAspect.SQUARE,
    /** px on a 1080-wide canvas (outer margin = spacing, gutters = spacing). */
    val spacing: Float = 16f,
    val cornerRadius: Float = 24f,
    val background: Int = 0xFF0B0F17.toInt()
)

object CollageRenderer {

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
        val w = outWidth
        val h = (w.toLong() * p.aspect.h / p.aspect.w).toInt()
        val s = outWidth / 1080f
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        c.drawColor(p.background)

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
            c.drawRoundedBitmap(bmp, centerCropRect(bmp.width, bmp.height, r), r, radius)
        }
        return out
    }
}
