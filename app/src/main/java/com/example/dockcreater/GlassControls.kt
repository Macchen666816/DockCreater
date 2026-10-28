package com.example.dockcreater

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.*
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.catalog.components.*

private val LocalGlassBackdrop = staticCompositionLocalOf<Backdrop> { error("GlassScene required") }
private val GlassInk = Color(0xFF182C30)
private val GlassAccent = Color(0xFF21675B)

/** Capture only the wallpaper, so glass controls never recursively sample themselves. */
@Composable
internal fun GlassScene(image: Bitmap?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val backdrop = rememberLayerBackdrop()
    Box(modifier.background(Color(0xFFF0F3F5))) {
        Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            image?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
            Box(Modifier.matchParentSize().background(Color.White.copy(alpha = .66f)))
        }
        CompositionLocalProvider(LocalGlassBackdrop provides backdrop, LocalContentColor provides GlassInk) { content() }
    }
}

@Composable
internal fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    LiquidButton(
        onClick, LocalGlassBackdrop.current,
        modifier = modifier.alpha(if (enabled) 1f else .42f), enabled = enabled,
        surfaceColor = if (primary) GlassAccent.copy(alpha = .22f) else Color.White.copy(alpha = .25f)
    ) { ProvideTextStyle(MaterialTheme.typography.labelLarge) { content() } }
}

/** All segmented choices share the catalog's moving, stretchable glass selection. */
@Composable
internal fun GlassChoices(labels: List<String>, selected: Int, onSelect: (Int) -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier, icons: List<ImageVector>? = null, compact: Boolean = false) {
    val current by rememberUpdatedState(selected)
    val select by rememberUpdatedState(onSelect)
    val active by rememberUpdatedState(enabled)
    LiquidBottomTabs(
        selectedTabIndex = { current }, onTabSelected = { if (active && it != current) select(it) },
        backdrop = LocalGlassBackdrop.current, tabsCount = labels.size, enabled = enabled,
        containerHeight = if (compact) 48.dp else 64.dp,
        modifier = modifier.fillMaxWidth().padding(horizontal = if (compact) 48.dp else 24.dp).alpha(if (enabled) 1f else .42f).selectableGroup()
    ) {
        labels.forEachIndexed { index, label ->
            LiquidBottomTab(onClick = { if (enabled) select(index) }, selected = selected == index, enabled = enabled) {
                icons?.getOrNull(index)?.let { Icon(it, null, Modifier.size(24.dp)) }
                Text(label, style = if (icons == null && !compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, enabled: Boolean = true, label: String) {
    val current by rememberUpdatedState(checked)
    val change by rememberUpdatedState(onCheckedChange)
    val active by rememberUpdatedState(enabled)
    LiquidToggle(
        selected = { current }, onSelect = { if (active) change(it) }, backdrop = LocalGlassBackdrop.current,
        enabled = enabled,
        modifier = Modifier.size(64.dp, 48.dp).alpha(if (enabled) 1f else .42f)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                role = Role.Switch
                toggleableState = ToggleableState(checked)
                if (!enabled) disabled()
                onClick { if (enabled) { change(!current); true } else false }
            }
            .onKeyEvent {
                if (enabled && it.type == KeyEventType.KeyUp && (it.key == Key.Spacebar || it.key == Key.Enter)) {
                    change(!current); true
                } else false
            }.focusable(enabled)
    )
}

@Composable
internal fun GlassSlider(value: Float, onValueChange: (Float) -> Unit, valueRange: ClosedFloatingPointRange<Float>, enabled: Boolean, label: String, modifier: Modifier = Modifier) {
    val current by rememberUpdatedState(value.coerceIn(valueRange))
    val change by rememberUpdatedState(onValueChange)
    val active by rememberUpdatedState(enabled)
    LiquidSlider(
        value = { current }, onValueChange = { if (active) change(it.coerceIn(valueRange)) },
        valueRange = valueRange, visibilityThreshold = ((valueRange.endInclusive - valueRange.start) / 1000f).coerceAtLeast(.0001f),
        backdrop = LocalGlassBackdrop.current, enabled = enabled,
        modifier = modifier.alpha(if (enabled) 1f else .42f)
            .semantics {
                contentDescription = label
                progressBarRangeInfo = ProgressBarRangeInfo(value.coerceIn(valueRange), valueRange)
                if (!enabled) disabled()
                setProgress { target ->
                    if (enabled) { change(target.coerceIn(valueRange)); true } else false
                }
            }
            .onKeyEvent {
                if (!enabled || it.type != KeyEventType.KeyDown) return@onKeyEvent false
                val delta = (valueRange.endInclusive - valueRange.start) / 20f
                val target = when (it.key) {
                    Key.DirectionRight, Key.DirectionUp -> current + delta
                    Key.DirectionLeft, Key.DirectionDown -> current - delta
                    Key.MoveHome -> valueRange.start
                    Key.MoveEnd -> valueRange.endInclusive
                    else -> return@onKeyEvent false
                }
                change(target.coerceIn(valueRange)); true
            }.focusable(enabled)
    )
}
