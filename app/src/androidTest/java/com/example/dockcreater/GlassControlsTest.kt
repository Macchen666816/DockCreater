package com.example.dockcreater

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class GlassControlsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun buttonAndTabsKeepEnabledAndSelectionSemantics() {
        var clicks = 0
        compose.setContent {
            MaterialTheme { GlassScene(null) { Column {
                var selected by remember { mutableIntStateOf(0) }
                GlassButton({ clicks++ }) { Text("保存") }
                GlassButton({ clicks++ }, enabled = false) { Text("禁用") }
                GlassChoices(listOf("裁切", "位置"), selected, { selected = it })
            } } }
        }
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("禁用").assertIsNotEnabled().performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
        compose.onNodeWithText("裁切").assertIsSelected()
        compose.onNodeWithText("位置").performClick().assertIsSelected()
        compose.onNodeWithText("裁切").assertIsNotSelected()
    }

    @Test fun toggleAndSliderUpdateStateAndRemainAccessible() {
        var current = .2f
        compose.setContent {
            MaterialTheme { GlassScene(null) { Column {
                var checked by remember { mutableStateOf(false) }
                var value by remember { mutableFloatStateOf(.2f) }
                GlassSwitch(checked, { checked = it }, label = "玻璃开关")
                GlassSlider(value, { value = it; current = it }, 0f..1f, true, "折射")
                GlassSlider(0f, {}, 0f..1f, false, "禁用滑块")
            } } }
        }
        compose.onNodeWithContentDescription("玻璃开关").assertIsOff().performClick().assertIsOn()
        compose.onNodeWithContentDescription("折射").performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(.75f)) }
        compose.runOnIdle { assertEquals(.75f, current, .001f) }
        compose.onNodeWithContentDescription("禁用滑块").assertIsNotEnabled()
    }

    @Test fun tabsSupportDraggingAndDisabledChoicesIgnoreTouches() {
        compose.setContent {
            MaterialTheme { GlassScene(null) { Column {
                var selected by remember { mutableIntStateOf(0) }
                GlassChoices(listOf("一", "二", "三"), selected, { selected = it }, modifier = Modifier.testTag("tabs"))
                GlassChoices(listOf("禁一", "禁二"), 0, { error("Disabled choices invoked") }, false, Modifier.testTag("disabled-tabs"))
            } } }
        }
        compose.onNodeWithTag("tabs").performTouchInput {
            swipe(Offset(width / 6f, height / 2f), Offset(width * 5 / 6f, height / 2f), 700)
        }
        compose.onNodeWithText("三").assertIsSelected()
        compose.onNodeWithTag("disabled-tabs").performTouchInput { swipeLeft() }
        compose.onNodeWithText("禁一").assertIsSelected()
    }

    @Test fun sliderTrackTapAndDragAndSwitchDragUpdateValues() {
        var current = .2f
        compose.setContent {
            MaterialTheme { GlassScene(null) { Column {
                var value by remember { mutableFloatStateOf(.2f) }
                var checked by remember { mutableStateOf(false) }
                GlassSlider(value, { value = it; current = it }, 0f..1f, true, "触摸滑块", Modifier.fillMaxWidth().height(48.dp))
                GlassSwitch(checked, { checked = it }, label = "拖动开关")
            } } }
        }
        compose.onNodeWithContentDescription("触摸滑块").performTouchInput { click(Offset(width * .8f, height / 2f)) }
        compose.runOnIdle { assertEquals(.8f, current, .04f) }
        compose.onNodeWithContentDescription("触摸滑块").performTouchInput {
            swipe(Offset(width * .8f, height / 2f), Offset(width * .25f, height / 2f), 600)
        }
        compose.runOnIdle { assertTrue("Slider should drag left: $current", current < .45f) }
        compose.onNodeWithContentDescription("拖动开关").performTouchInput {
            swipe(Offset(width * .25f, height / 2f), Offset(width * .95f, height / 2f), 600)
        }
        compose.onNodeWithContentDescription("拖动开关").assertIsOn()
    }

    @Test fun draggingSliderInsidePagerDoesNotTurnPage() {
        var page = -1
        var sliderValue = .7f
        compose.setContent {
            MaterialTheme { GlassScene(null) {
                val pager = rememberPagerState { 2 }
                SideEffect { page = pager.currentPage }
                HorizontalPager(pager, Modifier.fillMaxSize()) { index ->
                    if (index == 0) {
                        var value by remember { mutableFloatStateOf(.7f) }
                        GlassSlider(value, { value = it; sliderValue = it }, 0f..1f, true, "分页中的滑块", Modifier.fillMaxWidth().height(48.dp))
                    } else Text("另一页")
                }
            } }
        }
        compose.onNodeWithContentDescription("分页中的滑块").performTouchInput {
            swipe(Offset(width * .7f, height / 2f), Offset(width * .1f, height / 2f), 600)
        }
        compose.runOnIdle {
            assertEquals("Horizontal slider drag must not navigate", 0, page)
            assertTrue(sliderValue < .3f)
        }
    }

    @Test fun previouslyDisabledControlsUseCurrentCallbacksAfterEnabling() {
        var sliderValue = .2f
        compose.setContent {
            MaterialTheme { GlassScene(null) { Column {
                var enabled by remember { mutableStateOf(false) }
                var value by remember { mutableFloatStateOf(.2f) }
                var checked by remember { mutableStateOf(false) }
                var selected by remember { mutableIntStateOf(0) }
                GlassButton({ enabled = true }) { Text("启用控件") }
                GlassSlider(value, { value = it; sliderValue = it }, 0f..1f, enabled, "导入后滑块", Modifier.fillMaxWidth().height(48.dp))
                GlassSwitch(checked, { checked = it }, enabled, "导入后开关")
                GlassChoices(listOf("甲", "乙"), selected, { selected = it }, enabled, Modifier.testTag("enabled-later-tabs"))
            } } }
        }
        compose.onNodeWithContentDescription("导入后滑块").assertIsNotEnabled()
        compose.onNodeWithText("启用控件").performClick()
        compose.onNodeWithContentDescription("导入后滑块").performTouchInput { click(Offset(width * .8f, height / 2f)) }
        compose.runOnIdle { assertEquals(.8f, sliderValue, .04f) }
        compose.onNodeWithContentDescription("导入后开关").performTouchInput { click(center) }
        compose.onNodeWithContentDescription("导入后开关").assertIsOn()
        compose.onNodeWithTag("enabled-later-tabs").performTouchInput {
            swipe(Offset(width * .25f, height / 2f), Offset(width * .75f, height / 2f), 600)
        }
        compose.onNodeWithText("乙").assertIsSelected()
    }
}
