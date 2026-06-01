package com.baysoft.gallerywall.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.pytorch.IValue
import org.pytorch.LiteModuleLoader
import org.pytorch.Module
import org.pytorch.Tensor
import java.io.File

/**
 * On-device ML image generator interface using PyTorch Mobile Lite.
 * Loads a PyTorch traced (.ptl) model to generate 64x64 pixel art sprite grids
 * locally in milliseconds, with zero JNI NDK compiler dependencies.
 */
class MLImageEngine private constructor() {

    private var module: Module? = null
    private var loadedModelPath: String? = null

    /**
     * Loads the PyTorch Mobile model from the given local file path.
     */
    fun loadModel(modelPath: String): Boolean {
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file does not exist at: $modelPath")
            return false
        }

        if (loadedModelPath == modelPath && module != null) {
            return true
        }

        return try {
            module = LiteModuleLoader.load(modelPath)
            loadedModelPath = modelPath
            Log.i(TAG, "PyTorch Mobile model successfully loaded from path: $modelPath")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PyTorch Lite model from: $modelPath", e)
            false
        }
    }

    /**
     * Loads the PyTorch Mobile model directly from the APK's assets folder.
     */
    fun loadModelFromAsset(context: Context, assetName: String): Boolean {
        val cacheKey = "asset://$assetName"
        if (loadedModelPath == cacheKey && module != null) {
            return true
        }

        return try {
            module = LiteModuleLoader.loadModuleFromAsset(context.assets, assetName)
            loadedModelPath = cacheKey
            Log.i(TAG, "PyTorch Mobile model successfully loaded from assets: $assetName")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading PyTorch Lite model from asset: $assetName", e)
            false
        }
    }

    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt and active color configurations.
     * @param prompt The descriptive text prompt for the texture.
     * @param colors The list of user-configured colors to map or tint the sprite palette.
     * @param size Resolution size: unused since PyTorch mobile GAN outputs native 64x64 grids.
     * @param steps Unused since GAN runs in a single forward pass.
     * @param seed Random seed for reproducibility. Set to -1 for random.
     * @param circular Unused.
     */
    fun generateTile(
        prompt: String,
        colors: List<Int>,
        size: Int = 64,
        steps: Int = 1,
        seed: Int = -1,
        circular: Boolean = true
    ): Bitmap? {
        val modelName = loadedModelPath?.let { File(it).name } ?: "Default (Assets)"
        Log.d(TAG, "ML generating process started | Model: $modelName | Seed: $seed | Steps: $steps")

        val startTime = System.currentTimeMillis()
        var generatedTile: Bitmap? = null

        try {
            if (module == null) {
                Log.e(TAG, "Cannot generate: PyTorch Mobile model is not loaded!")
                return null
            }

            // 1. Generate standard latent vector z ~ N(0, 1) of size 100
            val rand = if (seed == -1) {
                java.util.Random()
            } else {
                java.util.Random(seed.toLong())
            }
            val noiseArray = FloatArray(100) { rand.nextGaussian().toFloat() }
            val noiseTensor = Tensor.fromBlob(noiseArray, longArrayOf(1, 100))

            // 2. Resolve the text prompt into a style class index (0-9)
            val classLabel = mapPromptToClassLabel(prompt)
            val labelTensor = Tensor.fromBlob(longArrayOf(classLabel), longArrayOf(1))

            Log.d(TAG, "Resolved prompt '$prompt' to class index $classLabel")

            // 3. Execute PyTorch mobile model forward pass with robust out-of-bounds error handling
            val outputTensor = try {
                module!!.forward(
                    IValue.from(noiseTensor),
                    IValue.from(labelTensor)
                ).toTensor()
            } catch (e: Exception) {
                if (e.message?.contains("index out of range", ignoreCase = true) == true) {
                    Log.w(TAG, "PyTorch label index $classLabel is out of range for this model. Retrying forward pass with fallback index 0L.")
                    val fallbackLabelTensor = Tensor.fromBlob(longArrayOf(0L), longArrayOf(1))
                    module!!.forward(
                        IValue.from(noiseTensor),
                        IValue.from(fallbackLabelTensor)
                    ).toTensor()
                } else {
                    throw e
                }
            }

            val floatOutputs = outputTensor.dataAsFloatArray
            val pixelCount = 64 * 64

            if (floatOutputs.size < 3 * pixelCount) {
                Log.e(TAG, "Model output float size (${floatOutputs.size}) is smaller than expected 3*64*64 (${3 * pixelCount})")
                return null
            }

            // 4. Convert float array output shape (3, 64, 64) with [-1, 1] range to colors
            val pixels = IntArray(pixelCount)
            for (i in 0 until pixelCount) {
                // Tanh output maps to [-1, 1]. Rescale to [0, 255]
                val r = ((floatOutputs[i] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val g = ((floatOutputs[i + pixelCount] + 1f) * 127.5f).toInt().coerceIn(0, 255)
                val b = ((floatOutputs[i + 2 * pixelCount] + 1f) * 127.5f).toInt().coerceIn(0, 255)

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

                    pixels[i] = (0xFF shl 24) or (rOut shl 16) or (gOut shl 8) or bOut
                } else {
                    pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                }
            }

            // 5. Build native 64x64 bitmap
            generatedTile = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "PyTorch Mobile generation failed", e)
            throw e
        }

        val duration = System.currentTimeMillis() - startTime
        if (generatedTile != null) {
            Log.d(TAG, "ML generating process SUCCEEDED | Duration: ${duration}ms | Size: 64x64")
        } else {
            Log.e(TAG, "ML generating process FAILED | Duration: ${duration}ms")
        }

        return generatedTile
    }

    /**
     * Translates descriptive words inside user-provided text prompts to class index indices.
     */
    private fun mapPromptToClassLabel(prompt: String): Long {
        val p = prompt.lowercase()
        return when {
            // User's custom dataset mapping (e.g. 3-class pirate/coins map)
            p.contains("coin") -> 0L
            p.contains("map") -> 1L
            p.contains("pirate") || p.contains("sea") || p.contains("ship") -> 2L

            // Default model class mapping
            p.contains("forest") || p.contains("green") || p.contains("tree") || p.contains("nature") -> 0L
            p.contains("cyber") || p.contains("neon") || p.contains("synth") || p.contains("future") -> 1L
            p.contains("space") || p.contains("star") || p.contains("galaxy") || p.contains("night") -> 2L
            p.contains("castle") || p.contains("dungeon") || p.contains("stone") || p.contains("retro") -> 3L
            p.contains("desert") || p.contains("sand") || p.contains("gold") || p.contains("sun") -> 4L
            p.contains("ocean") || p.contains("water") -> 5L
            p.contains("snow") || p.contains("ice") || p.contains("winter") || p.contains("cold") -> 6L
            p.contains("lava") || p.contains("fire") || p.contains("magma") || p.contains("red") -> 7L
            p.contains("candy") || p.contains("pink") || p.contains("sweet") || p.contains("cute") -> 8L
            else -> 0L // Highly robust default fallback to prevent out-of-range index crashes on smaller models!
        }
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
