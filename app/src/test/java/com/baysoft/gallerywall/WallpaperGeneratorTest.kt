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

    @Test
    fun renderTiledWallpaper_createsCorrectDimensions() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val tile = android.graphics.Bitmap.createBitmap(128, 128, android.graphics.Bitmap.Config.ARGB_8888)
        val wallpaper = WallpaperGenerator.renderTiledWallpaper(context, tile)
        
        val (expectedW, expectedH) = WallpaperGenerator.resolveSize(context)
        assertEquals(expectedW, wallpaper.width)
        assertEquals(expectedH, wallpaper.height)
    }
}
