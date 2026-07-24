package com.baysoft.gallerywall.ml

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.opengl.EGL14
import android.opengl.GLES20
import android.util.Log

/**
 * Runtime GPU capability checker for MediaPipe Stable Diffusion requirements.
 *
 * Checks:
 * 1. OpenGL ES version ≥ 3.1
 * 2. GPU renderer is not on the known-unsupported list (e.g. Adreno 5xx and below)
 * 3. OpenCL library availability (required by MediaPipe diffuser GPU backend)
 * 4. Minimum RAM (4 GB is the bare minimum; 6 GB recommended)
 */
object GpuCapabilityChecker {

    private const val TAG = "GpuCapabilityChecker"

    /** Minimum OpenGL ES version required (3.1 = 0x00030001). */
    private const val MIN_GL_ES_VERSION = 0x00030001

    /** Minimum RAM in MB to even attempt loading the model. */
    private const val MIN_RAM_MB = 4_000L

    /**
     * Known GPU families that have OpenCL libraries but lack the required Qualcomm
     * extensions (clSetPerfHintQCOM) or have insufficient compute for SD 1.5.
     * Matched case-insensitively against the GL_RENDERER string.
     */
    private val UNSUPPORTED_GPU_PATTERNS = listOf(
        Regex("Adreno.*[( ]3\\d\\d", RegexOption.IGNORE_CASE),  // Adreno 3xx
        Regex("Adreno.*[( ]4\\d\\d", RegexOption.IGNORE_CASE),  // Adreno 4xx
        Regex("Adreno.*[( ]50\\d", RegexOption.IGNORE_CASE),    // Adreno 50x (e.g. 506, 508, 509)
        Regex("Adreno.*[( ]51\\d", RegexOption.IGNORE_CASE),    // Adreno 51x (e.g. 512)
        Regex("Mali-T", RegexOption.IGNORE_CASE),               // Mali-T series (old Midgard)
        Regex("Mali-4", RegexOption.IGNORE_CASE),               // Mali-400 series
        Regex("PowerVR", RegexOption.IGNORE_CASE),              // PowerVR GPUs — no OpenCL for MediaPipe
    )

    data class GpuCheckResult(
        val isSupported: Boolean,
        val reason: String? = null,
        val glEsVersion: String = "unknown",
        val gpuRenderer: String = "unknown",
        val hasOpenCL: Boolean = false,
        val totalRamMb: Long = 0
    )

