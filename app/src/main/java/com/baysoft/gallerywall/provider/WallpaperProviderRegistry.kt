package com.baysoft.gallerywall.provider

import android.content.Context
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.BuildConfig
import com.baysoft.gallerywall.Settings

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

    fun unregister(context: Context, provider: WallpaperProvider) = synchronized(providers) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val activeId = prefs.getString(Settings.PREF_WALLPAPER_PROVIDER, null)
        providers.removeAll { it.id == provider.id }
        if (activeId == provider.id) {
            val firstProvider = providers.firstOrNull()
            if (firstProvider != null) {
                prefs.edit().putString(Settings.PREF_WALLPAPER_PROVIDER, firstProvider.id).apply()
            }
        }
    }

    val defaultProvider: WallpaperProvider
        get() = if (BuildConfig.DEBUG) ColorProvider else LocalAIProvider
}
