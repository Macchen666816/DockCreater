package com.example.dockcreater

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun CalibrationDialog(width: Int, height: Int, backgroundImage: Bitmap? = null, onDismiss: () -> Unit, onConfirm: (DockDetection, Bitmap) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var uri by rememberSaveable { mutableStateOf<String?>(null) }
    var request by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var reference by remember { mutableStateOf<Bitmap?>(null) }
    var annotated by remember { mutableStateOf<Bitmap?>(null) }
    var report by remember { mutableStateOf<DockDetector.Report?>(null) }
    var message by remember { mutableStateOf("") }
    val background = when (mode) { 1 -> DockDetector.DARK_BACKGROUND; 2 -> DockDetector.LIGHT_BACKGROUND; else -> null }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { selected ->
        if (selected != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(selected, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            uri = selected.toString(); request++
        }
    }
    LaunchedEffect(uri, mode, request) {
        report = null; annotated = null; reference = null
        val selected = uri ?: return@LaunchedEffect
        busy = true
        try {
            val result = withContext(Dispatchers.Default) {
                val image = WallpaperRenderer.load(context, Uri.parse(selected), width, height)
                require(kotlin.math.abs(image.width.toFloat() / image.height - width.toFloat() / height) < .01f) { "截图比例不符，请选择本机完整竖屏截图" }
                val analysis = DockDetector.analyze(image, background)
                Triple(image, analysis, drawDetection(image, analysis))
            }
            reference = result.first; report = result.second; annotated = result.third
            message = result.second.message
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { message = e.localizedMessage ?: "截图读取失败" }
        finally { busy = false }
    }
    Dialog(onDismissRequest = { if (!busy && !exporting) onDismiss() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        GlassScene(reference ?: backgroundImage, Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("桌面校准", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    GlassButton(onClick = onDismiss, enabled = !busy && !exporting) { Text("取消") }
                }
                GlassChoices(listOf("普通截图", "深色校准", "浅色校准"), mode, { mode = it }, !busy && !exporting)
                if (background != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(32.dp).background(Color(background)))
                    GlassButton(enabled = !busy && !exporting, onClick = {
                        exporting = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val image = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { eraseColor(background) }
                                    try { WallpaperRenderer.save(context, image, "Dock_calibration_${if (mode == 1) "dark" else "light"}") } finally { image.recycle() }
                                }
                                message = "校准壁纸已保存到相册 Pictures / DockCreater"
                            } catch (e: Exception) { message = "保存失败，请重试" }
                            finally { exporting = false }
                        }
                    }) { Text(if (exporting) "保存中" else "保存校准壁纸") }
                }
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = .22f))
                    .border(1.dp, Color.White.copy(alpha = .45f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    annotated?.let { Image(it.asImageBitmap(), "截图识别框", Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
                        ?: Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(28.dp))
                            else Icon(Icons.Default.Add, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .65f))
                            Text(if (busy) "正在识别" else "待导入桌面截图")
                        }
                }
                Column(Modifier.heightIn(max = 88.dp).verticalScroll(rememberScrollState())) {
                    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.bodyMedium)
                }
                GlassButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !busy && !exporting, modifier = Modifier.fillMaxWidth()) { Text("导入桌面截图") }
                GlassButton(primary = true, enabled = report?.detection != null && !busy && !exporting, modifier = Modifier.fillMaxWidth(), onClick = {
                    val detection = report?.detection ?: return@GlassButton
                    val image = reference ?: return@GlassButton
                    onConfirm(detection, image)
                }) { Text(if (report?.needsReview == true) "确认估计范围并保存校准" else "确认图标框并保存校准") }
            }
        }
    }
}

internal fun drawDetection(source: Bitmap, report: DockDetector.Report): Bitmap {
    val bitmap = Bitmap.createScaledBitmap(source, 540, (540f * source.height / source.width).toInt(), true).copy(Bitmap.Config.ARGB_8888, true)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 2f }
    val boxes = report.detection?.icons ?: report.candidates
    paint.color = if (report.detection != null && !report.needsReview) android.graphics.Color.rgb(20, 220, 130) else android.graphics.Color.rgb(245, 150, 20)
    boxes.forEach { r -> canvas.drawRect(RectF(r.left * bitmap.width, r.top * bitmap.height, r.right * bitmap.width, r.bottom * bitmap.height), paint) }
    report.detection?.layout(.2f)?.let { r ->
        paint.color = android.graphics.Color.rgb(30, 180, 255)
        canvas.drawRoundRect(RectF(r.left * bitmap.width, r.top * bitmap.height, r.right * bitmap.width, r.bottom * bitmap.height), 8f, 8f, paint)
    }
    return bitmap
}
