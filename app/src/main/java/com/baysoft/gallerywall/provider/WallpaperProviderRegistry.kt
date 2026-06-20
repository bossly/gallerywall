package com.baysoft.gallerywall.provider

import com.baysoft.gallerywall.BuildConfig

/**
 * Registry of [WallpaperProvider] implementations.
 * Allows dynamic registration of providers (useful for UI tests).
 */
object WallpaperProviderRegistry {

    private val providers = mutableListOf<WallpaperProvider>(LocalAIProvider)

    fun all(): List<WallpaperProvider> = synchronized(providers) {
        providers.toList()
    }

    fun get(id: String): WallpaperProvider? = synchronized(providers) {
        providers.find { it.id == id }
    }

    /**
     * Registers a new provider if it's not already registered.
     */
    fun register(provider: WallpaperProvider) = synchronized(providers) {
        if (providers.none { it.id == provider.id }) {
            providers.add(provider)
        }
    }

    fun unregister(provider: WallpaperProvider) = synchronized(providers) {
        providers.removeAll { it.id == provider.id }
    }

    val defaultProvider: WallpaperProvider
        get() = if (BuildConfig.DEBUG) ColorProvider else LocalAIProvider
}
