package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator

/**
 * Solid fill using [Settings.colorProviderSolidHex] ([Settings.PREF_COLOR_PROVIDER_SOLID]).
 */
object ColorProvider : WallpaperProvider {
    override val id: String = "color"

    override val titleRes: Int = R.string.provider_color_title

    override val summaryRes: Int = R.string.provider_color_summary

    override fun generateBitmap(context: Context): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val hex = Settings(prefs).colorProviderSolidHex
        val colorInt = Color.parseColor(hex)
        val (w, h) = WallpaperGenerator.resolveSize(context)
        return WallpaperGenerator.renderSolid(w, h, colorInt)
    }
}
