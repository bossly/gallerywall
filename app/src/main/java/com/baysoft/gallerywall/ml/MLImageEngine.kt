package com.baysoft.gallerywall.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * On-device ML image generator interface using TensorFlow Lite.
 * Loads a TensorFlow Lite (.tflite) model to generate 64x64 pixel art sprite grids
 * locally in milliseconds, with zero JNI NDK compiler dependencies.
 */
class MLImageEngine private constructor() {

    private var interpreter: Interpreter? = null
    private var loadedModelPath: String? = null
    private var classMapping: Map<String, Long>? = null

    /**
     * Parses and loads prompt-to-class-index map from JSON strings.
     */
    fun loadMapping(jsonString: String) {
        try {
            val mapping = mutableMapOf<String, Long>()
            val jsonObject = org.json.JSONObject(jsonString)
            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                mapping[key.lowercase()] = jsonObject.getLong(key)
            }
            classMapping = mapping
            Log.i(TAG, "Successfully loaded class style mapping: $classMapping")
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing class mapping JSON", e)
        }
    }

    /**
     * Maps an asset file descriptor into memory.
     */
    private fun loadModelFileFromAsset(context: Context, assetName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(assetName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    /**
     * Loads the TensorFlow Lite model from the given local file path.
     */
    fun loadModel(modelPath: String): Boolean {
        if (!File(modelPath).exists()) {
            Log.e(TAG, "Model file does not exist at: $modelPath")
            return false
        }

        if (loadedModelPath == modelPath && interpreter != null) {
            return true
        }

        return try {
            val options = Interpreter.Options()
            interpreter = Interpreter(File(modelPath), options)
            loadedModelPath = modelPath
            Log.i(TAG, "TensorFlow Lite model successfully loaded from path: $modelPath")
            
            // Try loading companion style mapper JSON
            val jsonFile = File(modelPath.replace(".tflite", ".json", ignoreCase = true))
            if (jsonFile.exists()) {
                loadMapping(jsonFile.readText())
            } else {
                classMapping = null
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model from: $modelPath", e)
            false
        }
    }

    /**
     * Loads the TensorFlow Lite model directly from the APK's assets folder.
     */
    fun loadModelFromAsset(context: Context, assetName: String): Boolean {
        val cacheKey = "asset://$assetName"
        if (loadedModelPath == cacheKey && interpreter != null) {
            return true
        }

        return try {
            val buffer = loadModelFileFromAsset(context, assetName)
            val options = Interpreter.Options()
            interpreter = Interpreter(buffer, options)
            loadedModelPath = cacheKey
            Log.i(TAG, "TensorFlow Lite model successfully loaded from assets: $assetName")
            
            // Try loading companion style mapper JSON from assets
            try {
                val jsonAssetName = assetName.replace(".tflite", ".json", ignoreCase = true)
                val jsonStr = context.assets.open(jsonAssetName).bufferedReader().use { it.readText() }
                loadMapping(jsonStr)
            } catch (e: Exception) {
                classMapping = null
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model from asset: $assetName", e)
            false
        }
    }

    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt and active color configurations.
     * @param prompt The descriptive text prompt for the texture.
     * @param colors The list of user-configured colors to map or tint the sprite palette.
     * @param size Resolution size: unused since GAN outputs native 64x64 grids.
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
            if (interpreter == null) {
                Log.e(TAG, "Cannot generate: TensorFlow Lite model is not loaded!")
                return null
            }

            // 1. Generate standard latent vector z ~ N(0, 1) of size 100
            val rand = if (seed == -1) {
                java.util.Random()
            } else {
                java.util.Random(seed.toLong())
            }
            val noiseInput = Array(1) { FloatArray(100) { rand.nextGaussian().toFloat() } }

            // 2. Resolve the text prompt into a style class index (0-9)
            val classLabel = mapPromptToClassLabel(prompt)
            val labelInput = Array(1) { IntArray(1) { classLabel.toInt() } }

            Log.d(TAG, "Resolved prompt '$prompt' to class index $classLabel")

            // 3. Prepare output buffer: TFLite model output has HWC shape [1, 64, 64, 4]
            val outputBuffer = Array(1) { Array(64) { Array(64) { FloatArray(4) } } }

            val inputs = arrayOf(noiseInput, labelInput)
            val outputs = mapOf(0 to outputBuffer)

            // 4. Run TFLite inference
            try {
                interpreter!!.runForMultipleInputsOutputs(inputs, outputs)
            } catch (e: Exception) {
                if (classLabel != 0L) {
                    Log.w(TAG, "TFLite forward pass failed for label $classLabel. Retrying with fallback index 0.")
                    val fallbackLabelInput = Array(1) { IntArray(1) { 0 } }
                    val fallbackInputs = arrayOf(noiseInput, fallbackLabelInput)
                    interpreter!!.runForMultipleInputsOutputs(fallbackInputs, outputs)
                } else {
                    throw e
                }
            }

            val pixelCount = 64 * 64
            val pixels = IntArray(pixelCount)

            // 5. Map HWC float output array [-1, 1] to pixel colors with RGBA transparency
            for (y in 0 until 64) {
                for (x in 0 until 64) {
                    val i = y * 64 + x
                    val rVal = outputBuffer[0][y][x][0]
                    val gVal = outputBuffer[0][y][x][1]
                    val bVal = outputBuffer[0][y][x][2]
                    val aVal = outputBuffer[0][y][x][3]

                    // Rescale tanh output [-1, 1] to [0, 255]
                    val r = ((rVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val g = ((gVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val b = ((bVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val a = ((aVal + 1f) * 127.5f).toInt().coerceIn(0, 255)

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
            }

            // 6. Build native 64x64 bitmap
            generatedTile = Bitmap.createBitmap(pixels, 64, 64, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "TensorFlow Lite generation failed", e)
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

    private fun mapPromptToClassLabel(prompt: String): Long {
        val p = prompt.lowercase()
        
        // 1. Check dynamic JSON class mapping first if loaded
        classMapping?.let { mapping ->
            for ((keyword, label) in mapping) {
                if (p.contains(keyword)) {
                    return label
                }
            }
        }

        // 2. Fallback to hardcoded default mapping
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
