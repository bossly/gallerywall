package com.baysoft.gallerywall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

/**
 * Handles [Intent.ACTION_BOOT_COMPLETED] by reinstalling WorkManager periodic work via [GalleryWall.schedule],
 * and explicit refresh intents that generate and apply a new wallpaper from settings.
 */
class GalleryWallReceiver : BroadcastReceiver() {

    companion object {
        fun updateIntent(context: Context): Intent =
            Intent(context, GalleryWallReceiver::class.java)
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                context?.let {
                    GalleryWall.schedule(it)
                }
            }
            else -> {
                context?.let { ctx ->
                    val handler = CoroutineExceptionHandler { _, e ->
                        Log.e("GalleryWallReceiver", "refresh failed", e)
                        ctx.sendBroadcast(Intent(GalleryWall.ACTION_REFRESH_IDLE))
                    }
                    GlobalScope.launch(handler) {
                        val bitmap = GalleryWall.createWallpaperBitmap(ctx)
                        if (bitmap == null) {
                            ctx.sendBroadcast(Intent(GalleryWall.ACTION_REFRESH_IDLE))
                            return@launch
                        }
                        GalleryWall.updateWallpaper(ctx, bitmap)
                        GalleryWall.recordWallpaper(ctx, bitmap)
                    }
                }
            }
        }
    }
}
