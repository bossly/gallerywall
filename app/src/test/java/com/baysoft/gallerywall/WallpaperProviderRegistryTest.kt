package com.baysoft.gallerywall

import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.LocalAIProvider
import com.baysoft.gallerywall.provider.ProviderState
import com.baysoft.gallerywall.provider.WallpaperProvider
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class WallpaperProviderRegistryTest {

    @After
    fun tearDown() {
        // Reset registry to initial state
        val all = WallpaperProviderRegistry.all()
        for (p in all) {
            if (p != LocalAIProvider) {
                WallpaperProviderRegistry.unregister(p)
            }
        }
        if (WallpaperProviderRegistry.get(LocalAIProvider.id) == null) {
            WallpaperProviderRegistry.register(LocalAIProvider)
        }
    }

    @Test
    fun get_unknownReturnsNull() {
        assertNull(WallpaperProviderRegistry.get("not-a-real-provider"))
    }

    @Test
    fun get_ai() {
        assertSame(LocalAIProvider, WallpaperProviderRegistry.get(LocalAIProvider.id))
    }

    @Test
    fun defaultProvider_isAI() {
        val expected = if (BuildConfig.DEBUG) ColorProvider else LocalAIProvider
        assertSame(expected, WallpaperProviderRegistry.defaultProvider)
    }

    @Test
    fun all_containsRegisteredProviders() {
        val ids = WallpaperProviderRegistry.all().map { it.id }.toSet()
        assertEquals(setOf(LocalAIProvider.id), ids)
    }

    @Test
    fun unregister_removesProvider() {
        val dummyProvider = DummyProvider("dummy")
        WallpaperProviderRegistry.register(dummyProvider)
        assertSame(dummyProvider, WallpaperProviderRegistry.get("dummy"))

        WallpaperProviderRegistry.unregister(dummyProvider)
        assertNull(WallpaperProviderRegistry.get("dummy"))
    }

    @Test
    fun unregister_withContext_automaticallySelectsFirstProvider_whenActiveProviderIsUnregistered() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val provider1 = DummyProvider("provider1")
        val provider2 = DummyProvider("provider2")

        WallpaperProviderRegistry.register(provider1)
        WallpaperProviderRegistry.register(provider2)

        // Set provider2 as active
        prefs.edit().putString(Settings.PREF_WALLPAPER_PROVIDER, provider2.id).apply()

        // Unregister provider2
        WallpaperProviderRegistry.unregister(context, provider2)

        // Check if provider2 is unregistered
        assertNull(WallpaperProviderRegistry.get(provider2.id))

        // Active provider should automatically become the first provider in all()
        val expectedActiveId = WallpaperProviderRegistry.all().firstOrNull()?.id
        val activeId = prefs.getString(Settings.PREF_WALLPAPER_PROVIDER, null)
        assertEquals(expectedActiveId, activeId)
    }

    @Test
    fun unregister_withContext_doesNotChangeActiveProvider_whenNonActiveProviderIsUnregistered() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)

        val provider1 = DummyProvider("provider1")
        val provider2 = DummyProvider("provider2")

        WallpaperProviderRegistry.register(provider1)
        WallpaperProviderRegistry.register(provider2)

        // Set provider1 as active
        prefs.edit().putString(Settings.PREF_WALLPAPER_PROVIDER, provider1.id).apply()

        // Unregister provider2
        WallpaperProviderRegistry.unregister(context, provider2)

        // Active provider should remain provider1
        val activeId = prefs.getString(Settings.PREF_WALLPAPER_PROVIDER, null)
        assertEquals(provider1.id, activeId)
    }
}

private class DummyProvider(override val id: String) : WallpaperProvider {
    override val titleRes = 0
    override val summaryRes = 0
    override fun generateBitmap(context: Context, onStateUpdate: (ProviderState) -> Unit): Bitmap {
        return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }
}
