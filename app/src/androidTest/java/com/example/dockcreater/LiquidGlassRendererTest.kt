package com.example.dockcreater

import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.*

class LiquidGlassRendererTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun pattern(w: Int, h: Int): Bitmap {
        val pixels = IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            Color.rgb((128 + 90 * sin(x / 18.0)).toInt(),
                (128 + 90 * sin(y / 22.0)).toInt(), (128 + 90 * sin((x + y) / 27.0)).toInt())
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    @Test fun centerIsUndistortedAndDispersionOnlyChangesBevel() {
        val source = pattern(400, 400)
        val rect = RectF(40f, 160f, 360f, 280f)
        val settings = DockSettings(liquid = true, liquidSettings = LiquidGlassSettings(refractionAmount = .26f, refractionHeight = .4f))
        val plain = LiquidGlassRenderer.render(source, 400, 400, rect, 40f, settings)
        val chromatic = LiquidGlassRenderer.render(source, 400, 400, rect, 40f, settings.copy(liquidSettings = settings.liquidSettings.copy(chromaticAberration = 1f)))
        for (y in 200..239) for (x in 100..299) {
            assertEquals(source.getPixel(x, y), chromatic.getPixel(x - 40, y - 160))
            assertEquals(plain.getPixel(x - 40, y - 160), chromatic.getPixel(x - 40, y - 160))
        }
        var changes = 0
        for (y in 1..22) for (x in 50..269) {
            if (plain.getPixel(x, y) != chromatic.getPixel(x, y)) changes++
        }
        assertTrue("Dispersion must affect the bevel", changes > 1000)
        source.recycle(); plain.recycle(); chromatic.recycle()
    }

