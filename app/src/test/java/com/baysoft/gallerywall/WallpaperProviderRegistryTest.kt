package com.baysoft.gallerywall

import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.GradientProvider
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
    fun get_colorAndGradient() {
        assertSame(ColorProvider, WallpaperProviderRegistry.get(ColorProvider.id))
        assertSame(GradientProvider, WallpaperProviderRegistry.get(GradientProvider.id))
    }

    @Test
    fun defaultProvider_isColor() {
        assertSame(ColorProvider, WallpaperProviderRegistry.defaultProvider)
    }

    @Test
    fun all_containsRegisteredProviders() {
        val ids = WallpaperProviderRegistry.all().map { it.id }.toSet()
        assertEquals(setOf(ColorProvider.id, GradientProvider.id), ids)
    }
}
