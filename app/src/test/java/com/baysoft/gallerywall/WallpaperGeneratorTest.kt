package com.baysoft.gallerywall

import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class WallpaperGeneratorTest {

    @Test
    fun parseColors_singleColor() {
        val list = WallpaperGenerator.parseColors("#FF00AA")
        assertEquals(1, list.size)
        assertEquals(Color.parseColor("#FF00AA"), list[0])
    }

    @Test
    fun parseColors_multipleWithoutHash() {
        val list = WallpaperGenerator.parseColors("FF0000, 00FF00, 0000FF")
        assertEquals(3, list.size)
    }

    @Test
    fun parseColors_invalidFallsBackToDefaults() {
        val list = WallpaperGenerator.parseColors(",,,,not-a-color")
        assertEquals(3, list.size)
    }
}
