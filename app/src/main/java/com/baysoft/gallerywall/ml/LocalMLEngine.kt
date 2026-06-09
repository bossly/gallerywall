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
class LocalMLEngine private constructor() {

    private var imageGenerator: ImageGenerator? = null
    private var loadedModelPath: String? = null
    var lastLoadError: String? = null

    /**
     * Loads the MediaPipe Image Generator using the given local directory path.
     */
    fun loadModel(context: Context, modelPath: String): Boolean {
        val baseDir = File(modelPath)
        if (!baseDir.exists()) {
            Log.e(TAG, "Model directory does not exist at: $modelPath")
            lastLoadError = "Model directory does not exist at: $modelPath"
            return false
        }

        // Dynamically locate the nested directory containing the actual model files (e.g. bpe_simple_vocab_16e6.txt)
        val resolvedDir = findModelDirectory(baseDir) ?: baseDir
        val resolvedPath = resolvedDir.absolutePath
        Log.i(TAG, "loadModel: Requested path = $modelPath | Resolved path = $resolvedPath")

        // Validate that all critical model files are present before initializing the native runner
        val validationError = validateModelFiles(resolvedDir)
        if (validationError != null) {
            Log.e(TAG, "Validation failed for model folder: $validationError")
            lastLoadError = "Invalid model structure: $validationError. Ensure the model is a fully converted SD 1.5 directory (with all .bin weights and bpe_simple_vocab_16e6.txt)."
            return false
        }

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
            lastLoadError = null
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading MediaPipe Image Generator from: $resolvedPath", e)
            val msg = e.toString()
            lastLoadError = if (msg.contains("OpenCL") || msg.contains("clSetPerfHintQCOM") || msg.contains("CalculatorGraph::Run") || msg.contains("FAILED_PRECONDITION")) {
                "Incompatible hardware: MediaPipe Stable Diffusion requires a physical Android GPU with OpenCL support. Emulators are not supported."
            } else {
                "Failed to initialize MediaPipe: ${e.localizedMessage}"
            }
            false
        }
    }

    /**
     * Recursively searches for the directory containing the BPE vocabulary file (indicating the root model weights folder).
     */
    private fun findModelDirectory(dir: File): File? {
        if (!dir.exists() || !dir.isDirectory) return null
        
        val vocab = File(dir, "bpe_simple_vocab_16e6.txt")
        if (vocab.exists()) {
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
     * Validates that critical files exist and are non-empty to prevent native crashes.
     */
    private fun validateModelFiles(dir: File): String? {
        val requiredFiles = listOf(
            "bpe_simple_vocab_16e6.txt",
            "cond_stage_model.transformer.text_model.embeddings.token_embedding.weight.bin",
            "model.diffusion_model.input_blocks.0.0.weight.bin",
            "first_stage_model.decoder.conv_out.weight.bin"
        )
        
        for (filename in requiredFiles) {
            val file = File(dir, filename)
            if (!file.exists()) {
                return "Missing required model file: $filename"
            }
            if (file.length() == 0L) {
                return "Model file is empty: $filename"
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
     * Generates a square seamless pixel-art tile bitmap based on a text prompt
     * using MediaPipe's iterative Image Generator API, reporting progress step by step.
     */
    fun generateTileProgressively(
        prompt: String,
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

            if (supportTransparency) {
                generatedTile = rawBitmap
            } else {
                // Force alpha channel to opaque (255) if transparency is disabled
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
                    pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                }

                generatedTile = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }

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

    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt
     * using MediaPipe's synchronous Image Generator API.
     */
    fun generateTile(
        prompt: String,
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

            if (supportTransparency) {
                generatedTile = rawBitmap
            } else {
                // Force alpha channel to opaque (255) if transparency is disabled
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
                    pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
                }

                generatedTile = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
            }

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

    /**
     * Unloads the current model and releases all native resources.
     */
    fun unloadModel() {
        synchronized(this) {
            try {
                imageGenerator?.close()
                Log.i(TAG, "MediaPipe Image Generator successfully closed/unloaded.")
            } catch (e: Exception) {
                Log.e(TAG, "Error closing MediaPipe Image Generator", e)
            }
            imageGenerator = null
            loadedModelPath = null
        }
    }

    companion object {
        private const val TAG = "LocalMLEngine"

        @Volatile
        private var instance: LocalMLEngine? = null

        fun getInstance(): LocalMLEngine {
            return instance ?: synchronized(this) {
                instance ?: LocalMLEngine().also { instance = it }
            }
        }
    }
}
