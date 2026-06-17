package com.baysoft.gallerywall

import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.LocalAIProvider
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class WallpaperProviderRegistryTest {

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
}
