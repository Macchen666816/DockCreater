package com.example.dockcreater

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.net.Uri
import android.provider.MediaStore
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import kotlin.math.roundToInt
import kotlin.math.*

data class LiquidGlassSettings(
    val cornerRadius: Float = .5f,
    val blurRadiusDp: Float = 0f,
    val refractionHeight: Float = .2f,
    val refractionAmount: Float = .2f,
    val chromaticAberration: Float = 0f
)

data class DockSettings(
    val zoom: Float = 1f, val cropX: Float = .5f, val cropY: Float = .5f,
    val width: Float = .9f, val height: Float = .085f, val bottom: Float = .035f,
    val radius: Float = .32f, val blur: Float = 28f, val opacity: Float = .28f,
    val dark: Boolean = false, val centerX: Float = .5f,
    val liquid: Boolean = false, val liquidSettings: LiquidGlassSettings = LiquidGlassSettings()
)

object WallpaperRenderer {
    fun load(context: Context, uri: Uri, width: Int, height: Int): Bitmap =
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val limit = maxOf(width, height) * 1.5f
            val scale = minOf(1f, limit / maxOf(info.size.width, info.size.height))
            decoder.setTargetSize(maxOf(1, (info.size.width * scale).toInt()), maxOf(1, (info.size.height * scale).toInt()))
            decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB))
        }

    fun render(context: Context, source: Bitmap, width: Int, height: Int, settings: DockSettings, dock: Boolean): Bitmap {
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val scale = maxOf(width.toFloat() / source.width, height.toFloat() / source.height) * settings.zoom
        val left = -(source.width * scale - width) * settings.cropX
        val top = -(source.height * scale - height) * settings.cropY
        canvas.drawBitmap(source, null, RectF(left, top, left + source.width * scale, top + source.height * scale), paint)
        if (!dock) return output
        val rect = RectF(width * (settings.centerX - settings.width / 2f), height * (1f - settings.bottom - settings.height), width * (settings.centerX + settings.width / 2f), height * (1f - settings.bottom))
        val radius = if (settings.liquid) minOf(rect.width(), rect.height()) * .5f * settings.liquidSettings.cornerRadius
            else rect.height() * settings.radius
        val clip = Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) }
        if (settings.liquid) {
            // Backdrop's catalog pipeline is vibrancy -> blur -> lens -> surface light.
            val vibrant = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            Canvas(vibrant).drawBitmap(output, 0f, 0f, Paint().apply {
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
            })
            val blurPx = settings.liquidSettings.blurRadiusDp * width / context.resources.configuration.screenWidthDp.coerceAtLeast(1)
            val blurred = if (blurPx > 0f) blur(context, vibrant, blurPx) else null
            val glass = try {
                LiquidGlassRenderer.render(blurred ?: vibrant, width, height, rect, radius, settings)
            } finally {
                blurred?.recycle()
                vibrant.recycle()
            }
            drawLiquidShadow(canvas, rect, radius, width)
            canvas.save()
            canvas.clipPath(clip)
            canvas.drawBitmap(glass, kotlin.math.floor(rect.left), kotlin.math.floor(rect.top), paint)
            canvas.restore()
            glass.recycle()
            return output
        }
        // Sample outside the dock mask too, avoiding artificial blur seams at its edges.
        val blurred = if (settings.blur > 0f) blur(context, output, settings.blur * width / 1080f) else null
        canvas.save()
        canvas.clipPath(clip)
        if (blurred != null) canvas.drawBitmap(blurred, null, Rect(0, 0, width, height), paint)
        paint.color = if (settings.dark) Color.rgb(20, 24, 28) else Color.WHITE
        paint.alpha = (settings.opacity * 255).roundToInt()
        canvas.drawRect(rect, paint)
        canvas.restore()
        drawDirectionalHighlight(canvas, rect, radius, width, settings.dark)
        paint.shader = null
        blurred?.recycle()
        return output
    }

    internal fun drawLiquidShadow(canvas: Canvas, rect: RectF, radius: Float, width: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0x24000000
            style = Paint.Style.STROKE
            strokeWidth = maxOf(1f, width / 300f)
            setShadowLayer(maxOf(4f, width / 72f), 0f, maxOf(2f, width / 270f), 0x65000000)
        }
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.clearShadowLayer()
    }

    /** A factory-style edge reflection: bright across the top, softer below, absent on sides. */
    internal fun drawDirectionalHighlight(canvas: Canvas, rect: RectF, radius: Float, width: Int, dark: Boolean) {
        val inset = maxOf(1f, width / 620f)
        val r = maxOf(0f, radius - inset)
        val left = rect.left + inset
        val right = rect.right - inset
        val top = rect.top + inset
        val bottom = rect.bottom - inset
        val fade = maxOf(1f, radius * .34f)

        fun edgePath(y: Float, topEdge: Boolean): Path = Path().apply {
            val direction = if (topEdge) 1f else -1f
            moveTo(left + r, y)
            lineTo(right - r, y)
            // Only turn into the rounded corners. The stroke dies before it can form side borders.
            quadTo(right - r * .35f, y, right - r * .08f, y + direction * fade)
            moveTo(left + r, y)
            quadTo(left + r * .35f, y, left + r * .08f, y + direction * fade)
        }

        val topPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            shader = LinearGradient(0f, top, 0f, top + fade,
                intArrayOf(if (dark) 0x688FA7B8 else 0xA8FFFFFF.toInt(), 0x00FFFFFF),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(edgePath(top, true), topPaint)

        val bottomPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            strokeWidth = maxOf(1f, width / 720f)
            shader = LinearGradient(0f, bottom, 0f, bottom - fade,
                intArrayOf(if (dark) 0x243A4650 else 0x58FFFFFF, 0x00101010),
                floatArrayOf(0f, 1f), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(edgePath(bottom, false), bottomPaint)
    }

    @Suppress("DEPRECATION")
    private fun blur(context: Context, bitmap: Bitmap, radius: Float): Bitmap {
        val factor = minOf(1f, 384f / bitmap.width)
        val scaled = Bitmap.createScaledBitmap(bitmap, maxOf(1, (bitmap.width * factor).toInt()), maxOf(1, (bitmap.height * factor).toInt()), true)
        val result = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScript.create(context)
        try {
            val input = Allocation.createFromBitmap(rs, scaled)
            val output = Allocation.createFromBitmap(rs, result)
            val script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            try {
                // Multiple passes preserve large dp blur values beyond RenderScript's 25px cap.
                val scaledRadius = radius * factor
                val passes = maxOf(1, ceil((scaledRadius / 25f).pow(2)).toInt())
                script.setRadius((scaledRadius / sqrt(passes.toFloat())).coerceIn(.1f, 25f))
                repeat(passes) { pass ->
                    script.setInput(input)
                    script.forEach(output)
                    if (pass < passes - 1) input.copyFrom(output)
                }
                output.copyTo(result)
            } finally { script.destroy(); input.destroy(); output.destroy() }
        } finally { rs.destroy(); if (scaled !== bitmap) scaled.recycle() }
        return result
    }

    fun save(context: Context, bitmap: Bitmap, prefix: String = "Dock"): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "${prefix}_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/DockCreater")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = checkNotNull(resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values))
        try {
            checkNotNull(resolver.openOutputStream(uri)).use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
            values.clear(); values.put(MediaStore.Images.Media.IS_PENDING, 0)
            check(resolver.update(uri, values, null, null) > 0)
            return uri
        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
    }
}
