package com.example.dockcreater

import android.graphics.*
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class DockDetectionTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun calibrationBackgroundAndFailureReport() {
        for (background in listOf(DockDetector.DARK_BACKGROUND, DockDetector.LIGHT_BACKGROUND)) {
            val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
            image.eraseColor(background)
            val empty = DockDetector.analyze(image, background)
            assertNull(empty.detection)
            assertTrue(empty.candidates.isEmpty())
            assertTrue(empty.message.contains("未找到"))
            val canvas = Canvas(image)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(180, 70, 110) }
            for (i in 0..4) {
                val x = 44f + i * 208f
                canvas.drawRoundRect(RectF(x, 2130f, x + 160f, 2290f), 36f, 36f, paint)
            }
            val report = DockDetector.analyze(image, background)
            assertNotNull(report.message, report.detection)
            assertEquals(5, report.detection!!.icons.size)
            image.recycle()
        }
    }

    @Test fun calibrationPersistsAndIsResolutionSpecific() {
        val icons = (0..4).map { RectF(.04f + it * .19f, .89f, .19f + it * .19f, .95f) }
        try {
            CalibrationStore.save(context, 1001, 2001, DockDetection(icons), .3f)
            val restored = CalibrationStore.load(context, 1001, 2001)
            assertNotNull(restored)
            assertEquals(icons, restored!!.first.icons)
            assertEquals(.3f, restored.second, .001f)
            assertNull(CalibrationStore.load(context, 1002, 2001))
        } finally {
            context.getSharedPreferences("dock_calibration", 0).edit().remove("1001x2001").remove("padding_1001x2001").commit()
        }
    }

    @Test fun nestedLensCannotDisplaceOuterIconAndPartialRowStillProducesEnvelope() {
        val outer = RectF(230f, 1065f, 310f, 1145f)
        val inner = RectF(250f, 1085f, 290f, 1125f)
        val merged = DockDetector.outerCandidates(listOf(inner, outer, RectF(outer)))
        assertEquals(listOf(outer), merged)
        // Exactly the failure pattern: reliable end icons, folders and a small camera lens.
        val boxes = listOf(RectF(22f, 1065f, 102f, 1145f), RectF(438f, 1065f, 518f, 1145f),
            RectF(126f, 1065f, 206f, 1145f), inner, RectF(334f, 1065f, 414f, 1145f))
        val fitted = checkNotNull(DockDetector.fitOuterAnchors(boxes, 540f))
        assertEquals(5, fitted.size)
        assertEquals(80f, fitted[2].width(), .01f)
        assertEquals(1065f, fitted[2].top, .01f)
        val endpoints = checkNotNull(DockDetector.fitOuterAnchors(boxes.take(2), 540f))
        assertEquals("Do not invent missing icons", 2, endpoints.size)
        assertEquals(22f, endpoints.first().left, .01f)
        assertNull(DockDetector.fitOuterAnchors(boxes.take(1), 540f))
        assertNull(DockDetector.fitOuterAnchors(listOf(boxes[0], RectF(438f, 950f, 518f, 1030f)), 540f))
    }

    @Test fun syntheticAnnotatedDesktopReturnsUsableBounds() {
        val image = syntheticDesktop()
        try {
            for (background in listOf<Int?>(null, DockDetector.DARK_BACKGROUND)) {
                val report = DockDetector.analyze(image, background)
                val result = checkNotNull(report.detection) { report.message }
                val bounds = result.layout(.2f)
                assertTrue(bounds.left < .07f && bounds.right > .93f)
                assertTrue(bounds.top > .84f && bounds.bottom > .94f)
                assertEquals(5, result.icons.size)
            }
        } finally { image.recycle() }
    }

    private fun syntheticDesktop(): Bitmap {
        val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(DockDetector.DARK_BACKGROUND)
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(540f, 870f, 920f,
                intArrayOf(Color.rgb(46, 105, 123), Color.rgb(48, 52, 59)), null, Shader.TileMode.CLAMP)
        }
        canvas.drawRect(0f, 0f, 1080f, 2000f, glow)
        val colors = intArrayOf(Color.rgb(235, 107, 87), Color.rgb(237, 174, 74),
            Color.rgb(74, 176, 137), Color.rgb(76, 143, 222), Color.rgb(166, 119, 211))
        for (i in colors.indices) {
            val x = 44f + i * 208f
            val box = RectF(x, 2130f, x + 160f, 2290f)
            val tile = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colors[i] }
            canvas.drawRoundRect(box, 36f, 36f, tile)
            val detail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                alpha = 190
                style = Paint.Style.STROKE
                strokeWidth = 8f
            }
            canvas.drawCircle(box.centerX(), box.centerY(), 35f, detail)
        }
        glow.shader = null
        return image
    }

    @Test fun missingMiddleIconsAreConfirmableAndTwoAnchorCalibrationRoundTrips() {
        val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        image.eraseColor(DockDetector.DARK_BACKGROUND)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val canvas = Canvas(image)
        for (x in listOf(44f, 876f)) canvas.drawRoundRect(RectF(x, 2130f, x + 160, 2290f), 36f, 36f, paint)
        try {
            val report = DockDetector.analyze(image, DockDetector.DARK_BACKGROUND)
            val result = checkNotNull(report.detection) { report.message }
            assertTrue(report.needsReview)
            assertEquals(2, result.icons.size)
            CalibrationStore.save(context, 1003, 2003, result, .2f)
            val restored = checkNotNull(CalibrationStore.load(context, 1003, 2003))
            assertEquals(result.icons, restored.first.icons)
            val bounds = result.layout(.2f)
            assertTrue(bounds.left < result.icons.first().left && bounds.right > result.icons.last().right)
            // A single anchor must remain visible as a candidate, without fabricated width.
            image.eraseColor(DockDetector.DARK_BACKGROUND)
            canvas.drawRoundRect(RectF(44f, 2130f, 204f, 2290f), 36f, 36f, paint)
            val single = DockDetector.analyze(image, DockDetector.DARK_BACKGROUND)
            assertNull(single.detection)
            assertTrue(single.candidates.isNotEmpty())
        } finally {
            image.recycle()
            context.getSharedPreferences("dock_calibration", 0).edit().remove("1003x2003").remove("padding_1003x2003").commit()
        }
    }

    @Test fun wrongCalibrationColorFallsBackToEdgesAndPrefersLowerPartialRow() {
        val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        image.eraseColor(Color.rgb(62, 67, 75))
        val canvas = Canvas(image)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        for (i in 0..4) {
            val x = 44f + i * 208f
            canvas.drawRoundRect(RectF(x, 1840f, x + 160f, 2000f), 36f, 36f, paint)
        }
        for (x in listOf(44f, 876f)) canvas.drawRoundRect(RectF(x, 2130f, x + 160f, 2290f), 36f, 36f, paint)
        try {
            val report = DockDetector.analyze(image, DockDetector.DARK_BACKGROUND)
            val result = checkNotNull(report.detection) { report.message }
            assertTrue(report.needsReview)
            assertEquals(2, result.icons.size)
            assertEquals(2130f / 2400, result.icons.first().top, .004f)
        } finally { image.recycle() }
    }

    @Test fun detectsBottomIconRowAndPadsItsBounds() {
        val image = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(image)
        canvas.drawColor(Color.rgb(30, 42, 52))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        for (y in listOf(1750f, 2130f)) for (i in 0..4) {
            paint.color = listOf(Color.GREEN, Color.YELLOW, Color.CYAN, Color.WHITE, Color.MAGENTA)[i]
            val x = 44f + i * 208f
            canvas.drawRoundRect(RectF(x, y, x + 160f, y + 160f), 36f, 36f, paint)
        }
        val result = DockDetector.detect(image)
        assertNotNull(result)
        assertEquals(5, result!!.icons.size)
        assertEquals(2130f / 2400f, result.icons.first().top, .004f)
        val rect = result.layout(.2f)
        assertTrue(rect.left >= 0f && rect.right <= 1f)
        assertEquals((result.icons.first().left - rect.left) * 1080, (result.icons.first().top - rect.top) * 2400, 3f)
        image.eraseColor(Color.GRAY)
        assertNull(DockDetector.detect(image))
        image.recycle()
    }

    @Test fun liquidRefractsBackgroundAndKeepsOutsideUnchanged() {
        val source = Bitmap.createBitmap(1080, 2400, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(source)
        val paint = Paint()
        canvas.drawColor(Color.rgb(72, 132, 140))
        for (x in 0 until 1080 step 36) {
            paint.color = if (x % 72 == 0) Color.rgb(209, 220, 208) else Color.rgb(167, 112, 133)
            canvas.drawRect(x.toFloat(), 0f, x + 18f, 2400f, paint)
        }
        val settings = DockSettings(liquid = true, blur = 0f, opacity = .06f)
        val zero = WallpaperRenderer.render(context, source, 1080, 2400, settings.copy(liquidSettings = LiquidGlassSettings(refractionAmount = 0f)), true)
        val liquid = WallpaperRenderer.render(context, source, 1080, 2400, settings.copy(liquidSettings = LiquidGlassSettings(refractionAmount = 1f)), true)
        var changes = 0
        for (y in 0 until 2400 step 3) for (x in 0 until 1080 step 3) {
            // A narrow drop shadow is intentional. Pixels well outside the glass must remain untouched.
            if (y < 2080 || y >= 2344 || x < 25 || x >= 1055) assertEquals(source.getPixel(x, y), liquid.getPixel(x, y))
            if (zero.getPixel(x, y) != liquid.getPixel(x, y)) changes++
        }
        assertTrue(changes > 300)
        File(context.cacheDir, "liquid-test.png").outputStream().use { liquid.compress(Bitmap.CompressFormat.PNG, 100, it) }
        source.recycle(); zero.recycle(); liquid.recycle()
    }
}