    @Test fun cpuPortMatchesUpstreamGpuShader() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        val source = pattern(400, 400)
        val rect = RectF(40f, 160f, 360f, 280f)
        val settings = DockSettings(liquid = true, liquidSettings = LiquidGlassSettings(refractionAmount = .22f, chromaticAberration = .55f, refractionHeight = .4f))
        val cpu = LiquidGlassRenderer.render(source, 400, 400, rect, 40f, settings)
        val gpu = referenceShader(source, rect, settings)
        var worst = 0
        var total = 0L
        var count = 0
        // Exclude the separately composited soft highlight, compare the optical bevel.
        for (y in 7..21) for (x in 60..259) for (shift in listOf(0, 8, 16)) {
            val delta = abs(((cpu.getPixel(x, y) shr shift) and 255) - ((gpu.getPixel(x, y) shr shift) and 255))
            worst = max(worst, delta); total += delta; count++
        }
        assertTrue("GPU/CPU mean delta ${total.toFloat() / count}, max $worst", total.toFloat() / count < 2f && worst <= 6)
        source.recycle(); cpu.recycle(); gpu.recycle()
    }

    @androidx.annotation.RequiresApi(33)
    private fun referenceShader(source: Bitmap, rect: RectF, settings: DockSettings): Bitmap {
        val shader = RuntimeShader(instrumentation.context.assets.open("backdrop-reference.agsl").bufferedReader().use { it.readText() })
        val sampler = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
            setLocalMatrix(Matrix().apply { setTranslate(-rect.left, -rect.top) })
            setFilterMode(BitmapShader.FILTER_MODE_LINEAR)
        }
        shader.setInputShader("content", sampler)
        shader.setFloatUniform("size", rect.width(), rect.height())
        shader.setFloatUniform("cornerRadii", 40f, 40f, 40f, 40f)
        shader.setFloatUniform("refractionHeight", rect.height() * .5f * settings.liquidSettings.refractionHeight)
        shader.setFloatUniform("refractionAmount", -rect.height() * settings.liquidSettings.refractionAmount)
        shader.setFloatUniform("depthEffect", 1f)
        shader.setFloatUniform("chromaticAberration", if (settings.liquidSettings.chromaticAberration > 0f) 1f else 0f)
        val w = rect.width().toInt(); val h = rect.height().toInt()
        val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        val handlerThread = HandlerThread("reference-shader").apply { start() }
        val ready = CountDownLatch(1)
        reader.setOnImageAvailableListener({ ready.countDown() }, Handler(handlerThread.looper))
        val renderer = HardwareRenderer()
        val node = RenderNode("upstream-reference").apply { setPosition(0, 0, w, h) }
        try {
            val canvas = node.beginRecording()
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply { this.shader = shader })
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            assertTrue("Reference GPU frame timed out", ready.await(5, TimeUnit.SECONDS))
            reader.acquireLatestImage().use { frame ->
                val buffer = checkNotNull(frame.hardwareBuffer)
                try {
                    val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)))
                    return hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
                } finally { buffer.close() }
            }
        } finally {
            renderer.destroy(); reader.close(); handlerThread.quitSafely()
        }
    }

    @Test fun opticalExtremesStayOpaqueAndExportRoundTrips() {
        val source = pattern(540, 1200)
        for (radius in listOf(0f, .5f)) {
            val settings = DockSettings(liquid = true, width = 1f, centerX = .5f, bottom = 0f,
                liquidSettings = LiquidGlassSettings(cornerRadius = radius * 2f, refractionAmount = 1f, chromaticAberration = 1f, refractionHeight = 1f))
            val result = WallpaperRenderer.render(context, source, 540, 1200, settings, true)
            for (y in 1080 until 1200) for (x in 0 until 540) assertEquals(255, Color.alpha(result.getPixel(x, y)))
            val uri = WallpaperRenderer.save(context, result, "Glass_test")
            try {
                val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
                assertEquals(540, decoded.width); assertEquals(1200, decoded.height)
                assertTrue(result.sameAs(decoded))
                decoded.recycle()
            } finally { context.contentResolver.delete(uri, null, null); result.recycle() }
        }
        source.recycle()
    }

    @Test fun frostedOutputIsIndependentOfLiquidOptics() {
        val source = pattern(432, 960)
        val settings = DockSettings(liquid = false)
        val before = WallpaperRenderer.render(context, source, 432, 960, settings, true)
        val after = WallpaperRenderer.render(context, source, 432, 960,
            settings.copy(liquidSettings = LiquidGlassSettings(refractionAmount = 1f, chromaticAberration = 1f, refractionHeight = 1f)), true)
        assertTrue("Liquid controls must not leak into frosted glass", before.sameAs(after))
        source.recycle(); before.recycle(); after.recycle()
    }

    @Test fun playgroundZeroHeightDisablesLensAndPositiveAberrationIsBoolean() {
        val source = pattern(400, 400)
        val rect = RectF(40f, 160f, 360f, 280f)
        val settings = DockSettings(liquid = true)
        fun render(optics: LiquidGlassSettings) = LiquidGlassRenderer.render(source, 400, 400, rect, 40f, settings.copy(liquidSettings = optics))
        val noHeight = render(LiquidGlassSettings(refractionHeight = 0f))
        val noAmount = render(LiquidGlassSettings(refractionAmount = 0f))
        assertTrue(noHeight.sameAs(noAmount))
        val low = render(LiquidGlassSettings(chromaticAberration = .01f))
        val high = render(LiquidGlassSettings(chromaticAberration = 1f))
        assertTrue("Match Playground's > 0 switch", low.sameAs(high))
        source.recycle(); noHeight.recycle(); noAmount.recycle(); low.recycle(); high.recycle()
    }

    @Test fun playgroundBlurAndRadiusControlsAffectExport() {
        val source = pattern(432, 960)
        val settings = DockSettings(liquid = true)
        val baseline = WallpaperRenderer.render(context, source, 432, 960, settings, true)
        val blurred = WallpaperRenderer.render(context, source, 432, 960, settings.copy(liquidSettings = LiquidGlassSettings(blurRadiusDp = 32f)), true)
        val square = WallpaperRenderer.render(context, source, 432, 960, settings.copy(liquidSettings = LiquidGlassSettings(cornerRadius = 0f)), true)
        assertFalse(baseline.sameAs(blurred))
        assertFalse(baseline.sameAs(square))
        assertEquals(432, blurred.width); assertEquals(960, blurred.height)
        source.recycle(); baseline.recycle(); blurred.recycle(); square.recycle()
    }

    @Test fun renderPreviewAndDeviceSizeAcceptanceArtifacts() {
        val source = pattern(1080, 2400)
        val settings = DockSettings(liquid = true)
        for (w in listOf(432, 1080)) {
            val result = WallpaperRenderer.render(context, source, w, w * 2400 / 1080, settings, true)
            File(context.cacheDir, "liquid-acceptance-$w.png").outputStream().use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
            result.recycle()
        }
        source.recycle()
    }
}
