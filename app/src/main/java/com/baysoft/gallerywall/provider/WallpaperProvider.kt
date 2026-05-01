package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes

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

    fun generateBitmap(context: Context): Bitmap
}
