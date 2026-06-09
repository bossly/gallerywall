package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.R
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.WallpaperGenerator
import com.baysoft.gallerywall.ml.DynamicPromptParser
import com.baysoft.gallerywall.ml.LocalMLEngine

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

    override fun generateBitmap(context: Context, onStateUpdate: (ProviderState) -> Unit): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)
        
        val promptTemplate = settings.automationPrompt
        val activeModelPath = settings.activeModelPath
        
        // 1. Resolve dynamic prompt placeholders
        val prompt = DynamicPromptParser.parse(context, promptTemplate)
        Log.i(TAG, "Generating AI wallpaper. Raw: '$promptTemplate' -> Resolved: '$prompt'")
        
        // 2. Load MediaPipe Image Generator model directory
        val engine = LocalMLEngine.getInstance()
        var modelLoaded = false
        
        onStateUpdate(DefaultProviderState(progress = 0.0f, message = "Loading AI model directory..."))
        if (!activeModelPath.isNullOrEmpty()) {
            val modelDir = java.io.File(activeModelPath)
            if (modelDir.exists() && modelDir.isDirectory) {
                modelLoaded = engine.loadModel(context, activeModelPath)
            }
        }
        
        if (!modelLoaded) {
            val error = IllegalStateException("MediaPipe on-device AI generation failed: No model directory configured, or directory failed to load. Please download a model from the Providers settings screen first.")
            onStateUpdate(DefaultProviderState(progress = 0.0f, error = error, message = error.message))
            throw error
        }
        
        // 3. Generate on-device image using MediaPipe diffusion progressive / step updates
        onStateUpdate(DefaultProviderState(progress = 0.1f, message = "Generating wallpaper..."))
        
        val steps = 20
        val rawTile = engine.generateTileProgressively(
            prompt = prompt,
            steps = steps,
            seed = -1,
            supportTransparency = true
        ) { step, total ->
            val progress = 0.1f + 0.8f * (step.toFloat() / total.toFloat())
            onStateUpdate(DefaultProviderState(
                progress = progress,
                message = "Generating image: step $step/$total"
            ))
        } ?: run {
            val error = IllegalStateException("MediaPipe AI generation failed: Engine returned a null bitmap.")
            onStateUpdate(DefaultProviderState(progress = 1.0f, error = error, message = error.message))
            throw error
        }
        
        onStateUpdate(DefaultProviderState(progress = 0.95f, message = "Upscaling and rendering..."))
        
        // 4. Upscale tile by the configured scale factor (e.g. 2×)
        val scaleFactor = settings.scaleFactor
        val scaledSize = rawTile.width * scaleFactor
        val generatedTile = if (scaleFactor > 1) {
            Bitmap.createScaledBitmap(rawTile, scaledSize, scaledSize, true)
        } else {
            rawTile
        }
        
        // 5. Repeat tile perfectly across the screen dimensions
        val wallpaperBmp = WallpaperGenerator.renderTiledWallpaper(context, generatedTile)
        
        onStateUpdate(DefaultProviderState(progress = 1.0f, result = wallpaperBmp, message = "Done"))
        return wallpaperBmp
    }
}
