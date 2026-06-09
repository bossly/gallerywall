package com.baysoft.gallerywall.provider

import com.baysoft.gallerywall.BuildConfig

/**
 * Compile-time registry of [WallpaperProvider] implementations.
 * In debug builds, [ColorProvider] is included and set as the default.
 */
object WallpaperProviderRegistry {

    private val providers: List<WallpaperProvider> = buildList {
        if (BuildConfig.DEBUG) {
            add(ColorProvider)
        }
        add(LocalAIProvider)
    }

    private val byId: Map<String, WallpaperProvider> = providers.associateBy { it.id }

    fun all(): List<WallpaperProvider> = providers

    fun get(id: String): WallpaperProvider? = byId[id]

    val defaultProvider: WallpaperProvider
        get() = if (BuildConfig.DEBUG) ColorProvider else LocalAIProvider
}
