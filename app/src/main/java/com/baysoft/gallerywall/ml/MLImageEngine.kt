package com.baysoft.gallerywall.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import com.google.mediapipe.framework.image.BitmapExtractor
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator
import com.google.mediapipe.tasks.vision.imagegenerator.ImageGenerator.ImageGeneratorOptions
import java.io.File

/**
 * On-device ML image generator interface using Google AI Edge MediaPipe Tasks Vision.
 * Loads a compatible Stable Diffusion v1.5 model directory to generate images
 * locally from text prompts.
 */
class MLImageEngine private constructor() {

    private var imageGenerator: ImageGenerator? = null
    private var loadedModelPath: String? = null

    /**
     * Loads the MediaPipe Image Generator using the given local directory path.
     */
    fun loadModel(context: Context, modelPath: String): Boolean {
        val baseDir = File(modelPath)
        if (!baseDir.exists()) {
            Log.e(TAG, "Model directory does not exist at: $modelPath")
            return false
        }

        // Dynamically locate the nested directory containing the actual model files (e.g. unet.tflite)
        val resolvedDir = findModelDirectory(baseDir) ?: baseDir
        val resolvedPath = resolvedDir.absolutePath
        Log.i(TAG, "loadModel: Requested path = $modelPath | Resolved path = $resolvedPath")

        if (loadedModelPath == resolvedPath && imageGenerator != null) {
            return true
        }

        return try {
            // Close existing generator to release native resources
            imageGenerator?.close()
            imageGenerator = null

            val options = ImageGeneratorOptions.builder()
                .setImageGeneratorModelDirectory(resolvedPath)
                .build()
            imageGenerator = ImageGenerator.createFromOptions(context, options)
            loadedModelPath = resolvedPath
            Log.i(TAG, "MediaPipe Image Generator successfully loaded from path: $resolvedPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MediaPipe Image Generator from: $resolvedPath", e)
            false
        }
    }

    /**
     * Recursively searches for the directory containing the core model weights (unet.tflite or text_encoder.tflite).
     */
    private fun findModelDirectory(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        
        val unet = File(dir, "unet.tflite")
        val textEncoder = File(dir, "text_encoder.tflite")
        if (unet.exists() || textEncoder.exists()) {
            return dir
        }
        
        val children = dir.listFiles() ?: return null
        for (child in children) {
            if (child.isDirectory) {
                val found = findModelDirectory(child)
                if (found != null) {
                    return found
                }
            }
        }
        return null
    }

