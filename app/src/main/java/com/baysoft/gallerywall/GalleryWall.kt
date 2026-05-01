package com.baysoft.gallerywall

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * Wallpaper helpers and **scheduled refresh** via [WorkManager].
 *
 * Wallpapers are generated locally from colors in settings ([WallpaperGenerator]); no remote image API.
 */
class GalleryWall {

    companion object {

        private const val TAG = "GalleryWall"

        /** Unique name for [WorkManager.enqueueUniquePeriodicWork]. */
        internal const val UNIQUE_WORK_NAME = "gallery_wall_refresh"

        /**
         * Sent when a manual refresh finishes without applying wallpaper so the UI can dismiss
         * progress (see [com.baysoft.gallerywall.ui.HomeFragment]).
         */
        const val ACTION_REFRESH_IDLE = "com.baysoft.gallerywall.REFRESH_IDLE"

        /**
         * WorkManager enforces a minimum interval (~15 minutes). Call this when building periodic
         * requests so invalid shorter intervals from future preference changes never enqueue.
         */
        internal fun clampPeriodicIntervalMinutes(minutes: Long): Long {
            val minMinutes =
                TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)
            return maxOf(minutes, minMinutes)
        }

        fun cancelSchedule(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }

        fun schedule(context: Context, minutes: Long? = null) {
            val period = minutes
                ?: Settings(PreferenceManager.getDefaultSharedPreferences(context)).period
            val wm = WorkManager.getInstance(context.applicationContext)

            if (period > 0) {
                val intervalMinutes = clampPeriodicIntervalMinutes(period)
                val constraints = Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                    .build()

                val request = PeriodicWorkRequestBuilder<GalleryWallRefreshWorker>(
                    intervalMinutes,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
                    .build()

                wm.enqueueUniquePeriodicWork(
                    UNIQUE_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            } else {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            }
        }

        /** Renders the current wallpaper from [Settings] colors (solid or gradient). */
        fun createWallpaperBitmap(context: Context): Bitmap? {
            return try {
                WallpaperGenerator.createBitmap(context)
            } catch (e: Exception) {
                Log.w(TAG, "createWallpaperBitmap failed", e)
                null
            }
        }

        fun updateWallpaper(context: Context, image: Bitmap?) {
            image?.let {
                WallpaperManager.getInstance(context).setBitmap(image)
            }
        }

        // Save wallpaper to database as recent
        fun recordWallpaper(context: Context, image: Bitmap?) {
            GlobalScope.launch {
                image?.let { bmp ->
                    val file = java.io.File(context.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    file.absolutePath
                }?.let { filePath ->
                    val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
                    val repo = com.baysoft.gallerywall.data.WallpaperRepository(db.wallpaperDao())
                    repo.addWallpaper(filePath)

                    // Notify UI to update recents
                    context.sendBroadcast(Intent("com.baysoft.gallerywall.WALLPAPER_SET"))
                }
            }
        }
    }
}
