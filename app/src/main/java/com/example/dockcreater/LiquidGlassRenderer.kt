/*
 * Copyright 2025 Kyant
 * Copyright 2026 DockCreater contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * CPU adaptation of Backdrop's RoundedRectRefractionWithDispersionShaderString,
 * RoundedRectSDF and Default/AmbientHighlightShaderString by Kyant (2.0.1).
 * Modified for static Bitmap export, bilinear sampling and DockSettings controls.
 */
package com.example.dockcreater

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import kotlin.math.*

internal object LiquidGlassRenderer {
    fun render(source: Bitmap, width: Int, height: Int, rect: RectF, radius: Float, settings: DockSettings): Bitmap {
        val left = floor(rect.left).toInt()
        val top = floor(rect.top).toInt()
        val w = ceil(rect.right).toInt() - left
        val h = ceil(rect.bottom).toInt() - top
        val pixels = IntArray(source.width * source.height)
        source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
        val output = IntArray(w * h)
        val halfW = rect.width() * .5f
        val halfH = rect.height() * .5f
        val r = radius.coerceIn(0f, min(halfW, halfH))
        val gradRadius = min(r * 1.5f, min(halfW, halfH))
        val optics = settings.liquidSettings
        val minDimension = min(rect.width(), rect.height())
        val bevel = minDimension * .5f * optics.refractionHeight.coerceIn(0f, 1f)
        val amount = minDimension * optics.refractionAmount.coerceIn(0f, 1f)
        val aberration = if (optics.chromaticAberration > 0f) 1f else 0f
        val highlightWidth = max(.65f, width / 1080f * 1.5f)
        val gxgy = FloatArray(2)
        val samples = FloatArray(21)

        fun sample(x: Float, y: Float, slot: Int) {
            val sx = (x * source.width / width - .5f).coerceIn(0f, source.width - 1f)
            val sy = (y * source.height / height - .5f).coerceIn(0f, source.height - 1f)
            val ix = sx.toInt(); val iy = sy.toInt()
            val fx = sx - ix; val fy = sy - iy
            val c00 = pixels[iy * source.width + ix]
            val c10 = pixels[iy * source.width + min(ix + 1, source.width - 1)]
            val c01 = pixels[min(iy + 1, source.height - 1) * source.width + ix]
            val c11 = pixels[min(iy + 1, source.height - 1) * source.width + min(ix + 1, source.width - 1)]
            for (channel in 0..2) {
                val shift = 16 - channel * 8
                val a = ((c00 shr shift) and 255) * (1f - fx) + ((c10 shr shift) and 255) * fx
                val b = ((c01 shr shift) and 255) * (1f - fx) + ((c11 shr shift) and 255) * fx
                samples[slot * 3 + channel] = a * (1f - fy) + b * fy
            }
        }

        for (y in 0 until h) for (x in 0 until w) {
            val px = left + x + .5f
            val py = top + y + .5f
            val cx = px - rect.centerX()
            val cy = py - rect.centerY()
            val qx = abs(cx) - (halfW - r)
            val qy = abs(cy) - (halfH - r)
            val sd = hypot(max(qx, 0f), max(qy, 0f)) + min(max(qx, qy), 0f) - r
            if (sd > 1f) continue
            val depth = max(-sd, 0f)
            gradient(cx, cy, halfW, halfH, gradRadius, gxgy)
            val nx = gxgy[0]; val ny = gxgy[1]
            // Upstream depthEffect = true: bend normals towards the radial direction.
            val radialLength = hypot(cx, cy).coerceAtLeast(.00001f)
            val dx = nx + cx / radialLength
            val dy = ny + cy / radialLength
            val length = hypot(dx, dy).coerceAtLeast(.00001f)
            val profile = if (bevel <= 0f || amount <= 0f || depth >= bevel) 0f else circleMap(1f - depth / bevel)
            // Lens.kt passes a negative refractionAmount to sample towards the interior.
            val displacement = -profile * amount
            val offsetX = displacement * dx / length
            val offsetY = displacement * dy / length
            val dispersion = aberration * cx * cy / (halfW * halfH)
            var red: Float
            var green: Float
            var blue: Float
            if (displacement == 0f || aberration == 0f) {
                sample(px + offsetX, py + offsetY, 0)
                red = samples[0]; green = samples[1]; blue = samples[2]
            } else {
                // Seven spectral taps, with exactly the channel weights used upstream.
                for (i in 0..6) {
                    val spectralOffset = 1f - i / 3f
                    sample(px + offsetX * (1f + dispersion * spectralOffset),
                        py + offsetY * (1f + dispersion * spectralOffset), i)
                }
                red = (samples[0] + samples[3] + samples[6]) / 3.5f + samples[18] / 7f
                green = samples[4] / 7f + (samples[7] + samples[10] + samples[13]) / 3.5f
                blue = (samples[14] + samples[17] + samples[20]) / 3f
            }
            // Backdrop's 45-degree normal lighting, confined to a soft outer rim.
            val lightDot = (nx + ny) * .70710677f
            val directional = abs(lightDot)
            val rim = exp(-.5f * (depth / highlightWidth).pow(2))
            val reflection = rim * directional * .50f
            val ambient = exp(-depth / (highlightWidth * 3f)) * directional * .055f
            fun lit(value: Float): Int = (value * (1f - ambient) + 255f * reflection)
                .roundToInt().coerceIn(0, 255)
            output[y * w + x] = Color.rgb(lit(red), lit(green), lit(blue))
        }
        return Bitmap.createBitmap(output, w, h, Bitmap.Config.ARGB_8888)
    }

    internal fun circleMap(x: Float): Float {
        val t = x.coerceIn(0f, 1f)
        return 1f - sqrt(max(0f, 1f - t * t))
    }

    private fun gradient(x: Float, y: Float, halfW: Float, halfH: Float, radius: Float, out: FloatArray) {
        val qx = abs(x) - (halfW - radius)
        val qy = abs(y) - (halfH - radius)
        val ox = max(qx, 0f); val oy = max(qy, 0f)
        val length = hypot(ox, oy)
        if (length > .00001f) {
            out[0] = sign(x) * ox / length
            out[1] = sign(y) * oy / length
        } else if (qx >= qy) {
            out[0] = sign(x); out[1] = 0f
        } else {
            out[0] = 0f; out[1] = sign(y)
        }
    }
}
