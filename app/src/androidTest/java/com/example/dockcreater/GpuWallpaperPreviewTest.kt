package com.example.dockcreater

import android.graphics.*
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresApi
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.*

class GpuWallpaperPreviewTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun gpuPreviewMatchesCpuMaterialAndReusesWallpaperAcrossParameterEdits() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        compareAndReuse()
    }

    @RequiresApi(33)
    private fun compareAndReuse() {
        val w = 432; val h = 960
        val source = Bitmap.createBitmap(IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            Color.rgb((120 + 80 * sin(x / 13.0)).toInt(), (130 + 70 * cos(y / 17.0)).toInt(),
                (120 + 80 * sin((x + y) / 21.0)).toInt())
        }, w, h, Bitmap.Config.ARGB_8888)
        val gpuRenderer = GpuWallpaperRenderer(context.resources.configuration.screenWidthDp)
        val session = CaptureSession(w, h)
        val baseline = DockSettings(liquid = true, height = .17f,
            liquidSettings = LiquidGlassSettings(refractionHeight = .6f, refractionAmount = .7f, chromaticAberration = 1f))
        val cases = listOf(baseline,
            baseline.copy(liquidSettings = baseline.liquidSettings.copy(blurRadiusDp = 20f)),
            baseline.copy(liquidSettings = baseline.liquidSettings.copy(cornerRadius = 0f, refractionHeight = 0f)),
            baseline.copy(width = 1f, bottom = 0f, liquidSettings = LiquidGlassSettings(
                cornerRadius = 1f, refractionHeight = 1f, refractionAmount = 1f, chromaticAberration = 1f)),
            baseline.copy(liquid = false, dark = true, blur = 28f),
            baseline.copy(liquid = false, dark = false, blur = 0f))
        try {
            for ((index, settings) in cases.withIndex()) {
                val cpu = WallpaperRenderer.render(context, source, w, h, settings, true)
                val gpu = session.capture { gpuRenderer.draw(it, source, w, h, settings, true) }
                java.io.File(context.cacheDir, "gpu-case-$index.png").outputStream().use { gpu.compress(Bitmap.CompressFormat.PNG, 100, it) }
                java.io.File(context.cacheDir, "cpu-case-$index.png").outputStream().use { cpu.compress(Bitmap.CompressFormat.PNG, 100, it) }
                try {
                    var total = 0L; var count = 0; var worst = 0
                    // Inside the shape: AA and shadow kernels have separate platform rasterization.
                    for (y in 770..915) for (x in 55..376) {
                        val halfW = w * settings.width / 2f
                        val halfH = h * settings.height / 2f
                        val radius = if (settings.liquid) min(halfW, halfH) * settings.liquidSettings.cornerRadius
                            else halfH * 2f * settings.radius
                        val qx = abs(x + .5f - w * settings.centerX) - (halfW - radius)
                        val qy = abs(y + .5f - h * (1 - settings.bottom - settings.height / 2)) - (halfH - radius)
                        val sd = hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - radius
                        if (sd > -3f) continue
                        for (shift in listOf(0, 8, 16)) {
                            val d = abs(((cpu.getPixel(x, y) shr shift) and 255) - ((gpu.getPixel(x, y) shr shift) and 255))
                            total += d; count++; worst = max(worst, d)
                        }
                    }
                    val mean = total.toFloat() / count
                    println("GPU case $index: mean=$mean max=$worst")
                    assertTrue("GPU case $index mean=$mean max=$worst", mean < 3f && worst <= 12)
                    assertEquals("Opaque wallpaper", 255, Color.alpha(gpu.getPixel(200, 850)))
                    assertEquals("Unchanged wallpaper outside glass", cpu.getPixel(200, 400), gpu.getPixel(200, 400))
                } finally { cpu.recycle(); gpu.recycle() }
            }
            // HWUI can discard a node's display list while the Activity is stopped,
            // independently of our Kotlin cache keys. Reproduce that loss explicitly.
            val expected = session.capture { gpuRenderer.draw(it, source, w, h, baseline, true) }
            val recordingsBeforeLoss = gpuRenderer.contentRecordings
            val field = GpuWallpaperRenderer::class.java.getDeclaredField("node").apply { isAccessible = true }
            (field.get(gpuRenderer) as RenderNode).discardDisplayList()
            val restored = session.capture { gpuRenderer.draw(it, source, w, h, baseline, true) }
            try {
                assertEquals("Lost HWUI display list must be recorded again", recordingsBeforeLoss + 1, gpuRenderer.contentRecordings)
                assertTrue("Restored material must match before resource loss", expected.sameAs(restored))
            } finally { expected.recycle(); restored.recycle() }
            gpuRenderer.release()
            val reattached = session.capture { gpuRenderer.draw(it, source, w, h, baseline, true) }
            val fresh = GpuWallpaperRenderer(context.resources.configuration.screenWidthDp)
            val freshImage = session.capture { fresh.draw(it, source, w, h, baseline, true) }
            try { assertTrue("Detach/reattach must restore the material", reattached.sameAs(freshImage)) }
            finally { reattached.recycle(); freshImage.recycle(); fresh.release() }
            // Restore the frosted input used by the existing reuse check; switching
            // material modes intentionally records a different saturation filter.
            session.capture { gpuRenderer.draw(it, source, w, h, cases.last(), true) }.recycle()
            val records = gpuRenderer.contentRecordings
            val start = System.nanoTime()
            session.capture { canvas ->
                repeat(90) { i ->
                    gpuRenderer.draw(canvas, source, w, h,
                        baseline.copy(liquid = false, opacity = i / 100f, blur = i / 2f), true)
                }
            }.recycle()
            assertEquals("Material edits must reuse wallpaper display list", records, gpuRenderer.contentRecordings)
            println("90 parameter updates + one GPU submission: ${(System.nanoTime() - start) / 1e6} ms")
            session.capture { gpuRenderer.draw(it, source, w, h, baseline, true) }.recycle()
            val liquidRecords = gpuRenderer.contentRecordings
            repeat(20) { i ->
                session.capture { canvas -> gpuRenderer.draw(canvas, source, w, h,
                    baseline.copy(liquidSettings = baseline.liquidSettings.copy(refractionAmount = i / 20f)), true) }.recycle()
            }
            assertEquals("Live lens frames reuse wallpaper", liquidRecords, gpuRenderer.contentRecordings)
        } finally { session.close(); gpuRenderer.release(); source.recycle() }
    }

    @RequiresApi(33)
    private class CaptureSession(w: Int, h: Int) {
        private val reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT)
        private val thread = HandlerThread("gpu-wallpaper-test").apply { start() }
        private val renderer = HardwareRenderer()
        private val root = RenderNode("gpu-preview-test").apply { setPosition(0, 0, w, h) }
        init { renderer.setSurface(reader.surface); renderer.setContentRoot(root) }

        fun capture(draw: (Canvas) -> Unit): Bitmap {
            val ready = CountDownLatch(1)
            reader.setOnImageAvailableListener({ ready.countDown() }, Handler(thread.looper))
            draw(root.beginRecording()); root.endRecording()
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            assertTrue("GPU frame timed out", ready.await(5, TimeUnit.SECONDS))
            reader.acquireLatestImage().use { image ->
                val buffer = checkNotNull(image.hardwareBuffer)
                try {
                    val hardware = checkNotNull(Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB)))
                    return hardware.copy(Bitmap.Config.ARGB_8888, false).also { hardware.recycle() }
                } finally { buffer.close() }
            }
        }
        fun close() { renderer.destroy(); reader.close(); thread.quitSafely() }
    }
}
