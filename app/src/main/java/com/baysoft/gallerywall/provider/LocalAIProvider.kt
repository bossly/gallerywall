package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator
import com.baysoft.gallerywall.ml.DynamicPromptParser
import com.baysoft.gallerywall.ml.MLImageEngine

/**
 * On-device local AI wallpaper generator. Uses stable-diffusion.cpp to synthesize a seamless
 * tile bitmap from a prompt, and tiles it to cover the screen.
 * Falls back to mathematical NoiseGenerator if no model is loaded.
 */
object LocalAIProvider : WallpaperProvider {
    private const val TAG = "LocalAIProvider"
    
    override val id: String = "local_ai"

    override val titleRes: Int = R.string.provider_ai_title
    override val summaryRes: Int = R.string.provider_ai_summary

    override fun generateBitmap(context: Context): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)
        
        val promptTemplate = settings.automationPrompt
        val activeModelPath = settings.activeModelPath
        val colors = WallpaperGenerator.parseColors(settings.generatedColorsHex)
        
        // 1. Resolve dynamic prompt placeholders
        val prompt = DynamicPromptParser.parse(context, promptTemplate)
        Log.i(TAG, "Generating AI wallpaper. Raw: '$promptTemplate' -> Resolved: '$prompt'")
        
        // 2. Load TensorFlow Lite model (with automatic fallback to assets if custom model fails or ends with non-tflite format)
        val engine = MLImageEngine.getInstance()
        var modelLoaded = false
        
        if (!activeModelPath.isNullOrEmpty() && activeModelPath.endsWith(".tflite", ignoreCase = true)) {
            modelLoaded = engine.loadModel(activeModelPath)
            if (!modelLoaded) {
                Log.w(TAG, "Configured TensorFlow Lite model failed to load at: $activeModelPath. Falling back to default asset model.")
            }
        }
        
        if (!modelLoaded) {
            modelLoaded = engine.loadModelFromAsset(context, "pixel_art_model.tflite")
        }
        
        if (!modelLoaded) {
            throw IllegalStateException("Local AI generation failed: Failed to load any TensorFlow Lite models (default asset load failed).")
        }
        
        // 3. Generate ML 64x64 pixel art tile
        val rawTile = engine.generateTile(
            prompt = prompt,
            colors = colors,
            size = 64,
            steps = 1,
            circular = true
        ) ?: throw IllegalStateException("Local AI generation failed: Engine returned a null bitmap.")
        
        // 4. Upscale tile by the configured scale factor (e.g. 2× → 128×128)
        val scaleFactor = settings.scaleFactor
        val scaledSize = 64 * scaleFactor
        val generatedTile = if (scaleFactor > 1) {
            Bitmap.createScaledBitmap(rawTile, scaledSize, scaledSize, true)
        } else {
            rawTile
        }
        
        // 5. Repeat tile perfectly across the screen dimensions
        return WallpaperGenerator.renderTiledWallpaper(context, generatedTile)
    }
}
