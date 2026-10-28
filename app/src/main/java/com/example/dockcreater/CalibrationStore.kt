package com.example.dockcreater

import android.content.Context
import android.graphics.RectF
import org.json.JSONArray

object CalibrationStore {
    fun save(context: Context, width: Int, height: Int, result: DockDetection, padding: Float) {
        val boxes = JSONArray()
        result.icons.forEach { boxes.put(JSONArray(listOf(it.left, it.top, it.right, it.bottom))) }
        context.getSharedPreferences("dock_calibration", Context.MODE_PRIVATE).edit()
            .putString("${width}x${height}", boxes.toString()).putFloat("padding_${width}x${height}", padding).apply()
    }
    fun load(context: Context, width: Int, height: Int): Pair<DockDetection, Float>? = runCatching {
        val prefs = context.getSharedPreferences("dock_calibration", Context.MODE_PRIVATE)
        val json = prefs.getString("${width}x${height}", null) ?: return null
        val array = JSONArray(json)
        require(array.length() in 2..7)
        val icons = (0 until array.length()).map { index ->
            val box = array.getJSONArray(index)
            RectF(box.getDouble(0).toFloat(), box.getDouble(1).toFloat(), box.getDouble(2).toFloat(), box.getDouble(3).toFloat()).also {
                require(it.left >= 0 && it.top >= 0 && it.right <= 1 && it.bottom <= 1 && it.width() > 0 && it.height() > 0)
            }
        }
        DockDetection(icons) to prefs.getFloat("padding_${width}x${height}", .2f).coerceIn(.05f, .45f)
    }.getOrNull()
}
