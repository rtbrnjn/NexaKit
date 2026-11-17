package com.mediaforge.studio.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min

/** Draws `src` (cropped to `srcRect`) into `dest` with anti-aliased rounded corners. */
internal fun Canvas.drawRoundedBitmap(src: Bitmap, srcRect: Rect?, dest: RectF, radius: Float) {
    val sr = RectF(srcRect ?: Rect(0, 0, src.width, src.height))
    val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val m = Matrix().apply { setRectToRect(sr, dest, Matrix.ScaleToFit.FILL) }
    shader.setLocalMatrix(m)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    drawRoundRect(dest, radius, radius, paint)
}

/** Center-crop source rectangle so that it has the aspect ratio of `dest`. */
internal fun centerCropRect(srcW: Int, srcH: Int, dest: RectF): Rect {
    val destAspect = dest.width() / dest.height()
    val srcAspect = srcW.toFloat() / srcH
    return if (srcAspect > destAspect) {
        val w = (srcH * destAspect).toInt()
        val x = (srcW - w) / 2
        Rect(x, 0, x + w, srcH)
    } else {
        val h = (srcW / destAspect).toInt()
        val y = (srcH - h) / 2
        Rect(0, y, srcW, y + h)
    }
}

enum class CanvasRatio(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1 Square", 1, 1),
    PORTRAIT("4:5 Portrait", 4, 5),
    STORY("9:16 Story", 9, 16)
}

internal data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)

enum class MeshPreset(val label: String, internal val base: Int, internal val light: Boolean, internal val blobs: List<Blob>) {
    AURORA(
        "Aurora", 0xFF0B0F17.toInt(), false,
        listOf(
            Blob(0.10f, 0.08f, 0.85f, 0x8C22D3EE.toInt()),
            Blob(0.92f, 0.88f, 0.90f, 0x8034D399.toInt()),
            Blob(0.88f, 0.12f, 0.55f, 0x596366F1)
        )
    ),
    SUNSET(
        "Sunset", 0xFF1F1030.toInt(), false,
        listOf(
            Blob(0.10f, 0.92f, 0.90f, 0x99FB7185.toInt()),
            Blob(0.92f, 0.08f, 0.90f, 0x8CF59E0B.toInt()),
            Blob(0.50f, 0.50f, 0.55f, 0x59A855F7)
        )
    ),
    OCEAN(
        "Ocean", 0xFF06182C.toInt(), false,
        listOf(
            Blob(0.15f, 0.15f, 0.90f, 0x990EA5E9.toInt()),
            Blob(0.90f, 0.90f, 0.90f, 0x8014B8A6.toInt()),
            Blob(0.85f, 0.20f, 0.45f, 0x4D3B82F6)
        )
    ),
    LAVENDER(
        "Lavender", 0xFFEDE9FE.toInt(), true,
        listOf(
            Blob(0.10f, 0.10f, 0.85f, 0xE6C4B5FD.toInt()),
            Blob(0.92f, 0.90f, 0.90f, 0xCCF9A8D4.toInt()),
            Blob(0.92f, 0.10f, 0.50f, 0xB3A5F3FC.toInt())
        )
    ),
    PEACH(
        "Peach", 0xFFFFF1E6.toInt(), true,
        listOf(
            Blob(0.10f, 0.90f, 0.90f, 0xCCFDBA74.toInt()),
            Blob(0.90f, 0.10f, 0.85f, 0xB3FCA5A5.toInt()),
            Blob(0.50f, 0.50f, 0.45f, 0x80FDE68A.toInt())
        )
    ),
    GRAPHITE(
        "Graphite", 0xFF111827.toInt(), false,
        listOf(
            Blob(0.20f, 0.10f, 0.90f, 0x99475569.toInt()),
            Blob(0.90f, 0.90f, 0.80f, 0xCC1E293B.toInt())
        )
    )
}

sealed interface MockBackground {
    data class Solid(val color: Int) : MockBackground
    data class Mesh(val preset: MeshPreset) : MockBackground
}

val SOLID_SWATCHES = listOf(
    0xFFFFFFFF.toInt(), 0xFF0B0F17.toInt(), 0xFF1E293B.toInt(), 0xFFF5F0E6.toInt(),
    0xFFD1FAE5.toInt(), 0xFFDBEAFE.toInt(), 0xFFFFE4E6.toInt(), 0xFFFEF3C7.toInt()
)

