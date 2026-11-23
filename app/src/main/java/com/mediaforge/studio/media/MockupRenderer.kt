package com.mediaforge.studio.media

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Draws `src` (cropped to `srcRect`) into `dest` with anti-aliased rounded corners. */
internal fun Canvas.drawRoundedBitmap(src: Bitmap, srcRect: Rect?, dest: RectF, radius: Float) {
    val sr = RectF(srcRect ?: Rect(0, 0, src.width, src.height))
    val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val m = Matrix().apply { setRectToRect(sr, dest, Matrix.ScaleToFit.FILL) }
    shader.setLocalMatrix(m)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    drawRoundRect(dest, radius, radius, paint)
}

/** Draws `src` inside `dest` clipped by independent per-corner radii `[tl, tl, tr, tr, br, br, bl, bl]`. */
private fun Canvas.drawRadiiBitmap(src: Bitmap, dest: RectF, radii: FloatArray) {
    val sr = RectF(0f, 0f, src.width.toFloat(), src.height.toFloat())
    val shader = BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    val m = Matrix().apply { setRectToRect(sr, dest, Matrix.ScaleToFit.FILL) }
    shader.setLocalMatrix(m)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
    val path = Path().apply { addRoundRect(dest, radii, Path.Direction.CW) }
    drawPath(path, paint)
}

/** Center-crop source rectangle so that it has the aspect ratio of `dest`. */
internal fun centerCropRect(srcW: Int, srcH: Int, dest: RectF): Rect {
    val destAspect = dest.width() / dest.height()
    val srcAspect = srcW.toFloat() / srcH
    return if (srcAspect > destAspect) {
        val w = (srcH * destAspect).toInt().coerceAtLeast(1)
        val x = (srcW - w) / 2
        Rect(x, 0, x + w, srcH)
    } else {
        val h = (srcW / destAspect).toInt().coerceAtLeast(1)
        val y = (srcH - h) / 2
        Rect(0, y, srcW, y + h)
    }
}

enum class CanvasRatio(val label: String, val w: Int, val h: Int) {
    SQUARE("1:1 Square", 1, 1),
    PORTRAIT("4:5 Portrait", 4, 5),
    STORY("9:16 Story", 9, 16),
    LANDSCAPE("16:9 Wide", 16, 9)
}

enum class DeviceFrameStyle(val label: String, val subtitle: String) {
    FLAT_SMARTPHONE("Smartphone", "Bezel tipis & punch-hole modern"),
    FRAMELESS_GLASS("Frameless Glass", "Bingkai kaca Liquid Glass tipis"),
    BROWSER_WINDOW("Browser Window", "Frame jendela desktop minimalis"),
    TABLET("Tablet", "Bingkai tablet proporsional")
}

internal data class Blob(val x: Float, val y: Float, val r: Float, val color: Int)

