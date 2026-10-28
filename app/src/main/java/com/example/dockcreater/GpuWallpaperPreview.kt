// Copyright 2025 Kyant; SPDX-License-Identifier: Apache-2.0
// Wallpaper AGSL adaptation and Android integration; see docs/gpu-wallpaper-preview.md.
package com.example.dockcreater

import android.content.Context
import android.graphics.*
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.*

/** Retains the wallpaper display list; parameter edits never allocate rendered Bitmaps. */
@RequiresApi(33)
internal class GpuWallpaperPreview(context: Context) : View(context) {
    private val renderer = GpuWallpaperRenderer(context.resources.configuration.screenWidthDp)
    private var source: Bitmap? = null
    private var settings = DockSettings()
    private var dock = false
    private var reference: Bitmap? = null
    private var detection: DockDetection? = null
    private var showIcons = true
    private var logicalHeight = 960

    fun update(source: Bitmap, settings: DockSettings, dock: Boolean, height: Int,
               reference: Bitmap?, detection: DockDetection?, showIcons: Boolean) {
        this.source = source; this.settings = settings; this.dock = dock
        logicalHeight = height; this.reference = reference; this.detection = detection; this.showIcons = showIcons
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val input = source ?: return
        if (!canvas.isHardwareAccelerated) return
        canvas.save()
        canvas.scale(width / 432f, height.toFloat() / logicalHeight)
        canvas.clipRect(0f, 0f, 432f, logicalHeight.toFloat())
        renderer.draw(canvas, input, 432, logicalHeight, settings, dock)
        if (dock && showIcons) drawIconReferences(canvas, 432, logicalHeight, reference, detection)
        canvas.restore()
    }

    override fun onDetachedFromWindow() {
        renderer.release()
        super.onDetachedFromWindow()
    }
}

internal fun drawIconReferences(canvas: Canvas, width: Int, height: Int, reference: Bitmap?, detection: DockDetection?) {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    detection?.icons?.forEach { r ->
        val dst = RectF(r.left * width, r.top * height, r.right * width, r.bottom * height)
        if (reference == null) {
            paint.color = Color.rgb(20, 220, 130); paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
            canvas.drawRect(dst, paint)
        } else {
            val src = Rect((r.left * reference.width).toInt(), (r.top * reference.height).toInt(),
                (r.right * reference.width).toInt(), (r.bottom * reference.height).toInt())
            canvas.save()
            canvas.clipPath(Path().apply { addRoundRect(dst, dst.width() * .22f, dst.height() * .22f, Path.Direction.CW) })
            canvas.drawBitmap(reference, src, dst, paint)
            canvas.restore()
        }
    }
}

@RequiresApi(33)
internal class GpuWallpaperRenderer(private val screenWidthDp: Int) {
    private val node = RenderNode("wallpaper-material")
    private val shader = RuntimeShader(LIQUID_SHADER)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private var contentKey: List<Any>? = null
    private var effectKey: List<Any>? = null
    internal var contentRecordings = 0
        private set

    fun release() {
        node.discardDisplayList()
        node.setRenderEffect(null)
        contentKey = null
        effectKey = null
    }

