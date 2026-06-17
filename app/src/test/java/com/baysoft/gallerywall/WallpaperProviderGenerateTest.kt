package com.baysoft.gallerywall

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertThrows
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
    fun localAIProvider_throwsException_whenNoModelLoaded() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_WALLPAPER_PROVIDER, "local_ai")
            .commit()
        assertThrows(IllegalStateException::class.java) {
            GalleryWall.createWallpaperBitmap(context)
        }
    }
}