data class MockupParams(
    val ratio: CanvasRatio = CanvasRatio.SQUARE,
    val background: MockBackground = MockBackground.Mesh(MeshPreset.AURORA),
    /** 0..100, in px of a 1080-wide canvas. */
    val cornerRadius: Float = 48f,
    /** 0..1 */
    val shadow: Float = 0.6f,
    /** Fraction of canvas width, 0..0.35 */
    val padding: Float = 0.12f,
    val glassFrame: Boolean = true,
    val watermark: String = ""
)

object MockupRenderer {

    fun outputSize(ratio: CanvasRatio, width: Int): Pair<Int, Int> =
        width to (width.toLong() * ratio.h / ratio.w).toInt()

    fun render(product: Bitmap, p: MockupParams, outWidth: Int): Bitmap {
        val (w, h) = outputSize(p.ratio, outWidth)
        val s = outWidth / 1080f
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val bgIsLight = drawBackground(c, p.background, w, h)

        val pad = p.padding * w
        val frameInset = if (p.glassFrame) 26f * s else 0f
        val area = RectF(pad + frameInset, pad + frameInset, w - pad - frameInset, h - pad - frameInset)
        if (area.width() < 20f || area.height() < 20f) return out

        val fit = min(area.width() / product.width, area.height() / product.height)
        val dw = product.width * fit
        val dh = product.height * fit
        val dest = RectF(
            area.centerX() - dw / 2, area.centerY() - dh / 2,
            area.centerX() + dw / 2, area.centerY() + dh / 2
        )
        val radius = p.cornerRadius * s

        // Outer shape that casts the shadow (glass frame if enabled, otherwise the photo itself).
        val outer = if (p.glassFrame) RectF(dest).apply { inset(-frameInset, -frameInset) } else dest
        val outerRadius = if (p.glassFrame) radius + frameInset * 0.8f else radius

        if (p.shadow > 0.01f) {
            val sp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((p.shadow * 150).toInt().coerceIn(0, 255), 0, 0, 0)
                maskFilter = BlurMaskFilter((18f + 46f * p.shadow) * s, BlurMaskFilter.Blur.NORMAL)
            }
            val off = (10f + 26f * p.shadow) * s
            c.drawRoundRect(
                RectF(outer).apply { offset(0f, off) }, outerRadius, outerRadius, sp
            )
        }

        if (p.glassFrame) {
            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (bgIsLight) Color.argb(120, 255, 255, 255) else Color.argb(36, 255, 255, 255)
            }
            c.drawRoundRect(outer, outerRadius, outerRadius, fill)
            val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, 2f * s)
                color = if (bgIsLight) Color.argb(200, 255, 255, 255) else Color.argb(90, 255, 255, 255)
            }
            c.drawRoundRect(outer, outerRadius, outerRadius, border)
        }

        c.drawRoundedBitmap(product, null, dest, radius)

        if (p.watermark.isNotBlank()) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = w * 0.034f
                color = if (bgIsLight) Color.argb(190, 20, 24, 38) else Color.argb(210, 255, 255, 255)
                letterSpacing = 0.08f
                setShadowLayer(4f * s, 0f, 1f * s, if (bgIsLight) Color.argb(40, 255, 255, 255) else Color.argb(140, 0, 0, 0))
            }
            val y = h - max(pad * 0.42f, w * 0.045f)
            c.drawText(p.watermark.trim(), w / 2f, y, tp)
        }
        return out
    }

    /** @return true when the resulting background is light (used to pick contrasting overlays). */
    private fun drawBackground(c: Canvas, bg: MockBackground, w: Int, h: Int): Boolean {
        when (bg) {
            is MockBackground.Solid -> {
                c.drawColor(bg.color)
                val lum = (0.299 * Color.red(bg.color) + 0.587 * Color.green(bg.color) + 0.114 * Color.blue(bg.color)) / 255.0
                return lum > 0.6
            }
            is MockBackground.Mesh -> {
                c.drawColor(bg.preset.base)
                val big = max(w, h).toFloat()
                bg.preset.blobs.forEach { b ->
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        shader = RadialGradient(
                            b.x * w, b.y * h, b.r * big, b.color, b.color and 0x00FFFFFF,
                            Shader.TileMode.CLAMP
                        )
                    }
                    c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
                }
                return bg.preset.light
            }
        }
    }
}
