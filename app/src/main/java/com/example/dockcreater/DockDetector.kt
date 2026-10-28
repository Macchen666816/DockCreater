package com.example.dockcreater

import android.graphics.Bitmap
import android.graphics.RectF
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Size
import org.opencv.core.Core
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

data class DockDetection(val icons: List<RectF>) {
    fun layout(padding: Float): RectF {
        val size = icons.map { it.height() }.sorted()[icons.size / 2]
        val left = icons.minOf { it.left }
        val right = icons.maxOf { it.right }
        val top = icons.minOf { it.top }
        val bottom = icons.maxOf { it.bottom }
        // Use equal pixel padding on both axes despite normalized screen coordinates.
        val xSize = icons.map { it.width() }.sorted()[icons.size / 2]
        val amount = minOf(padding, left / xSize, (1f - right) / xSize, top / size, (1f - bottom) / size)
        val px = xSize * amount
        val py = size * amount
        return RectF(left - px, top - py, right + px, bottom + py)
    }
}

object DockDetector {
    const val DARK_BACKGROUND = 0xFF30343B.toInt()
    const val LIGHT_BACKGROUND = 0xFFE4E7EB.toInt()
    data class Report(val detection: DockDetection?, val candidates: List<RectF>, val message: String,
                      val needsReview: Boolean = false)
    fun detect(bitmap: Bitmap): DockDetection? = analyze(bitmap).detection
    fun analyze(bitmap: Bitmap, background: Int? = null): Report {
        check(OpenCVLoader.initLocal()) { "图像识别组件不可用" }
        val scaled = Bitmap.createScaledBitmap(bitmap, 540, (540f * bitmap.height / bitmap.width).toInt(), true)
        val rgba = Mat(); val gray = Mat(); val edges = Mat(); val hierarchy = Mat()
        val candidates = mutableListOf<RectF>()
        try {
            Utils.bitmapToMat(scaled, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, gray, Size(3.0, 3.0), 0.0)
            // Calibration masks and Canny complement one another. A selected calibration
            // mode is not proof that the screenshot still has the exact reference color.
            val passes = if (background == null) listOf(false to 35.0, false to 70.0, false to 110.0)
                else listOf(true to 22.0, true to 40.0, false to 35.0, false to 70.0)
            for ((calibrationMask, threshold) in passes) {
                if (!calibrationMask) Imgproc.Canny(gray, edges, threshold, threshold * 2)
                else {
                    val knownBackground = checkNotNull(background)
                    val rgb = Mat(); val difference = Mat(); val channels = mutableListOf<Mat>()
                    val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
                    try {
                        Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
                        Core.absdiff(rgb, Scalar(android.graphics.Color.red(knownBackground).toDouble(), android.graphics.Color.green(knownBackground).toDouble(), android.graphics.Color.blue(knownBackground).toDouble()), difference)
                        Core.split(difference, channels)
                        Core.max(channels[0], channels[1], edges); Core.max(edges, channels[2], edges)
                        Imgproc.threshold(edges, edges, threshold, 255.0, Imgproc.THRESH_BINARY)
                        Imgproc.morphologyEx(edges, edges, Imgproc.MORPH_CLOSE, kernel)
                    } finally { rgb.release(); difference.release(); channels.forEach { it.release() }; kernel.release() }
                }
                val contours = mutableListOf<MatOfPoint>()
                try {
                    Imgproc.findContours(edges, contours, hierarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
                    for (contour in contours) {
                        val r = Imgproc.boundingRect(contour)
                        val ratio = r.width.toFloat() / r.height.coerceAtLeast(1)
                        if (r.width !in 30..115 || ratio !in .72f..1.35f || r.y < scaled.height * .75f || r.y + r.height > scaled.height * .98f) continue
                        if (abs(Imgproc.contourArea(contour)) / (r.width * r.height) < .55) continue
                        val box = RectF(r.x.toFloat(), r.y.toFloat(), (r.x + r.width).toFloat(), (r.y + r.height).toFloat())
                        candidates += box
                    }
                } finally { contours.forEach { it.release() } }
            }
            // RETR_LIST includes holes and nested contours. Select the outer box across
            // ALL passes before deduplicating; contour traversal order is not confidence.
            val merged = outerCandidates(candidates)
            val rows = merged.mapNotNull { seed ->
                val row = merged.filter {
                    abs(it.centerY() - seed.centerY()) < seed.height() * .18f &&
                        it.width() / seed.width() in .8f..1.25f &&
                        it.height() / seed.height() in .8f..1.25f
                }.sortedBy { it.left }
                if (row.size !in 3..7 || row.last().right - row.first().left < scaled.width * .55f) return@mapNotNull null
                val gaps = row.zipWithNext { a, b -> b.centerX() - a.centerX() }
                val average = gaps.average()
                if (gaps.any { abs(it - average) > average * .22 } || row.zipWithNext().any { (a, b) -> a.right >= b.left }) return@mapNotNull null
                row
            }
            fun normalized(box: RectF) = RectF(box.left / scaled.width, box.top / scaled.height, box.right / scaled.width, box.bottom / scaled.height)
            val row = rows.maxByOrNull { it.map { box -> box.centerY() }.average() }
            val fallback = fitOuterAnchors(merged, scaled.width.toFloat())
            // Prefer the lower plausible row; never let a complete row above a partial
            // bottom dock silently replace the user's dock.
            val useFallback = fallback != null && (row == null ||
                fallback.map { it.centerY() }.average() > row.map { it.centerY() }.average() + 5 ||
                (abs(fallback.map { it.centerY() }.average() - row.map { it.centerY() }.average()) < 5 &&
                    fallback.last().right - fallback.first().left > row.last().right - row.first().left + scaled.width * .1f))
            val chosen = if (useFallback) fallback else row
            val result = chosen?.let { DockDetection(it.map(::normalized)) }
            val message = when {
                useFallback -> "已用两侧可靠边界估计 Dock；中间漏检或内轮廓已忽略/校正。橙框为估计，确认范围后可保存"
                result != null -> "找到 ${result.icons.size} 个图标，待确认"
                merged.isEmpty() -> "未找到可靠轮廓；可换纯色校准截图，或返回 Dock 位置手动调整"
                else -> "找到 ${merged.size} 个候选（橙框），但不足以确定左右边界；请核对截图/校准模式，或返回手动调整"
            }
            return Report(result, merged.map(::normalized), message, useFallback)
        } finally {
            rgba.release(); gray.release(); edges.release(); hierarchy.release()
            if (scaled !== bitmap) scaled.recycle()
        }
    }

    internal fun outerCandidates(boxes: List<RectF>): List<RectF> {
        val result = mutableListOf<RectF>()
        for (box in boxes.sortedByDescending { it.width() * it.height() }) {
            if (result.none { outer ->
                val intersection = RectF(outer)
                val nested = intersection.intersect(box) && intersection.width() * intersection.height() > box.width() * box.height() * .75f
                nested || (abs(outer.centerX() - box.centerX()) < 12 && abs(outer.centerY() - box.centerY()) < 12)
            }) result += box
        }
        return result.sortedBy { it.left }
    }

    /** Infer only the envelope, not missing icons. Two wide, balanced anchors suffice.
     * Middle candidates are support, not a veto. A single edge cannot establish width. */
    internal fun fitOuterAnchors(boxes: List<RectF>, screenWidth: Float): List<RectF>? {
        data class Fit(val boxes: List<RectF>, val y: Float, val span: Float)
        val fits = mutableListOf<Fit>()
        for (left in boxes) for (right in boxes) {
            val size = (left.width() + right.width()) / 2f
            val h = (left.height() + right.height()) / 2f
            val span = right.right - left.left
            if (right.centerX() <= left.centerX() || left.right >= right.left) continue
            if (span < screenWidth * .65f || left.left > screenWidth * .22f || right.right < screenWidth * .78f) continue
            if (abs(left.left - (screenWidth - right.right)) > screenWidth * .15f) continue
            if (left.width() / right.width() !in .8f..1.25f || left.height() / right.height() !in .8f..1.25f) continue
            if (abs(left.centerY() - right.centerY()) > h * .2f) continue
            val cy = (left.centerY() + right.centerY()) / 2f
            val middle = boxes.filter { it.centerX() > left.right && it.centerX() < right.left &&
                abs(it.centerY() - cy) < h * .3f && it.width() / size in .4f..1.3f && it.height() / h in .4f..1.3f }
            // Score possible launcher grids using observed centers; never create an
            // unobserved icon or use extrapolation to guess the left/right extremes.
            val grid = (3..7).mapNotNull { count ->
                val step = (right.centerX() - left.centerX()) / (count - 1)
                if (step < size * 1.12f || step > size * 2.8f) return@mapNotNull null
                val support = (1 until count - 1).mapNotNull { slot ->
                    val cx = left.centerX() + slot * step
                    middle.filter { abs(it.centerX() - cx) < minOf(size * .3f, step * .18f) }
                        .minByOrNull { abs(it.centerX() - cx) }
                }
                count to support
            }.maxWithOrNull(compareBy<Pair<Int, List<RectF>>> { it.second.size }.thenBy { -it.first })
            val fitted = grid?.second.orEmpty().map { box ->
                RectF(box.centerX() - size / 2, cy - h / 2, box.centerX() + size / 2, cy + h / 2)
            }
            fits += Fit(listOf(left) + fitted + right, cy, span)
        }
        return fits.maxWithOrNull(compareBy<Fit> { it.y }.thenBy { it.span })?.boxes
    }
}
