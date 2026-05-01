package com.baysoft.gallerywall

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs scheduled wallpaper refresh under [WorkManager].
 *
 * Generates wallpaper locally via [GalleryWall.createWallpaperBitmap]; no network.
 */
class GalleryWallRefreshWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val context = applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)

        val bitmap = GalleryWall.createWallpaperBitmap(context)
        if (bitmap == null) {
            Log.w(TAG, "No bitmap produced")
            return@withContext Result.retry()
        }

        GalleryWall.updateWallpaper(context, bitmap)
        GalleryWall.recordWallpaper(context, bitmap)

        if (settings.notification) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            val notification = GalleryWallNotifications.buildRefreshNotification(context, bitmap)
            notificationManager.notify(GalleryWallNotifications.NOTIFICATION_ID, notification)
        }

        Result.success()
    }

    companion object {
        private const val TAG = "GalleryWallRefreshWorker"
    }
}
