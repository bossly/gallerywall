package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes

/**
 * State representing the wallpaper generation process.
 */
interface ProviderState {
    val progress: Float
    val result: Bitmap?
    val error: Throwable?
    val message: String?
}

/**
 * Default implementation of [ProviderState].
 */
data class DefaultProviderState(
    override val progress: Float,
    override val result: Bitmap? = null,
    override val error: Throwable? = null,
    override val message: String? = null
) : ProviderState

/**
 * Strategy for generating a wallpaper bitmap from current preferences.
 * Register new implementations in [WallpaperProviderRegistry].
 */
interface WallpaperProvider {
    val id: String

    @get:StringRes
    val titleRes: Int

    @get:StringRes
    val summaryRes: Int

    fun generateBitmap(context: Context, onStateUpdate: (ProviderState) -> Unit = {}): Bitmap
}
