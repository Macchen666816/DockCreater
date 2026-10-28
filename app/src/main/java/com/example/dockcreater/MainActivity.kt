package com.example.dockcreater

import android.graphics.Bitmap
import android.os.Bundle
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val screen = ScreenProfile.from(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF21675B), secondary = Color(0xFF735B88), background = Color(0xFFF6F7F8), surface = Color(0xFFF6F7F8))) {
                DockEditor(screen.width, screen.height, screen.ratio)
            }
        }
    }
}

@Composable
private fun DockEditor(width: Int, height: Int, ratio: Float) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var imageUri by rememberSaveable { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val pager = rememberPagerState(initialPage = tab, pageCount = { 3 })
    LaunchedEffect(pager.settledPage) { tab = pager.settledPage }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    var cropX by rememberSaveable { mutableFloatStateOf(.5f) }
    var cropY by rememberSaveable { mutableFloatStateOf(.5f) }
    var dockWidth by rememberSaveable { mutableFloatStateOf(.90f) }
    var dockHeight by rememberSaveable { mutableFloatStateOf(.085f) }
    var bottom by rememberSaveable { mutableFloatStateOf(.035f) }
    var radius by rememberSaveable { mutableFloatStateOf(.32f) }
    var blur by rememberSaveable { mutableFloatStateOf(28f) }
    var opacity by rememberSaveable { mutableFloatStateOf(.28f) }
    var dark by rememberSaveable { mutableStateOf(false) }
    var centerX by rememberSaveable { mutableFloatStateOf(.5f) }
    var liquid by rememberSaveable { mutableStateOf(true) }
    var glassCornerRadius by rememberSaveable { mutableFloatStateOf(.5f) }
    var glassBlurRadiusDp by rememberSaveable { mutableFloatStateOf(0f) }
    var refractionHeight by rememberSaveable { mutableFloatStateOf(.3f) }
    var refractionAmount by rememberSaveable { mutableFloatStateOf(.6f) }
    var chromaticAberration by rememberSaveable { mutableFloatStateOf(0f) }
    var screenshot by remember { mutableStateOf<Bitmap?>(null) }
    var detection by remember { mutableStateOf<DockDetection?>(null) }
    val detecting = false
    var calibrationOpen by rememberSaveable { mutableStateOf(false) }
    var calibrationLoaded by rememberSaveable { mutableStateOf(false) }
    var detectedMessage by remember { mutableStateOf("") }
    var iconPadding by rememberSaveable { mutableFloatStateOf(.20f) }
    var showIcons by rememberSaveable { mutableStateOf(true) }
    fun applyDetection(result: DockDetection, padding: Float) {
        val rect = result.layout(padding)
        dockWidth = rect.width(); dockHeight = rect.height(); bottom = 1f - rect.bottom
        centerX = rect.centerX(); radius = .32f
    }
    val settings = DockSettings(zoom, cropX, cropY, dockWidth, dockHeight, bottom, radius, blur, opacity, dark, centerX, liquid,
        LiquidGlassSettings(glassCornerRadius, glassBlurRadiusDp, refractionHeight, refractionAmount, chromaticAberration))
    LaunchedEffect(width, height) {
        CalibrationStore.load(context, width, height)?.let { (result, padding) ->
            detection = result
            if (!calibrationLoaded) { iconPadding = padding; applyDetection(result, padding) }
            calibrationLoaded = true
            detectedMessage = "已加载本机校准"
        }
    }
    if (calibrationOpen) CalibrationDialog(width, height, backgroundImage = source, onDismiss = { calibrationOpen = false }) { result, reference ->
        detection = result; screenshot = reference
        applyDetection(result, iconPadding)
        CalibrationStore.save(context, width, height, result, iconPadding)
        detectedMessage = "已保存 ${result.icons.size} 个图标的校准"
        calibrationLoaded = true; calibrationOpen = false
    }
    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        loading = true
        try { source = withContext(Dispatchers.IO) { WallpaperRenderer.load(context, android.net.Uri.parse(uri), width, height) } }
        catch (e: Exception) { snackbar.showSnackbar("图片读取失败，请重新选择") }
        finally { loading = false }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            imageUri = uri.toString(); zoom = 1f; cropX = .5f; cropY = .5f
        }
    }
    val gpuPreview = Build.VERSION.SDK_INT >= 33
    val preview by produceState<Bitmap?>(null, source, if (gpuPreview) null else settings, width, height,
        if (gpuPreview) null else tab, if (gpuPreview) null else screenshot,
        if (gpuPreview) null else detection, if (gpuPreview) null else showIcons) {
        if (gpuPreview) return@produceState
        val input = source ?: return@produceState
        value = withContext(Dispatchers.Default) {
            val rendered = WallpaperRenderer.render(context, input, 432, (432f * height / width).toInt(), settings, tab != 0)
            val reference = screenshot
            if (tab != 0 && showIcons && reference == null) {
                val canvas = android.graphics.Canvas(rendered)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(20, 220, 130)
                    style = android.graphics.Paint.Style.STROKE; strokeWidth = 1.5f
                }
                detection?.icons?.forEach { r ->
                    canvas.drawRect(android.graphics.RectF(r.left * rendered.width, r.top * rendered.height, r.right * rendered.width, r.bottom * rendered.height), paint)
                }
            }
            if (tab != 0 && showIcons && reference != null) {
                val canvas = android.graphics.Canvas(rendered)
                val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG or android.graphics.Paint.FILTER_BITMAP_FLAG)
                detection?.icons?.forEach { r ->
                    val src = android.graphics.Rect((r.left * reference.width).toInt(), (r.top * reference.height).toInt(), (r.right * reference.width).toInt(), (r.bottom * reference.height).toInt())
                    val dst = android.graphics.RectF(r.left * rendered.width, r.top * rendered.height, r.right * rendered.width, r.bottom * rendered.height)
                    canvas.save()
                    canvas.clipPath(android.graphics.Path().apply { addRoundRect(dst, dst.width() * .22f, dst.height() * .22f, android.graphics.Path.Direction.CW) })
                    canvas.drawBitmap(reference, src, dst, paint)
                    canvas.restore()
                }
            }
            rendered
        }
    }
    GlassScene(source, Modifier.fillMaxSize()) {
        Scaffold(containerColor = Color.Transparent, snackbarHost = { SnackbarHost(snackbar) }, bottomBar = {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton(onClick = { picker.launch(arrayOf("image/*")) }, enabled = !saving && !loading, modifier = Modifier.weight(1f)) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(if (source == null) "选择壁纸" else "更换壁纸") }
                    GlassButton(primary = true, enabled = source != null && !saving && !loading && !detecting, modifier = Modifier.weight(1f), onClick = {
                        val input = source ?: return@GlassButton
                        val snapshot = settings
                        saving = true
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val output = WallpaperRenderer.render(context, input, width, height, snapshot, true)
                                    try { WallpaperRenderer.save(context, output) } finally { output.recycle() }
                                }
                                snackbar.showSnackbar("已保存到相册 Pictures / DockCreater")
                            } catch (e: Exception) { snackbar.showSnackbar("保存失败，请重试") }
                            finally { saving = false }
                        }
                    }) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Text(if (saving) "保存中" else "保存壁纸") }
                }
                GlassChoices(listOf("裁切", "Dock 位置", "毛玻璃"), pager.targetPage, { index ->
                    scope.launch { pager.animateScrollToPage(index) }
                }, modifier = Modifier.testTag("bottom-tabs"), icons = listOf(Icons.Default.Edit, Icons.Default.Settings, Icons.Default.Star))
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dock Studio", style = MaterialTheme.typography.titleLarge)
                        Text("${width} × ${height} · ${"%.3f".format(ratio)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                    }
                }
                Box(Modifier.fillMaxWidth().weight(1f).padding(12.dp), contentAlignment = Alignment.Center) {
                    val bitmap = preview
                    if (source != null && (gpuPreview || bitmap != null)) {
                        val previewModifier = Modifier.fillMaxHeight().aspectRatio(width.toFloat() / height)
                                .pointerInput(source, tab, saving) {
                                    detectTransformGestures { _, pan, scale, _ ->
                                        if (tab == 0 && !saving) {
                                            zoom = (zoom * scale).coerceIn(1f, 4f)
                                            val input = source ?: return@detectTransformGestures
                                            val fit = maxOf(size.width.toFloat() / input.width, size.height.toFloat() / input.height) * zoom
                                            val excessX = input.width * fit - size.width
                                            val excessY = input.height * fit - size.height
                                            if (excessX > 1f) cropX = (cropX - pan.x / excessX).coerceIn(0f, 1f)
                                            if (excessY > 1f) cropY = (cropY - pan.y / excessY).coerceIn(0f, 1f)
                                        }
                                    }
                                }
                        if (gpuPreview && Build.VERSION.SDK_INT >= 33) {
                            AndroidView(factory = { GpuWallpaperPreview(it).apply { contentDescription = "壁纸预览" } },
                                modifier = previewModifier,
                                update = { it.update(checkNotNull(source), settings, tab != 0,
                                    (432f * height / width).toInt(), screenshot, detection, showIcons) })
                        } else if (bitmap != null) {
                            Image(bitmap.asImageBitmap(), contentDescription = "壁纸预览", contentScale = ContentScale.Fit,
                                modifier = previewModifier)
                        }
                    } else Text("选择一张壁纸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (loading) CircularProgressIndicator()
                }
                HorizontalPager(state = pager, modifier = Modifier.fillMaxWidth().height(224.dp).testTag("editor-pages"), userScrollEnabled = !saving) { page ->
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
                        val enabled = source != null && !saving && !loading && !detecting
                        when (page) {
                            0 -> {
                                Adjustment("缩放", "%.2f×".format(zoom), zoom, 1f..4f, enabled) { zoom = it }
                                Adjustment("水平构图", "${(cropX * 100).toInt()}%", cropX, 0f..1f, enabled) { cropX = it }
                                Adjustment("垂直构图", "${(cropY * 100).toInt()}%", cropY, 0f..1f, enabled) { cropY = it }
                            }
                            1 -> {
                                GlassButton(onClick = { calibrationOpen = true }, enabled = !saving) { Text("截图识别与校准") }
                                if (detectedMessage.isNotEmpty()) Text(detectedMessage, style = MaterialTheme.typography.labelMedium)
                                if (detection != null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (screenshot == null) "图标位置框" else "图标参考", Modifier.weight(1f)); GlassSwitch(showIcons, onCheckedChange = { showIcons = it }, enabled = enabled, label = "显示图标参考")
                                    }
                                    Adjustment("统一留白", "${(iconPadding * 100).toInt()}%", iconPadding, .05f.. .45f, enabled) {
                                        iconPadding = it; detection?.let { result ->
                                            applyDetection(result, it)
                                            CalibrationStore.save(context, width, height, result, it)
                                        }
                                    }
                                }
                                Adjustment("宽度", "${(dockWidth * width).toInt()} px", dockWidth, .3f..1f, enabled) {
                                    dockWidth = it; centerX = centerX.coerceIn(it / 2f, 1f - it / 2f)
                                }
                                Adjustment("高度", "${(dockHeight * height).toInt()} px", dockHeight, .025f.. .22f, enabled) { dockHeight = it }
                                Adjustment("水平位置", "${(centerX * width).toInt()} px", centerX, dockWidth / 2f..(1f - dockWidth / 2f), enabled && dockWidth < 1f) { centerX = it }
                                Adjustment("距底部", "${(bottom * height).toInt()} px", bottom, 0f.. .25f, enabled) { bottom = it }
                            }
                            2 -> {
                                GlassChoices(listOf("液态玻璃", "磨砂"), if (liquid) 0 else 1, { liquid = it == 0 }, enabled, compact = true)
                                if (liquid) {
                                    Adjustment("圆角半径", "%.2f".format(glassCornerRadius), glassCornerRadius, 0f..1f, enabled) { glassCornerRadius = it }
                                    Adjustment("模糊半径", "%.1f dp".format(glassBlurRadiusDp), glassBlurRadiusDp, 0f..32f, enabled) { glassBlurRadiusDp = it }
                                    Adjustment("折射高度", "%.2f".format(refractionHeight), refractionHeight, 0.3f..1f, enabled) { refractionHeight = it }
                                    Adjustment("折射量", "%.2f".format(refractionAmount), refractionAmount, 0.5f..1f, enabled) { refractionAmount = it }
                                    Adjustment("色差", "%.2f".format(chromaticAberration), chromaticAberration, 0f..1f, enabled) { chromaticAberration = it }
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("深色玻璃", Modifier.weight(1f)); GlassSwitch(checked = dark, onCheckedChange = { dark = it }, enabled = enabled, label = "深色玻璃")
                                    }
                                    Adjustment("模糊", "${blur.toInt()}", blur, 0f..64f, enabled) { blur = it }
                                    Adjustment("底色浓度", "${(opacity * 100).toInt()}%", opacity, 0f.. .8f, enabled) { opacity = it }
                                    Adjustment("圆角", "${(radius * 100).toInt()}%", radius, 0f.. .5f, enabled) { radius = it }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Adjustment(label: String, valueLabel: String, value: Float, range: ClosedFloatingPointRange<Float>, enabled: Boolean, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
        }
        GlassSlider(value = value, onValueChange = onChange, valueRange = range, enabled = enabled, label = label, modifier = Modifier.height(48.dp))
    }
}
