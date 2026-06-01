package com.baysoft.gallerywall

import com.baysoft.gallerywall.provider.ProceduralProvider
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
    fun get_proceduralAndAI() {
        assertSame(ProceduralProvider, WallpaperProviderRegistry.get(ProceduralProvider.id))
        assertSame(LocalAIProvider, WallpaperProviderRegistry.get(LocalAIProvider.id))
    }

    @Test
    fun defaultProvider_isProcedural() {
        assertSame(ProceduralProvider, WallpaperProviderRegistry.defaultProvider)
    }

    @Test
    fun all_containsRegisteredProviders() {
        val ids = WallpaperProviderRegistry.all().map { it.id }.toSet()
        assertEquals(setOf(ProceduralProvider.id, LocalAIProvider.id), ids)
    }
}