    /**
     * Legacy helper to satisfy existing structure checks.
     * MediaPipe Image Generator does not support direct asset folder TFLite loading for SD.
     */
    fun loadModelFromAsset(context: Context, assetName: String): Boolean {
        Log.w(TAG, "MediaPipe Image Generator does not support direct asset loading for Stable Diffusion v1.5.")
        return false
    }

    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt and active color configurations
     * using MediaPipe's synchronous Image Generator API.
     * @param prompt The descriptive text prompt for the texture.
     * @param colors The list of user-configured colors to map or tint the sprite palette.
     * @param size Unused.
     * @param steps The number of diffusion sampling steps (iterations).
     * @param seed Random seed for reproducibility. Set to -1 for random.
     * @param circular Unused.
     * @param supportTransparency Whether to preserve generated alpha transparency or force fully opaque.
     */
    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt and active color configurations
     * using MediaPipe's iterative Image Generator API, reporting progress step by step.
     */
    fun generateTileProgressively(
        prompt: String,
        colors: List<Int>,
        steps: Int = 20,
        seed: Int = -1,
        supportTransparency: Boolean = true,
        onProgress: (step: Int, totalSteps: Int) -> Unit
    ): Bitmap? {
        val modelName = loadedModelPath?.let { File(it).name } ?: "Default"
        Log.d(TAG, "ML progressive generating process started | Model: $modelName | Seed: $seed | Steps: $steps")

        val startTime = System.currentTimeMillis()
        var generatedTile: Bitmap? = null

        try {
            val generator = imageGenerator
            if (generator == null) {
                Log.e(TAG, "Cannot generate: MediaPipe Image Generator is not loaded!")
                return null
            }

            val actualSeed = if (seed == -1) (0..Int.MAX_VALUE).random() else seed

            // 1. Initialize inputs for iterative generation
            generator.setInputs(prompt, steps, actualSeed)

            var rawBitmap: Bitmap? = null

            // 2. Loop through iterations
            for (step in 0 until steps) {
                // Check intermediate result request: showResult = true ONLY on the last step
                // to save substantial device resources and prevent unnecessary CPU/GPU overhead.
                val isLast = (step == steps - 1)
                
                onProgress(step + 1, steps)
                
                val result = generator.execute(isLast)
                
                if (isLast) {
                    val mpImage = result?.generatedImage() ?: run {
                        Log.e(TAG, "ImageGenerator returned null generatedImage at final step")
                        return null
                    }
                    // Extract native Bitmap from MPImage
                    rawBitmap = BitmapExtractor.extract(mpImage)
                }
            }

            if (rawBitmap == null) {
                Log.e(TAG, "Failed to extract Bitmap from MPImage")
                return null
            }

            val width = rawBitmap.width
            val height = rawBitmap.height
            val pixelCount = height * width
            val pixels = IntArray(pixelCount)
            rawBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in 0 until pixelCount) {
                val color = pixels[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val a = if (supportTransparency) Color.alpha(color) else 255

                if (colors.size > 1) {
                    val grayscale = (0.299f * r + 0.587f * g + 0.114f * b)
                    val norm = grayscale / 255.0f

                    val position = norm * (colors.size - 1)
                    val idx1 = position.toInt().coerceIn(0, colors.size - 2)
                    val idx2 = (idx1 + 1).coerceIn(0, colors.size - 1)
                    val weight = position - idx1

                    val c1 = colors[idx1]
                    val c2 = colors[idx2]

                    val rOut = (Color.red(c1) * (1f - weight) + Color.red(c2) * weight).toInt().coerceIn(0, 255)
                    val gOut = (Color.green(c1) * (1f - weight) + Color.green(c2) * weight).toInt().coerceIn(0, 255)
                    val bOut = (Color.blue(c1) * (1f - weight) + Color.blue(c2) * weight).toInt().coerceIn(0, 255)

                    pixels[i] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                } else {
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            generatedTile = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Image Generator generation failed", e)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        if (generatedTile != null) {
            Log.d(TAG, "ML progressive generating process SUCCEEDED | Duration: ${duration}ms | Size: ${generatedTile.width}x${generatedTile.height}")
        } else {
            Log.e(TAG, "ML progressive generating process FAILED | Duration: ${duration}ms")
        }

        return generatedTile
    }

    fun generateTile(
        prompt: String,
        colors: List<Int>,
        size: Int = 32,
        steps: Int = 20,
        seed: Int = -1,
        circular: Boolean = true,
        supportTransparency: Boolean = true
    ): Bitmap? {
        val modelName = loadedModelPath?.let { File(it).name } ?: "Default"
        Log.d(TAG, "ML generating process started | Model: $modelName | Seed: $seed | Steps: $steps")

        val startTime = System.currentTimeMillis()
        var generatedTile: Bitmap? = null

        try {
            val generator = imageGenerator
            if (generator == null) {
                Log.e(TAG, "Cannot generate: MediaPipe Image Generator is not loaded!")
                return null
            }

            val actualSeed = if (seed == -1) (0..Int.MAX_VALUE).random() else seed

            // Run MediaPipe Image Generator (synchronous end-to-end generate)
            val result = generator.generate(prompt, steps, actualSeed)
            val mpImage = result.generatedImage() ?: run {
                Log.e(TAG, "ImageGenerator returned null generatedImage")
                return null
            }

            // Extract native Bitmap from MPImage
            val rawBitmap = BitmapExtractor.extract(mpImage) ?: run {
                Log.e(TAG, "Failed to extract Bitmap from MPImage")
                return null
            }

            val width = rawBitmap.width
            val height = rawBitmap.height
            val pixelCount = height * width
            val pixels = IntArray(pixelCount)
            rawBitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            for (i in 0 until pixelCount) {
                val color = pixels[i]
                val r = Color.red(color)
                val g = Color.green(color)
                val b = Color.blue(color)
                val a = if (supportTransparency) Color.alpha(color) else 255

                if (colors.size > 1) {
                    // Apply visual harmony color-mapping: convert pixel to grayscale intensity
                    val grayscale = (0.299f * r + 0.587f * g + 0.114f * b)
                    val norm = grayscale / 255.0f

                    // Interpolate across custom colors selected in the app
                    val position = norm * (colors.size - 1)
                    val idx1 = position.toInt().coerceIn(0, colors.size - 2)
                    val idx2 = (idx1 + 1).coerceIn(0, colors.size - 1)
                    val weight = position - idx1

                    val c1 = colors[idx1]
                    val c2 = colors[idx2]

                    val rOut = (Color.red(c1) * (1f - weight) + Color.red(c2) * weight).toInt().coerceIn(0, 255)
                    val gOut = (Color.green(c1) * (1f - weight) + Color.green(c2) * weight).toInt().coerceIn(0, 255)
                    val bOut = (Color.blue(c1) * (1f - weight) + Color.blue(c2) * weight).toInt().coerceIn(0, 255)

                    pixels[i] = (a shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                } else {
                    pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            // Build dynamic sized bitmap with visual harmony palette applied
            generatedTile = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Image Generator generation failed", e)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        if (generatedTile != null) {
            Log.d(TAG, "ML generating process SUCCEEDED | Duration: ${duration}ms | Size: ${generatedTile.width}x${generatedTile.height}")
        } else {
            Log.e(TAG, "ML generating process FAILED | Duration: ${duration}ms")
        }

        return generatedTile
    }

    companion object {
        private const val TAG = "MLImageEngine"

        @Volatile
        private var instance: MLImageEngine? = null

        fun getInstance(): MLImageEngine {
            return instance ?: synchronized(this) {
                instance ?: MLImageEngine().also { instance = it }
            }
        }
    }
}
