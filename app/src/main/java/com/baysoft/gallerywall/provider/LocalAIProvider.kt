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
        
        // 1. Resolve dynamic prompt placeholders
        val prompt = DynamicPromptParser.parse(context, promptTemplate)
        Log.i(TAG, "Generating AI wallpaper. Raw: '$promptTemplate' -> Resolved: '$prompt'")
        
        // 2. Load MediaPipe Image Generator model directory
        val engine = MLImageEngine.getInstance()
        var modelLoaded = false
        
        if (!activeModelPath.isNullOrEmpty()) {
            val modelDir = java.io.File(activeModelPath)
            if (modelDir.exists() && modelDir.isDirectory) {
                modelLoaded = engine.loadModel(context, activeModelPath)
            }
        }
        
        if (!modelLoaded) {
            throw IllegalStateException("MediaPipe on-device AI generation failed: No model directory configured, or directory failed to load. Please download a model from the Providers settings screen first.")
        }
        
        // 3. Generate on-device image using MediaPipe diffusion
        val rawTile = engine.generateTile(
            prompt = prompt,
            steps = 20,
            seed = -1,
            supportTransparency = true
        ) ?: throw IllegalStateException("MediaPipe AI generation failed: Engine returned a null bitmap.")
        
        // 4. Upscale tile by the configured scale factor (e.g. 2×)
        val scaleFactor = settings.scaleFactor
        val scaledSize = rawTile.width * scaleFactor
        val generatedTile = if (scaleFactor > 1) {
            Bitmap.createScaledBitmap(rawTile, scaledSize, scaledSize, true)
        } else {
            rawTile
        }
        
        // 5. Repeat tile perfectly across the screen dimensions
        return WallpaperGenerator.renderTiledWallpaper(context, generatedTile)
    }
}