    /**
     * Performs all GPU/device capability checks and returns a result indicating whether
     * this device can run MediaPipe Stable Diffusion.
     */
    fun check(context: Context): GpuCheckResult {
        // 1. Check OpenGL ES version
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val configInfo: ConfigurationInfo? = activityManager?.deviceConfigurationInfo
        val glEsVersionInt = configInfo?.reqGlEsVersion ?: 0
        val glEsVersionString = configInfo?.glEsVersion ?: "unknown"

        if (glEsVersionInt < MIN_GL_ES_VERSION) {
            val reason = "Device GPU does not support OpenGL ES 3.1 (found: $glEsVersionString). " +
                "On-device AI requires OpenGL ES 3.1 or higher."
            Log.w(TAG, reason)
            return GpuCheckResult(
                isSupported = false,
                reason = reason,
                glEsVersion = glEsVersionString
            )
        }

        // 2. Check GPU renderer against known-unsupported list
        val gpuRenderer = getGpuRenderer()
        Log.d(TAG, "GPU renderer: $gpuRenderer")

        val unsupportedMatch = UNSUPPORTED_GPU_PATTERNS.firstOrNull { it.containsMatchIn(gpuRenderer) }
        if (unsupportedMatch != null) {
            val reason = "This device's GPU ($gpuRenderer) is not powerful enough for on-device AI image generation. " +
                "A newer GPU (Adreno 6xx or higher, Mali-G series, etc.) is required."
            Log.w(TAG, reason)
            return GpuCheckResult(
                isSupported = false,
                reason = reason,
                glEsVersion = glEsVersionString,
                gpuRenderer = gpuRenderer
            )
        }

        // 3. Check for OpenCL library availability
        val hasOpenCL = checkOpenCLAvailability()
        if (!hasOpenCL) {
            val reason = "OpenCL is not available on this device. " +
                "On-device AI requires GPU acceleration via OpenCL."
            Log.w(TAG, reason)
            return GpuCheckResult(
                isSupported = false,
                reason = reason,
                glEsVersion = glEsVersionString,
                gpuRenderer = gpuRenderer,
                hasOpenCL = false
            )
        }

        // 4. Check available RAM
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager?.getMemoryInfo(memoryInfo)
        val totalRamMb = memoryInfo.totalMem / (1024 * 1024)

        if (totalRamMb < MIN_RAM_MB) {
            val reason = "Device has insufficient RAM (${totalRamMb} MB). " +
                "On-device AI requires at least ${MIN_RAM_MB} MB of RAM."
            Log.w(TAG, reason)
            return GpuCheckResult(
                isSupported = false,
                reason = reason,
                glEsVersion = glEsVersionString,
                gpuRenderer = gpuRenderer,
                hasOpenCL = true,
                totalRamMb = totalRamMb
            )
        }

        Log.i(TAG, "GPU capability check PASSED: GLES=$glEsVersionString, GPU=$gpuRenderer, OpenCL=true, RAM=${totalRamMb}MB")
        return GpuCheckResult(
            isSupported = true,
            glEsVersion = glEsVersionString,
            gpuRenderer = gpuRenderer,
            hasOpenCL = true,
            totalRamMb = totalRamMb
        )
    }

    /**
     * Retrieves the GPU renderer string using EGL14.
     * Creates a temporary EGL context to query GL_RENDERER if needed.
     */
    private fun getGpuRenderer(): String {
        return try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (display == EGL14.EGL_NO_DISPLAY) return "unknown"

            val version = IntArray(2)
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) return "unknown"

            val configAttr = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<android.opengl.EGLConfig>(1)
            val numConfig = IntArray(1)
            EGL14.eglChooseConfig(display, configAttr, 0, configs, 0, 1, numConfig, 0)

            if (numConfig[0] == 0 || configs[0] == null) {
                EGL14.eglTerminate(display)
                return "unknown"
            }

            val contextAttr = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
            val eglContext = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttr, 0)

            val surfaceAttr = intArrayOf(EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE)
            val surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttr, 0)

            EGL14.eglMakeCurrent(display, surface, surface, eglContext)

            val renderer = GLES20.glGetString(GLES20.GL_RENDERER) ?: "unknown"

            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(display, surface)
            EGL14.eglDestroyContext(display, eglContext)
            EGL14.eglTerminate(display)

            renderer
        } catch (e: Exception) {
            Log.w(TAG, "Failed to query GPU renderer", e)
            "unknown"
        }
    }

    /**
     * Attempts to detect OpenCL availability by checking for known OpenCL library paths.
     * This is a best-effort heuristic — the definitive test happens when MediaPipe
     * tries to load the library at runtime.
     */
    private fun checkOpenCLAvailability(): Boolean {
        val openClLibPaths = listOf(
            "/system/lib64/libOpenCL.so",
            "/system/lib/libOpenCL.so",
            "/system/vendor/lib64/libOpenCL.so",
            "/system/vendor/lib/libOpenCL.so",
            "/vendor/lib64/libOpenCL.so",
            "/vendor/lib/libOpenCL.so"
        )

        for (path in openClLibPaths) {
            if (java.io.File(path).exists()) {
                Log.d(TAG, "OpenCL library found at: $path")
                return true
            }
        }

        // Fallback: try System.loadLibrary (catches cases where library exists but in non-standard path)
        return try {
            System.loadLibrary("OpenCL")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.d(TAG, "OpenCL library not found via System.loadLibrary: ${e.message}")
            false
        }
    }
}
