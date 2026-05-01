package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator

/**
 * Vertical linear gradient using all colors from [Settings.PREF_GENERATED_COLORS].
 * A single configured color is drawn as a flat gradient (two identical stops).
 */
object GradientProvider : WallpaperProvider {
    override val id: String = "gradient"

    override val titleRes: Int = R.string.provider_gradient_title

    override val summaryRes: Int = R.string.provider_gradient_summary

    override fun generateBitmap(context: Context): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val colors = WallpaperGenerator.parseColors(Settings(prefs).generatedColorsHex)
        val (w, h) = WallpaperGenerator.resolveSize(context)
        return WallpaperGenerator.renderGradient(w, h, colors)
    }
}