enum class MeshPreset(val label: String, internal val base: Int, internal val light: Boolean, internal val blobs: List<Blob>) {
    AURORA(
        "Monochrome Mist", 0xFF0B0E13.toInt(), false,
        listOf(
            Blob(0.14f, 0.12f, 0.85f, 0x4DB8C7D9),
            Blob(0.88f, 0.86f, 0.90f, 0x3D8AB59D),
            Blob(0.85f, 0.14f, 0.55f, 0x3364748B)
        )
    ),
    OCEAN(
        "Silver Studio", 0xFFF4F6F9.toInt(), true,
        listOf(
            Blob(0.15f, 0.15f, 0.90f, 0xCCFFFFFF.toInt()),
            Blob(0.90f, 0.90f, 0.90f, 0x80CBD5E1.toInt()),
            Blob(0.80f, 0.20f, 0.50f, 0x4D94A3B8)
        )
    ),
    SUNSET(
        "Rose Silk", 0xFFFFF1F5.toInt(), true,
        listOf(
            Blob(0.12f, 0.88f, 0.90f, 0x99FFD1DC.toInt()),
            Blob(0.90f, 0.12f, 0.85f, 0x80FBCFE8.toInt())
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

data class SolidBackdropPreset(val label: String, val color: Int)

val STUDIO_SOLID_PRESETS = listOf(
    SolidBackdropPreset("Obsidian Dark", 0xFF0B0E13.toInt()),
    SolidBackdropPreset("Pure White", 0xFFFFFFFF.toInt()),
    SolidBackdropPreset("Soft Pastel Pink", 0xFFFFD1DC.toInt()),
    SolidBackdropPreset("Slate Gray", 0xFF475569.toInt()),
    SolidBackdropPreset("Warm Cream", 0xFFF5F0E6.toInt()),
    SolidBackdropPreset("Muted Sage", 0xFFDCE8E1.toInt()),
    SolidBackdropPreset("Cool Mist", 0xFFE2E8F0.toInt()),
    SolidBackdropPreset("Midnight Navy", 0xFF141B29.toInt())
)

val SOLID_SWATCHES = STUDIO_SOLID_PRESETS.map { it.color }

sealed interface MockBackground {
    data class Solid(val color: Int) : MockBackground
    data object BlurredImage : MockBackground
    data class Mesh(val preset: MeshPreset) : MockBackground
}

data class MockupParams(
    val ratio: CanvasRatio = CanvasRatio.SQUARE,
    val frameStyle: DeviceFrameStyle = DeviceFrameStyle.FLAT_SMARTPHONE,
    val background: MockBackground = MockBackground.Solid(0xFF0B0E13.toInt()),
    /** 0..100, in px of a 1080-wide canvas. */
    val cornerRadius: Float = 44f,
    /** 0..1 */
    val shadow: Float = 0.6f,
    /** Fraction of shortest canvas dimension, 0.04..0.30 */
    val padding: Float = 0.12f,
    val glassReflection: Boolean = true,
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
        val bgIsLight = drawBackground(c, product, p.background, w, h)

        val pad = p.padding * min(w, h)
        val avail = RectF(pad, pad, w - pad, h - pad)
        if (avail.width() < 24f || avail.height() < 24f) return out

        val cornerR = p.cornerRadius * s

        when (p.frameStyle) {
            DeviceFrameStyle.FRAMELESS_GLASS ->
                drawFramelessGlass(c, product, avail, cornerR, p, bgIsLight, s)
            DeviceFrameStyle.FLAT_SMARTPHONE ->
                drawFlatSmartphone(c, product, avail, cornerR, p, s)
            DeviceFrameStyle.BROWSER_WINDOW ->
                drawBrowserWindow(c, product, avail, cornerR, p, bgIsLight, s)
            DeviceFrameStyle.TABLET ->
                drawTabletFrame(c, product, avail, cornerR, p, s)
        }

        if (p.watermark.isNotBlank()) {
            val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                textSize = min(w, h) * 0.030f
                color = if (bgIsLight) Color.argb(190, 15, 23, 42) else Color.argb(215, 245, 247, 250)
                letterSpacing = 0.08f
                setShadowLayer(4f * s, 0f, 1f * s, if (bgIsLight) Color.argb(40, 255, 255, 255) else Color.argb(140, 0, 0, 0))
            }
            val y = h - max(pad * 0.38f, h * 0.036f)
            c.drawText(p.watermark.trim(), w / 2f, y, tp)
        }
        return out
    }

    private fun drawFramelessGlass(
        c: Canvas,
        product: Bitmap,
        avail: RectF,
        cornerR: Float,
        p: MockupParams,
        bgIsLight: Boolean,
        s: Float
    ) {
        val frameInset = max(14f * s, min(avail.width(), avail.height()) * 0.026f)
        val innerArea = RectF(avail).apply { inset(frameInset, frameInset) }
        val dest = fitCenteredRect(product.width, product.height, innerArea)
        val outer = RectF(dest).apply { inset(-frameInset, -frameInset) }
        val outerR = cornerR + frameInset * 0.72f

        drawDropShadow(c, outer, outerR, p.shadow, s)

        val glassFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (bgIsLight) Color.argb(165, 255, 255, 255) else Color.argb(36, 255, 255, 255)
        }
        c.drawRoundRect(outer, outerR, outerR, glassFill)

        val border = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(1.5f, 2f * s)
            color = if (bgIsLight) Color.argb(180, 203, 213, 225) else Color.argb(95, 255, 255, 255)
        }
        c.drawRoundRect(outer, outerR, outerR, border)

