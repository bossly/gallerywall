package com.baysoft.gallerywall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RenderEffect
import android.graphics.Shader

/**
 * Shared rendering helpers for [com.baysoft.gallerywall.provider.WallpaperProvider] implementations.
 */
object WallpaperGenerator {

    /** Hex string `#RRGGBB` for prefs/UI (opaque). */
    fun colorToHexString(rgb: Int): String =
        String.format("#%06X", 0xFFFFFF and rgb)

    internal fun parseColors(hexLine: String): List<Int> {
        val parsed = hexLine.split(',').mapNotNull { segment ->
            val s = segment.trim()
            if (s.isEmpty()) return@mapNotNull null
            try {
                android.graphics.Color.parseColor(if (s.startsWith("#")) s else "#$s")
            } catch (_: IllegalArgumentException) {
                null
            }
        }
        return parsed.ifEmpty { defaultColors() }
    }

    private fun defaultColors(): List<Int> {
        return listOf(
            android.graphics.Color.parseColor("#6750A4"),
            android.graphics.Color.parseColor("#625B71"),
            android.graphics.Color.parseColor("#7D5260")
        )
    }

    internal fun resolveSize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(480)
        val h = dm.heightPixels.coerceAtLeast(800)
        return w to h
    }

    internal fun renderSolid(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        return bmp
    }

    internal fun renderGradient(w: Int, h: Int, colors: List<Int>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val stops = when {
            colors.isEmpty() -> defaultColors()
            colors.size == 1 -> listOf(colors[0], colors[0])
            else -> colors
        }
        val arr = stops.toIntArray()
        val positions = gradientPositions(stops.size)
        val shader = LinearGradient(
            0f,
            0f,
            0f,
            h.toFloat(),
            arr,
            positions,
            Shader.TileMode.CLAMP
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { setShader(shader) }
        canvas.drawPaint(paint)
        return bmp
    }

    internal fun renderTiledWallpaper(context: Context, tile: Bitmap): Bitmap {
        val (w, h) = resolveSize(context)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply {
            isAntiAlias = false
            isFilterBitmap = false
            shader = android.graphics.BitmapShader(tile, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        return bmp
    }

    private fun gradientPositions(n: Int): FloatArray {
        if (n < 2) return floatArrayOf(0f, 1f)
        return FloatArray(n) { i -> i.toFloat() / (n - 1) }
    }

    /** Applies post-processing filter to the [bitmap]. */
    fun applyPostProcessing(bitmap: Bitmap, filter: String): Bitmap {
        return when (filter) {
            "bw" -> applyColorMatrix(bitmap, ColorMatrix().apply { setSaturation(0f) })
            "sepia" -> applyColorMatrix(bitmap, ColorMatrix().apply {
                setSaturation(0f)
                postConcat(ColorMatrix(floatArrayOf(
                    1f, 0f, 0f, 0f, 30f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 0.8f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )))
            })
            "invert" -> applyColorMatrix(bitmap, ColorMatrix(floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )))
            "blur" -> {
                val outBmp = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
                val canvas = Canvas(outBmp)
                val paint = Paint()
                try {
                    val method = paint.javaClass.getMethod("setRenderEffect", RenderEffect::class.java)
                    method.invoke(paint, RenderEffect.createBlurEffect(15f, 15f, Shader.TileMode.CLAMP))
                } catch (e: Exception) {
                    // Fallback or ignore if reflection fails
                }
                canvas.drawBitmap(bitmap, 0f, 0f, paint)
                outBmp
            }
            else -> bitmap
        }
    }

    private fun applyColorMatrix(bitmap: Bitmap, matrix: ColorMatrix): Bitmap {
        val outBmp = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config ?: Bitmap.Config.ARGB_8888)
        val canvas = Canvas(outBmp)
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return outBmp
    }
}

