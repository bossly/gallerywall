package com.baysoft.gallerywall

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.util.Log
import androidx.preference.PreferenceManager
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

        fun applyIntent(context: Context, filePath: String): Intent =
            Intent(context, GalleryWallReceiver::class.java).apply {
                action = GalleryWall.ACTION_APPLY_WALLPAPER
                putExtra(GalleryWall.EXTRA_FILE_PATH, filePath)
            }

        fun stopIntent(context: Context): Intent =
            Intent(context, GalleryWallReceiver::class.java).apply {
                action = GalleryWall.ACTION_STOP_GENERATION
            }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                context?.let {
                    GalleryWall.schedule(it)
                }
            }
            GalleryWall.ACTION_STOP_GENERATION -> {
                context?.let { ctx ->
                    Log.i("GalleryWallReceiver", "Stop generation broadcast received.")
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    val settings = Settings(prefs)
                    val providerId = settings.activeProviderId
                    val provider = com.baysoft.gallerywall.provider.WallpaperProviderRegistry.get(providerId)
                    provider?.stop(ctx)
                    
                    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(GalleryWallNotifications.PROGRESS_NOTIFICATION_ID)
                }
            }
            GalleryWall.ACTION_APPLY_WALLPAPER -> {
                val filePath = intent.getStringExtra(GalleryWall.EXTRA_FILE_PATH) ?: return
                context?.let { ctx ->
                    GlobalScope.launch {
                        try {
                            val bitmap = BitmapFactory.decodeFile(filePath)
                            if (bitmap != null) {
                                GalleryWall.updateWallpaper(ctx, bitmap)
                                GalleryWall.rememberAppliedWallpaperPath(ctx, filePath)
                                ctx.sendBroadcast(Intent("com.baysoft.gallerywall.WALLPAPER_SET"))
                                Log.i("GalleryWallReceiver", "Wallpaper applied from notification: $filePath")
                                
                                // Dismiss notification after Apply
                                val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                nm.cancel(GalleryWallNotifications.NOTIFICATION_ID)
                            }
                        } catch (e: Exception) {
                            Log.e("GalleryWallReceiver", "Failed to apply wallpaper from file", e)
                        }
                    }
                }
            }
            else -> {
                context?.let { ctx ->
                    val handler = CoroutineExceptionHandler { _, e ->
                        Log.e("GalleryWallReceiver", "refresh failed", e)
                        ctx.sendBroadcast(Intent(GalleryWall.ACTION_REFRESH_IDLE))
                    }
                    GlobalScope.launch(handler) {
                        // Dismiss notification before starting new generation (Retry)
                        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        nm.cancel(GalleryWallNotifications.NOTIFICATION_ID)

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
