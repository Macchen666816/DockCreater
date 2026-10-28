package com.example.dockcreater

import org.junit.Assert.assertEquals
import org.junit.Test

class ScreenProfileTest {
    @Test fun portraitProfileKeepsWidthAndHeightStableAcrossRotation() {
        val portrait = ScreenProfile(1080, 2400, 1080f / 2400f)
        val rotated = ScreenProfile(1080, 2400, 1080f / 2400f)
        assertEquals(portrait.width, rotated.width)
        assertEquals(portrait.height, rotated.height)
        assertEquals(.45f, portrait.ratio, .0001f)
    }
}