        c.drawRoundedBitmap(product, null, dest, cornerR)
        if (p.glassReflection) drawGlassReflection(c, dest, cornerR)
    }

    private fun drawFlatSmartphone(
        c: Canvas,
        product: Bitmap,
        avail: RectF,
        cornerR: Float,
        p: MockupParams,
        s: Float
    ) {
        val rawAspect = product.width.toFloat() / product.height.coerceAtLeast(1)
        val screenAspect = rawAspect.coerceIn(0.45f, 0.72f)
        val phoneScreenMax = RectF(avail).apply { inset(avail.width() * 0.04f, avail.height() * 0.04f) }
        val screenBox = fitAspectRect(screenAspect, phoneScreenMax)
        val bezel = max(10f * s, screenBox.width() * 0.036f)
        val outer = RectF(screenBox).apply { inset(-bezel, -bezel) }
        val outerR = max(cornerR, bezel * 2.2f)
        val innerR = max(6f * s, outerR - bezel * 0.85f)

        drawDropShadow(c, outer, outerR, p.shadow, s)

        // Subtle side buttons
        val btnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(42, 48, 60) }
        val btnW = max(3f * s, bezel * 0.24f)
        c.drawRoundRect(
            RectF(outer.right - 1f, outer.top + outer.height() * 0.26f, outer.right + btnW, outer.top + outer.height() * 0.38f),
            btnW, btnW, btnPaint
        )
        c.drawRoundRect(
            RectF(outer.left - btnW, outer.top + outer.height() * 0.22f, outer.left + 1f, outer.top + outer.height() * 0.31f),
            btnW, btnW, btnPaint
        )
        c.drawRoundRect(
            RectF(outer.left - btnW, outer.top + outer.height() * 0.33f, outer.left + 1f, outer.top + outer.height() * 0.42f),
            btnW, btnW, btnPaint
        )

        // Flat dark obsidian chassis & fine metallic outer stroke
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                outer.left, outer.top, outer.right, outer.bottom,
                Color.rgb(24, 29, 38), Color.rgb(12, 15, 22), Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(outer, outerR, outerR, bodyPaint)
        c.drawRoundRect(
            outer, outerR, outerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, 2.2f * s)
                color = Color.argb(95, 226, 232, 240)
            }
        )

        // Screen background for non-portrait product images
        c.drawRoundRect(screenBox, innerR, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 14, 19) })
        val contentDest = if (rawAspect in 0.42f..0.78f) screenBox else fitCenteredRect(product.width, product.height, screenBox)
        if (rawAspect in 0.42f..0.78f) {
            c.drawRoundedBitmap(product, centerCropRect(product.width, product.height, screenBox), screenBox, innerR)
        } else {
            c.save()
            val clipPath = Path().apply { addRoundRect(screenBox, innerR, innerR, Path.Direction.CW) }
            c.clipPath(clipPath)
            c.drawRoundedBitmap(product, null, contentDest, min(innerR * 0.5f, 18f * s))
            c.restore()
        }

        if (p.glassReflection) drawGlassReflection(c, screenBox, innerR)

        // Punch-hole camera & minimal home bar inside screen
        val camR = max(5f * s, screenBox.width() * 0.022f)
        val camY = screenBox.top + camR * 1.9f
        c.drawCircle(screenBox.centerX(), camY, camR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(8, 10, 14) })
        c.drawCircle(
            screenBox.centerX(), camY, camR * 0.52f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(32, 44, 62) }
        )
        c.drawCircle(
            screenBox.centerX() - camR * 0.25f, camY - camR * 0.25f, camR * 0.18f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(115, 255, 255, 255) }
        )

        val homeW = screenBox.width() * 0.28f
        val homeH = max(3f * s, screenBox.height() * 0.006f)
        val homeY = screenBox.bottom - bezel * 0.85f
        c.drawRoundRect(
            RectF(screenBox.centerX() - homeW / 2f, homeY - homeH, screenBox.centerX() + homeW / 2f, homeY),
            homeH, homeH,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(85, 255, 255, 255) }
        )
    }

    private fun drawBrowserWindow(
        c: Canvas,
        product: Bitmap,
        avail: RectF,
        cornerR: Float,
        p: MockupParams,
        bgIsLight: Boolean,
        s: Float
    ) {
        // Reserve top bar proportion relative to available area
        val prelim = fitCenteredRect(product.width, product.height, avail)
        val barRatio = 0.072f
        val estW = prelim.width()
        val barH = max(34f * s, min(72f * s, estW * barRatio))
        val contentAvail = RectF(avail.left, avail.top + barH, avail.right, avail.bottom)
        val contentDest = fitCenteredRect(product.width, product.height, contentAvail)

        val bezel = max(1.5f, 2f * s)
        val outer = RectF(
            contentDest.left,
            contentDest.top - barH,
            contentDest.right,
            contentDest.bottom
        )
        val winR = min(cornerR, barH * 0.52f).coerceAtLeast(14f * s)

        drawDropShadow(c, outer, winR, p.shadow, s)

        // Window chrome fill (Monochrome Dark header on light/dark or matching translucent glass header)
        val chromeColor = if (bgIsLight) Color.rgb(248, 250, 252) else Color.rgb(22, 27, 36)
        val barRect = RectF(outer.left, outer.top, outer.right, contentDest.top)
        val topRadii = floatArrayOf(winR, winR, winR, winR, 0f, 0f, 0f, 0f)
        val chromePath = Path().apply { addRoundRect(barRect, topRadii, Path.Direction.CW) }
        c.drawPath(chromePath, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chromeColor })

        // Monochrome minimal window dots + glass address capsule
        val dotR = barH * 0.14f
        val dotY = barRect.centerY()
        val startX = barRect.left + barH * 0.55f
        val dotColor = if (bgIsLight) Color.argb(85, 15, 23, 42) else Color.argb(110, 255, 255, 255)
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = dotColor }
        repeat(3) { i -> c.drawCircle(startX + i * dotR * 3.1f, dotY, dotR, dotPaint) }

        val pillW = min(barRect.width() * 0.38f, 220f * s)
        val pillH = barH * 0.44f
        c.drawRoundRect(
            RectF(barRect.centerX() - pillW / 2f, dotY - pillH / 2f, barRect.centerX() + pillW / 2f, dotY + pillH / 2f),
            pillH / 2f, pillH / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (bgIsLight) Color.argb(18, 15, 23, 42) else Color.argb(24, 255, 255, 255)
            }
        )

        // Hairline divider
        c.drawLine(
            outer.left, contentDest.top, outer.right, contentDest.top,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = max(1f, 1.5f * s)
                color = if (bgIsLight) Color.argb(28, 15, 23, 42) else Color.argb(38, 255, 255, 255)
            }
        )

        // Screenshot/content inside lower rounded window bounds
        val bottomRadii = floatArrayOf(0f, 0f, 0f, 0f, winR, winR, winR, winR)
        c.drawRadiiBitmap(product, contentDest, bottomRadii)

        if (p.glassReflection) drawGlassReflection(c, contentDest, winR * 0.5f)

        // Outer glass hairline frame
        c.drawRoundRect(
            outer, winR, winR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = bezel
                color = if (bgIsLight) Color.argb(55, 15, 23, 42) else Color.argb(72, 255, 255, 255)
            }
        )
    }

    private fun drawTabletFrame(
        c: Canvas,
        product: Bitmap,
        avail: RectF,
        cornerR: Float,
        p: MockupParams,
        s: Float
    ) {
        val rawAspect = product.width.toFloat() / product.height.coerceAtLeast(1)
        val screenAspect = rawAspect.coerceIn(0.65f, 1.55f)
        val screenMax = RectF(avail).apply { inset(avail.width() * 0.035f, avail.height() * 0.035f) }
        val screenBox = fitAspectRect(screenAspect, screenMax)
        val bezel = max(14f * s, min(screenBox.width(), screenBox.height()) * 0.045f)
        val outer = RectF(screenBox).apply { inset(-bezel, -bezel) }
        val outerR = max(cornerR, bezel * 1.85f)
        val innerR = max(6f * s, outerR - bezel * 0.8f)

        drawDropShadow(c, outer, outerR, p.shadow, s)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                outer.left, outer.top, outer.right, outer.bottom,
                Color.rgb(28, 34, 45), Color.rgb(13, 17, 24), Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(outer, outerR, outerR, bodyPaint)
        c.drawRoundRect(
            outer, outerR, outerR,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = max(1.5f, 2f * s)
                color = Color.argb(90, 226, 232, 240)
            }
        )

        c.drawRoundRect(screenBox, innerR, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(11, 14, 19) })
        if (rawAspect in 0.62f..1.62f) {
            c.drawRoundedBitmap(product, centerCropRect(product.width, product.height, screenBox), screenBox, innerR)
        } else {
            val contentDest = fitCenteredRect(product.width, product.height, screenBox)
            c.save()
            c.clipPath(Path().apply { addRoundRect(screenBox, innerR, innerR, Path.Direction.CW) })
            c.drawRoundedBitmap(product, null, contentDest, min(innerR * 0.5f, 18f * s))
            c.restore()
        }

        if (p.glassReflection) drawGlassReflection(c, screenBox, innerR)

        // Centered subtle front camera on top bezel
        val camY = outer.top + bezel / 2f
        val camR = bezel * 0.17f
        c.drawCircle(outer.centerX(), camY, camR, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(45, 58, 78) })
    }

    private fun drawDropShadow(c: Canvas, rect: RectF, cornerR: Float, shadowStrength: Float, s: Float) {
        if (shadowStrength <= 0.01f) return
        val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((shadowStrength * 135).toInt().coerceIn(0, 255), 0, 0, 0)
            maskFilter = BlurMaskFilter((20f + 46f * shadowStrength) * s, BlurMaskFilter.Blur.NORMAL)
        }
        val offY = (12f + 26f * shadowStrength) * s
        c.drawRoundRect(RectF(rect).apply { offset(0f, offY) }, cornerR, cornerR, ambientPaint)

        val contactPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((shadowStrength * 75).toInt().coerceIn(0, 255), 0, 0, 0)
            maskFilter = BlurMaskFilter(8f * s, BlurMaskFilter.Blur.NORMAL)
        }
        c.drawRoundRect(RectF(rect).apply { offset(0f, 5f * s) }, cornerR, cornerR, contactPaint)
    }

    private fun drawGlassReflection(c: Canvas, rect: RectF, cornerR: Float) {
        val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left, rect.top, rect.centerX(), rect.bottom * 0.85f,
                intArrayOf(Color.argb(34, 255, 255, 255), Color.argb(10, 255, 255, 255), Color.TRANSPARENT),
                floatArrayOf(0f, 0.35f, 0.72f),
                Shader.TileMode.CLAMP
            )
        }
        c.drawRoundRect(rect, cornerR, cornerR, sheenPaint)
    }

    private fun fitCenteredRect(srcW: Int, srcH: Int, area: RectF): RectF {
        val fit = min(area.width() / srcW.toFloat(), area.height() / srcH.toFloat())
        val dw = srcW * fit
        val dh = srcH * fit
        return RectF(
            area.centerX() - dw / 2f, area.centerY() - dh / 2f,
            area.centerX() + dw / 2f, area.centerY() + dh / 2f
        )
    }

    private fun fitAspectRect(aspect: Float, area: RectF): RectF {
        val areaAspect = area.width() / area.height()
        val dw: Float
        val dh: Float
        if (aspect >= areaAspect) {
            dw = area.width()
            dh = dw / aspect
        } else {
            dh = area.height()
            dw = dh * aspect
        }
        return RectF(
            area.centerX() - dw / 2f, area.centerY() - dh / 2f,
            area.centerX() + dw / 2f, area.centerY() + dh / 2f
        )
    }

    /** @return true when the resulting background is light (used to pick contrasting overlays). */
    private fun drawBackground(c: Canvas, product: Bitmap, bg: MockBackground, w: Int, h: Int): Boolean {
        when (bg) {
            is MockBackground.Solid -> {
                c.drawColor(bg.color)
                val lum = (0.299 * Color.red(bg.color) + 0.587 * Color.green(bg.color) + 0.114 * Color.blue(bg.color)) / 255.0
                return lum > 0.6
            }
            is MockBackground.BlurredImage -> {
                return drawBlurredBackdrop(c, product, w, h)
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

    /** Generates a smooth gaussian-like blurred canvas derived directly from the source photo's colors. */
    private fun drawBlurredBackdrop(c: Canvas, src: Bitmap, w: Int, h: Int): Boolean {
        val targetAspect = w.toFloat() / h
        val sampleLong = 72
        val sampleW = if (targetAspect >= 1f) sampleLong else (sampleLong * targetAspect).roundToInt().coerceAtLeast(24)
        val sampleH = if (targetAspect >= 1f) (sampleLong / targetAspect).roundToInt().coerceAtLeast(24) else sampleLong

        val cropped = Bitmap.createBitmap(sampleW, sampleH, Bitmap.Config.ARGB_8888)
        val sc = Canvas(cropped)
        val coverRect = centerCropRect(src.width, src.height, RectF(0f, 0f, sampleW.toFloat(), sampleH.toFloat()))
        sc.drawBitmap(src, coverRect, Rect(0, 0, sampleW, sampleH), Paint(Paint.FILTER_BITMAP_FLAG))

        val pixels = IntArray(sampleW * sampleH)
        cropped.getPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)
        repeat(3) {
            boxBlurHorizontal(pixels, sampleW, sampleH, radius = 5)
            boxBlurVertical(pixels, sampleW, sampleH, radius = 5)
        }
        cropped.setPixels(pixels, 0, sampleW, 0, 0, sampleW, sampleH)

        // Average dominant luminance after blur
        var lumSum = 0.0
        for (px in pixels) {
            lumSum += (0.299 * Color.red(px) + 0.587 * Color.green(px) + 0.114 * Color.blue(px)) / 255.0
        }
        val avgLum = lumSum / pixels.size.coerceAtLeast(1)

        c.drawBitmap(cropped, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG))
        cropped.recycle()

        // Gentle studio veil for depth and figure-ground separation
        val veilAlpha = if (avgLum > 0.62) 56 else 72
        val veilColor = if (avgLum > 0.62) Color.argb(veilAlpha, 255, 255, 255) else Color.argb(veilAlpha, 11, 14, 19)
        c.drawColor(veilColor)
        return avgLum > 0.62
    }

    private fun boxBlurHorizontal(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val row = IntArray(w)
        val window = radius * 2 + 1
        for (y in 0 until h) {
            val offset = y * w
            var rSum = 0; var gSum = 0; var bSum = 0
            for (k in -radius..radius) {
                val px = pixels[offset + k.coerceIn(0, w - 1)]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px)
            }
            for (x in 0 until w) {
                row[x] = Color.rgb(rSum / window, gSum / window, bSum / window)
                val outX = (x - radius).coerceIn(0, w - 1)
                val inX = (x + radius + 1).coerceIn(0, w - 1)
                val outPx = pixels[offset + outX]
                val inPx = pixels[offset + inX]
                rSum += Color.red(inPx) - Color.red(outPx)
                gSum += Color.green(inPx) - Color.green(outPx)
                bSum += Color.blue(inPx) - Color.blue(outPx)
            }
            System.arraycopy(row, 0, pixels, offset, w)
        }
    }

    private fun boxBlurVertical(pixels: IntArray, w: Int, h: Int, radius: Int) {
        val col = IntArray(h)
        val window = radius * 2 + 1
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (k in -radius..radius) {
                val px = pixels[k.coerceIn(0, h - 1) * w + x]
                rSum += Color.red(px); gSum += Color.green(px); bSum += Color.blue(px)
            }
            for (y in 0 until h) {
                col[y] = Color.rgb(rSum / window, gSum / window, bSum / window)
                val outY = (y - radius).coerceIn(0, h - 1)
                val inY = (y + radius + 1).coerceIn(0, h - 1)
                val outPx = pixels[outY * w + x]
                val inPx = pixels[inY * w + x]
                rSum += Color.red(inPx) - Color.red(outPx)
                gSum += Color.green(inPx) - Color.green(outPx)
                bSum += Color.blue(inPx) - Color.blue(outPx)
            }
            for (y in 0 until h) pixels[y * w + x] = col[y]
        }
    }
}
