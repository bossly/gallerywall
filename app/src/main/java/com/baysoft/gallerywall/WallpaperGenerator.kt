package com.baysoft.gallerywall

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import androidx.preference.PreferenceManager

/**
 * Builds wallpaper bitmaps from one or more colors (solid fill or vertical linear gradient).
 */
object WallpaperGenerator {

    fun createBitmap(context: Context): Bitmap {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val colors = parseColors(Settings(prefs).generatedColorsHex)
        val (w, h) = resolveSize(context)
        return render(w, h, colors)
    }

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

    private fun resolveSize(context: Context): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val w = dm.widthPixels.coerceAtLeast(480)
        val h = dm.heightPixels.coerceAtLeast(800)
        return w to h
    }

    private fun render(w: Int, h: Int, colors: List<Int>): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (colors.size == 1) {
            canvas.drawColor(colors[0])
            return bmp
        }
        val arr = colors.toIntArray()
        val positions = gradientPositions(colors.size)
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
        if (n < 2) return floatArrayOf(0f)
        return FloatArray(n) { i -> i.toFloat() / (n - 1) }
    }
}
