package com.baysoft.gallerywall.provider

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.Log
import com.baysoft.gallerywall.R
import java.util.Random

/**
 * Debug-only wallpaper provider that generates a random color gradient wallpaper.
 * Visible and set as default when the app is built in debug mode.
 */
object ColorProvider : WallpaperProvider {
    private const val TAG = "ColorProvider"

    @Volatile
    private var isCancelled = false

    override val id: String = "random_color"

    override val titleRes: Int = R.string.provider_color_title
    override val summaryRes: Int = R.string.provider_color_summary

    override fun generateBitmap(context: Context, onStateUpdate: (ProviderState) -> Unit): Bitmap {
        isCancelled = false
        for (i in 1..5) {
            if (isCancelled) {
                throw java.util.concurrent.CancellationException("Generation stopped by user")
            }
            val progress = i / 5.0f
            onStateUpdate(DefaultProviderState(
                progress = progress,
                message = "Generating gradient: step $i/5"
            ))
            try {
                Thread.sleep(1000)
            } catch (e: InterruptedException) {
                throw java.util.concurrent.CancellationException("Generation interrupted")
            }
        }
        if (isCancelled) {
            throw java.util.concurrent.CancellationException("Generation stopped by user")
        }

        val metrics = context.resources.displayMetrics
        val width = metrics.widthPixels.coerceAtLeast(1080)
        val height = metrics.heightPixels.coerceAtLeast(1920)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val rng = Random()
        val color1 = (0xFF000000 or rng.nextInt(0x1000000).toLong()).toInt()
        val color2 = (0xFF000000 or rng.nextInt(0x1000000).toLong()).toInt()
        val color3 = (0xFF000000 or rng.nextInt(0x1000000).toLong()).toInt()

        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            intArrayOf(color1, color2, color3),
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)

        Log.i(TAG, "Generated random gradient wallpaper: ${width}×${height} " +
                "colors=#${Integer.toHexString(color1)}, #${Integer.toHexString(color2)}, #${Integer.toHexString(color3)}")

        val finalState = DefaultProviderState(
            progress = 1.0f,
            result = bitmap,
            message = "Done"
        )
        onStateUpdate(finalState)
        return bitmap
    }

    override fun isReady(context: Context): ProviderReadiness = ProviderReadiness.PROMPT

    override fun stop(context: Context) {
        isCancelled = true
    }
}

