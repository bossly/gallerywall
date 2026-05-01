package com.baysoft.gallerywall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
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

    private fun gradientPositions(n: Int): FloatArray {
        if (n < 2) return floatArrayOf(0f, 1f)
        return FloatArray(n) { i -> i.toFloat() / (n - 1) }
    }
}
