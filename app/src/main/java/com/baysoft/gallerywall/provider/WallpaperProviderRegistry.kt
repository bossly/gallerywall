package com.baysoft.gallerywall.provider

/**
 * Compile-time registry of [WallpaperProvider] implementations.
 */
object WallpaperProviderRegistry {

    private val providers: List<WallpaperProvider> = listOf(
        GradientProvider,
        TileNoiseProvider,
        LocalAIProvider,
    )

    private val byId: Map<String, WallpaperProvider> = providers.associateBy { it.id }

    fun all(): List<WallpaperProvider> = providers

    fun get(id: String): WallpaperProvider? = byId[id]

    val defaultProvider: WallpaperProvider get() = GradientProvider
}
