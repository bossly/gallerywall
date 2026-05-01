package com.baysoft.gallerywall

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.GradientProvider
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = GalleryApplication::class)
class WallpaperProviderGenerateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun colorProvider_generatesSizedBitmap() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_COLOR_PROVIDER_SOLID, "#123456")
            .putString(Settings.PREF_GENERATED_COLORS, "#123456,#ABCDEF")
            .commit()
        val bmp = ColorProvider.generateBitmap(context)
        assertNotNull(bmp)
        assertTrue(bmp.width > 0)
        assertTrue(bmp.height > 0)
    }

    @Test
    fun gradientProvider_generatesSizedBitmap() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_GENERATED_COLORS, "#FF0000,#0000FF")
            .commit()
        val bmp = GradientProvider.generateBitmap(context)
        assertNotNull(bmp)
        assertTrue(bmp.width > 0)
        assertTrue(bmp.height > 0)
    }

    @Test
    fun galleryWall_unknownProviderId_fallsBackToDefault() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_WALLPAPER_PROVIDER, "unknown-provider-id")
            .commit()
        val bmp = GalleryWall.createWallpaperBitmap(context)
        assertNotNull(bmp)
        assertTrue((bmp?.width ?: 0) > 0)
    }
}
