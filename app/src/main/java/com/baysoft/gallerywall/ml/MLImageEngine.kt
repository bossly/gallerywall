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
 * Loads a TensorFlow Lite (.tflite) diffusion model to generate pixel art sprite grids
 * locally in milliseconds, with zero JNI NDK compiler dependencies.
 */
class MLImageEngine private constructor() {

    private var interpreter: Interpreter? = null
    private var loadedModelPath: String? = null

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
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading TensorFlow Lite model from asset: $assetName", e)
            false
        }
    }

    /**
     * Generates a square seamless pixel-art tile bitmap based on a text prompt and active color configurations
     * using a 5-step DDPM (Denoising Diffusion Probabilistic Model) loop.
     * @param prompt The descriptive text prompt for the texture (unused for unconditional diffusion).
     * @param colors The list of user-configured colors to map or tint the sprite palette.
     * @param size Unused since output size is read dynamically from the model's dimensions.
     * @param steps Unused since the optimized diffusion loop runs for 5 steps.
     * @param seed Random seed for reproducibility. Set to -1 for random.
     * @param circular Unused.
     * @param supportTransparency Whether to preserve generated alpha transparency or force fully opaque.
     */
    fun generateTile(
        prompt: String,
        colors: List<Int>,
        size: Int = 32,
        steps: Int = 5,
        seed: Int = -1,
        circular: Boolean = true,
        supportTransparency: Boolean = true
    ): Bitmap? {
        val modelName = loadedModelPath?.let { File(it).name } ?: "Default (Assets)"
        Log.d(TAG, "ML generating process started | Model: $modelName | Seed: $seed | Steps: 5")

        val startTime = System.currentTimeMillis()
        var generatedTile: Bitmap? = null

        try {
            if (interpreter == null) {
                Log.e(TAG, "Cannot generate: TensorFlow Lite model is not loaded!")
                return null
            }

            // 1. Read the output tensor dimensions dynamically (supports 32x32, 64x64, etc.)
            val outputTensor = interpreter!!.getOutputTensor(0)
            val shape = outputTensor.shape() // Shape: [1, H, W, 4]
            val height = shape[1]
            val width = shape[2]
            Log.d(TAG, "Dynamic model output shape detected: ${height}x${width} pixels")

            // 2. Initialize x_t with Gaussian noise N(0, 1) of shape [1, H, W, 4]
            val rand = if (seed == -1) {
                java.util.Random()
            } else {
                java.util.Random(seed.toLong())
            }
            val x_t = Array(1) { Array(height) { Array(width) { FloatArray(4) } } }
            for (y in 0 until height) {
                for (x in 0 until width) {
                    for (c in 0 until 4) {
                        x_t[0][y][x][c] = rand.nextGaussian().toFloat()
                    }
                }
            }

            // 3. Precompute DDPM schedule parameters for T=5 steps
            val T = 5
            val betas = FloatArray(T) { i -> 0.1f + 0.8f * (i.toFloat() / (T - 1)) }
            val alphas = FloatArray(T) { i -> 1.0f - betas[i] }
            val alphasCumprod = FloatArray(T)
            var prod = 1.0f
            for (i in 0 until T) {
                prod *= alphas[i]
                alphasCumprod[i] = prod
            }
            val alphasCumprodPrev = FloatArray(T) { i -> if (i == 0) 1.0f else alphasCumprod[i - 1] }

            val sqrtRecipAlphas = FloatArray(T) { i -> Math.sqrt(1.0 / alphas[i].toDouble()).toFloat() }
            val sqrtOneMinusAlphasCumprod = FloatArray(T) { i -> Math.sqrt(1.0 - alphasCumprod[i].toDouble()).toFloat() }
            val posteriorVariance = FloatArray(T) { i ->
                (betas[i] * (1.0f - alphasCumprodPrev[i]) / (1.0f - alphasCumprod[i]))
            }
            val sqrtPosteriorVariance = FloatArray(T) { i -> Math.sqrt(posteriorVariance[i].toDouble()).toFloat() }

            // 4. Map model input tensor indices dynamically by name
            val inputCount = interpreter!!.getInputTensorCount()
            var xTIndex = 0
            var tIndex = 1
            var labelIndex = -1

            for (i in 0 until inputCount) {
                val name = interpreter!!.getInputTensor(i).name().lowercase()
                if (name.contains("x_t") || name.contains("image") || name.contains("input")) {
                    xTIndex = i
                } else if (name.contains("t") || name.contains("step")) {
                    tIndex = i
                } else if (name.contains("label") || name.contains("class")) {
                    labelIndex = i
                }
            }

            val noisePred = Array(1) { Array(height) { Array(width) { FloatArray(4) } } }

            // 5. Run the 5-step DDPM sampling loop
            for (t in T - 1 downTo 0) {
                val tInput = Array(1) { IntArray(1) { t } }
                val inputs = arrayOfNulls<Any>(inputCount)
                inputs[xTIndex] = x_t
                inputs[tIndex] = tInput
                if (labelIndex != -1) {
                    inputs[labelIndex] = Array(1) { IntArray(1) { 0 } } // unconditional/default label
                }

                val outputs = mapOf(0 to noisePred)
                interpreter!!.runForMultipleInputsOutputs(inputs, outputs)

                // Update x_t -> x_{t-1} using DDPM sampler step with intermediate clipping
                val recipAlpha = sqrtRecipAlphas[t]
                val betaTerm = betas[t] / sqrtOneMinusAlphasCumprod[t]
                val sigma = if (t > 0) sqrtPosteriorVariance[t] else 0.0f

                for (y in 0 until height) {
                    for (x in 0 until width) {
                        for (c in 0 until 4) {
                            val xtVal = x_t[0][y][x][c]
                            val epsVal = noisePred[0][y][x][c]
                            val z = if (t > 0) rand.nextGaussian().toFloat() else 0.0f
                            val nextVal = recipAlpha * (xtVal - betaTerm * epsVal) + sigma * z
                            x_t[0][y][x][c] = nextVal.coerceIn(-1.0f, 1.0f)
                        }
                    }
                }
            }

            // 6. Convert the generated final x_0 tensor [-1, 1] into a dynamic pixel array
            val pixelCount = height * width
            val pixels = IntArray(pixelCount)

            for (y in 0 until height) {
                for (x in 0 until width) {
                    val i = y * width + x
                    val rVal = x_t[0][y][x][0]
                    val gVal = x_t[0][y][x][1]
                    val bVal = x_t[0][y][x][2]
                    val aVal = x_t[0][y][x][3]

                    // Rescale output [-1, 1] to [0, 255]
                    val r = ((rVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val g = ((gVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val b = ((bVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    val a = if (supportTransparency) {
                        ((aVal + 1f) * 127.5f).toInt().coerceIn(0, 255)
                    } else {
                        255
                    }

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

            // 7. Build dynamic sized bitmap
            generatedTile = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)

        } catch (e: Exception) {
            Log.e(TAG, "TensorFlow Lite DDPM generation failed", e)
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
