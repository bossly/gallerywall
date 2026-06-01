package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator

/**
 * Procedural Noise wallpaper provider. Generates a seamless mathematical noise tile
 * and repeats it perfectly across the device screen.
 */
object TileNoiseProvider : WallpaperProvider {
    override val id: String = "tile_noise"

    override val titleRes: Int = R.string.provider_noise_title
    override val summaryRes: Int = R.string.provider_noise_summary

    override fun generateBitmap(context: Context): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)
        
        // Compute tile size from scale factor (e.g. 2× → 128, 4× → 256)
        val size = settings.scaleFactor * 64
        
        // Parse current accent gradient colors or use dynamic theme colors
        val colors = WallpaperGenerator.parseColors(settings.generatedColorsHex)
        
        // Resolve dynamic prompt and seed to draw custom mock shapes
        val promptTemplate = settings.automationPrompt
        val prompt = com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, promptTemplate)
        val seed = (0..Int.MAX_VALUE).random()
        
        // Generate seamless dynamic simulated ML tile
        val tile = NoiseGenerator.generateSeamlessTile(size, colors, prompt, seed)
        
        // Tile across full screen dimensions
        return WallpaperGenerator.renderTiledWallpaper(context, tile)
    }
}
