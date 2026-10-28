package com.example.dockcreater

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class GlassEditorTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun editorTabsAndCalibrationDialogStillWorkWithoutWallpaper() {
        compose.onNodeWithText("保存壁纸").assertIsNotEnabled()
        compose.onNodeWithText("毛玻璃").performClick().assertIsSelected()
        compose.onNodeWithText("液态玻璃").assertIsNotEnabled()
        compose.onNodeWithText("Dock 位置").performClick().assertIsSelected()
        compose.onNodeWithText("截图识别与校准").performClick()
        compose.onNodeWithText("桌面校准").assertIsDisplayed()
        compose.onNodeWithText("深色校准").performClick().assertIsSelected()
        compose.onNodeWithText("保存校准壁纸").assertIsEnabled()
        compose.onNodeWithText("确认图标框并保存校准").assertIsNotEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("Dock Studio").assertIsDisplayed()
    }

    @Test fun swipePagesAndDragBottomTabsStayInSync() {
        compose.onNodeWithTag("editor-pages").performTouchInput { swipeLeft() }
        compose.onNodeWithText("Dock 位置").assertIsSelected()
        compose.onNodeWithText("截图识别与校准").assertIsDisplayed()
        compose.onNodeWithTag("editor-pages").performTouchInput { swipeLeft() }
        compose.onNodeWithText("毛玻璃").assertIsSelected()
        compose.onNodeWithTag("bottom-tabs").performTouchInput {
            swipe(androidx.compose.ui.geometry.Offset(width * 5 / 6f, height / 2f), androidx.compose.ui.geometry.Offset(width / 6f, height / 2f), 700)
        }
        compose.onNodeWithText("裁切").assertIsSelected()
        compose.onNodeWithText("水平构图").assertIsDisplayed()
    }
}
