package com.example.dockcreater

import android.app.Activity
import android.graphics.Rect
import kotlin.math.max
import kotlin.math.min

data class ScreenProfile(
    val width: Int,
    val height: Int,
    val ratio: Float
) {
    val label: String get() = "$width × $height · ${"%.3f".format(ratio)}"

    companion object {
        /**
         * maximumWindowMetrics follows the logical display size, including a user's
         * resolution override, which is the same coordinate system used by wallpaper.
         * Normalize it to portrait so calibration keys stay stable across rotation.
         */
        fun from(activity: Activity): ScreenProfile {
            val bounds: Rect = activity.windowManager.maximumWindowMetrics.bounds
            val portraitWidth = min(bounds.width(), bounds.height())
            val portraitHeight = max(bounds.width(), bounds.height())
            return ScreenProfile(portraitWidth, portraitHeight, portraitWidth.toFloat() / portraitHeight)
        }
    }
}
