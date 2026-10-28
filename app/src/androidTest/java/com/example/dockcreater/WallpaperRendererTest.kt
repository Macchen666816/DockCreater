package com.example.dockcreater

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.ImageDecoder
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class WallpaperRendererTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun cropCoversCanvasAndDockChangesOnlyItsRegion() {
        val source = Bitmap.createBitmap(300, 200, Bitmap.Config.ARGB_8888)
        for (y in 0 until 200) for (x in 0 until 300) {
            source.setPixel(x, y, if ((x / 4 + y / 4) % 2 == 0) Color.RED else Color.BLUE)
        }
        val plain = WallpaperRenderer.render(context, source, 108, 240, DockSettings(), false)
        val dock = WallpaperRenderer.render(context, source, 108, 240, DockSettings(), true)
        assertEquals(108, dock.width)
        assertEquals(240, dock.height)
        var changed = 0
        for (y in 0 until 240) for (x in 0 until 108) {
            assertEquals(255, Color.alpha(plain.getPixel(x, y)))
            if (y < 211 || y >= 232 || x < 5 || x >= 103) assertEquals(plain.getPixel(x, y), dock.getPixel(x, y))
            if (plain.getPixel(x, y) != dock.getPixel(x, y)) changed++
        }
        assertTrue(changed > 500)
        source.recycle(); plain.recycle(); dock.recycle()
    }

    @Test fun exportRoundTripsAtDeviceResolution() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(70, 130, 170)) }
        val rendered = WallpaperRenderer.render(context, source, 1080, 2400, DockSettings(), true)
        val uri = WallpaperRenderer.save(context, rendered)
        try {
            val decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, _, _ -> decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE }
            assertEquals(1080, decoded.width)
            assertEquals(2400, decoded.height)
            assertEquals(rendered.getPixel(540, 2200), decoded.getPixel(540, 2200))
            decoded.recycle()
        } finally { context.contentResolver.delete(uri, null, null); source.recycle(); rendered.recycle() }
    }
}