    fun draw(canvas: Canvas, source: Bitmap, width: Int, height: Int, settings: DockSettings, dock: Boolean) {
        val scale = max(width.toFloat() / source.width, height.toFloat() / source.height) * settings.zoom
        val left = -(source.width * scale - width) * settings.cropX
        val top = -(source.height * scale - height) * settings.cropY
        val dst = RectF(left, top, left + source.width * scale, top + source.height * scale)
        paint.colorFilter = null; paint.shader = null; paint.alpha = 255
        canvas.drawColor(Color.BLACK)
        canvas.drawBitmap(source, null, dst, paint)
        if (!dock) return
        val rect = RectF(width * (settings.centerX - settings.width / 2), height * (1 - settings.bottom - settings.height),
            width * (settings.centerX + settings.width / 2), height * (1 - settings.bottom))
        val radius = if (settings.liquid) min(rect.width(), rect.height()) * .5f * settings.liquidSettings.cornerRadius
            else rect.height() * settings.radius
        val key = listOf(source, width, height, settings.zoom, settings.cropX, settings.cropY, settings.liquid)
        // HWUI may discard this child node while the window is stopped or a
        // full-screen picker/dialog covers it. Cache keys alone cannot prove validity.
        val contentLost = !node.hasDisplayList()
        if (contentKey != key || contentLost) {
            node.setPosition(0, 0, width, height)
            val recording = node.beginRecording()
            recording.drawColor(Color.BLACK)
            paint.colorFilter = if (settings.liquid) ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) }) else null
            recording.drawBitmap(source, null, dst, paint)
            node.endRecording(); contentKey = key; contentRecordings++
            paint.colorFilter = null
        }
        val nextEffect = listOf(width, height, settings.liquid, settings.liquidSettings, settings.blur,
            settings.width, settings.height, settings.bottom, settings.centerX, settings.radius)
        if (effectKey != nextEffect || contentLost) {
            val blurPx = if (settings.liquid) settings.liquidSettings.blurRadiusDp * width / screenWidthDp.coerceAtLeast(1)
                else settings.blur * width / 1080f
            // RenderScript's kernel uses sigma = 0.4 * radius + 0.6 at its downsampled resolution.
            val factor = min(1f, 384f / width)
            val passes = max(1, ceil((blurPx * factor / 25f).pow(2)).toInt())
            val sigma = if (blurPx > 0) (.4f * blurPx * factor / sqrt(passes.toFloat()) + .6f) * sqrt(passes.toFloat()) / factor else 0f
            // Android's RenderEffect radius is converted by Skia to sigma = radius / sqrt(3) + 0.5.
            val gpuRadius = max(0f, (sigma - .5f) * sqrt(3f))
            val blur = if (gpuRadius > 0) RenderEffect.createBlurEffect(gpuRadius, gpuRadius, Shader.TileMode.CLAMP) else null
            val effect = if (settings.liquid) {
                val optics = settings.liquidSettings
                shader.setFloatUniform("bounds", rect.left, rect.top, rect.right, rect.bottom)
                shader.setFloatUniform("canvasSize", width.toFloat(), height.toFloat())
                shader.setFloatUniform("radius", radius)
                shader.setFloatUniform("bevel", min(rect.width(), rect.height()) * .5f * optics.refractionHeight.coerceIn(0f, 1f))
                shader.setFloatUniform("amount", min(rect.width(), rect.height()) * optics.refractionAmount.coerceIn(0f, 1f))
                shader.setFloatUniform("dispersion", if (optics.chromaticAberration > 0) 1f else 0f)
                shader.setFloatUniform("rimWidth", max(.65f, width / 1080f * 1.5f))
                val lens = RenderEffect.createRuntimeShaderEffect(shader, "content")
                if (blur == null) lens else RenderEffect.createChainEffect(lens, blur)
            } else blur
            node.setRenderEffect(effect); effectKey = nextEffect
        }
        if (settings.liquid) WallpaperRenderer.drawLiquidShadow(canvas, rect, radius, width)
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        canvas.drawRenderNode(node)
        if (!settings.liquid) {
            paint.color = if (settings.dark) Color.rgb(20, 24, 28) else Color.WHITE
            paint.alpha = (settings.opacity * 255).roundToInt()
            canvas.drawRect(rect, paint); paint.alpha = 255
        }
        canvas.restore()
        if (!settings.liquid) WallpaperRenderer.drawDirectionalHighlight(canvas, rect, radius, width, settings.dark)
    }

    companion object {
        // CPU peer: LiquidGlassRenderer. Seven spectral samples retain the upstream channel weights.
        private const val LIQUID_SHADER = """
uniform shader content;
uniform float4 bounds;
uniform float2 canvasSize;
uniform float radius;
uniform float bevel;
uniform float amount;
uniform float dispersion;
uniform float rimWidth;
float2 safeNormalize(float2 v) { return v / max(length(v), 0.00001); }
half3 sampleContent(float2 p) { return content.eval(clamp(p, float2(0.5), canvasSize - 0.5)).rgb; }
half4 main(float2 p) {
    float2 halfSize = (bounds.zw - bounds.xy) * 0.5;
    float2 c = p - (bounds.xy + bounds.zw) * 0.5;
    float r = clamp(radius, 0.0, min(halfSize.x, halfSize.y));
    float2 q = abs(c) - (halfSize - r);
    float sd = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
    float depth = max(-sd, 0.0);
    float gr = min(r * 1.5, min(halfSize.x, halfSize.y));
    float2 gq = abs(c) - (halfSize - gr);
    float2 outside = max(gq, 0.0);
    float2 n = length(outside) > 0.00001 ? sign(c) * safeNormalize(outside)
        : (gq.x >= gq.y ? float2(sign(c.x), 0.0) : float2(0.0, sign(c.y)));
    float profile = 0.0;
    if (bevel > 0.0 && amount > 0.0 && depth < bevel) {
        float t = clamp(1.0 - depth / bevel, 0.0, 1.0);
        profile = 1.0 - sqrt(max(0.0, 1.0 - t * t));
    }
    float2 offset = -profile * amount * safeNormalize(n + safeNormalize(c));
    float d = dispersion * c.x * c.y / (halfSize.x * halfSize.y);
    half3 color;
    if (dispersion == 0.0 || profile == 0.0) {
        color = sampleContent(p + offset);
    } else {
        half3 a = sampleContent(p + offset * (1.0 + d));
        half3 b = sampleContent(p + offset * (1.0 + d * 2.0 / 3.0));
        half3 c1 = sampleContent(p + offset * (1.0 + d / 3.0));
        half3 e = sampleContent(p + offset);
        half3 f = sampleContent(p + offset * (1.0 - d / 3.0));
        half3 g = sampleContent(p + offset * (1.0 - d * 2.0 / 3.0));
        half3 h = sampleContent(p + offset * (1.0 - d));
        color = half3((a.r + b.r + c1.r) / 3.5 + h.r / 7.0,
            b.g / 7.0 + (c1.g + e.g + f.g) / 3.5, (f.b + g.b + h.b) / 3.0);
    }
    float directional = abs((n.x + n.y) * 0.70710677);
    float rim = exp(-0.5 * pow(depth / rimWidth, 2.0));
    float ambient = exp(-depth / (rimWidth * 3.0)) * directional * 0.055;
    return half4(clamp(color * (1.0 - ambient) + rim * directional * 0.5, 0.0, 1.0), 1.0);
}
"""
    }
}
