package com.baysoft.gallerywall

import android.app.WallpaperManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.util.Log
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
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
 * Wallpapers are generated locally via [com.baysoft.gallerywall.provider.WallpaperProvider]; no remote image API.
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

        internal fun buildWorkConstraints(settings: Settings): Constraints {
            val builder = Constraints.Builder()
            if (settings.constraintWifi) {
                builder.setRequiredNetworkType(NetworkType.UNMETERED)
            } else {
                builder.setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            }
            if (settings.constraintCharging) {
                builder.setRequiresCharging(true)
            }
            if (settings.constraintIdle) {
                builder.setRequiresDeviceIdle(true)
            }
            return builder.build()
        }

        fun schedule(context: Context, minutes: Long? = null) {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            Settings.migrateLegacyPrefsIfNeeded(prefs)
            val settings = Settings(prefs)
            val wm = WorkManager.getInstance(context.applicationContext)

            val period = settings.period
            if (!settings.autoWallpaperEnabled || period <= 0) {
                wm.cancelUniqueWork(UNIQUE_WORK_NAME)
                return
            }

            val intervalMinutes = if (minutes != null) {
                minutes
            } else {
                when (settings.periodUnit) {
                    "HOURS" -> period * 60L
                    "DAYS" -> period * 24L * 60L
                    "WEEKS" -> period * 7L * 24L * 60L
                    "MONTHS" -> period * 30L * 24L * 60L
                    else -> period
                }
            }

            val clampedMinutes = clampPeriodicIntervalMinutes(intervalMinutes)
            val constraints = buildWorkConstraints(settings)

            val request = PeriodicWorkRequestBuilder<GalleryWallRefreshWorker>(
                clampedMinutes,
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
        }

        /** Renders the current wallpaper using the active provider from [Settings]. */
        fun createWallpaperBitmap(context: Context): Bitmap {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val settings = Settings(prefs)
            val providerId = settings.activeProviderId
            val colors = settings.generatedColorsHex
            
            val promptStr = if (providerId == "local_ai") {
                val rawPrompt = settings.automationPrompt
                val resolvedPrompt = try {
                    com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, rawPrompt)
                } catch (e: Exception) {
                    rawPrompt
                }
                "Raw: \"$rawPrompt\", Resolved: \"$resolvedPrompt\""
            } else {
                "None"
            }
            
            Log.i(TAG, "Wallpaper generation started | Mode: $providerId | Colors: $colors | Prompt: $promptStr")

            val provider = WallpaperProviderRegistry.get(providerId)
                ?: WallpaperProviderRegistry.defaultProvider
            return provider.generateBitmap(context)
        }

        fun updateWallpaper(context: Context, image: Bitmap?) {
            image?.let {
                WallpaperManager.getInstance(context).setBitmap(image)
            }
        }

        /** Persists which generated file was last applied as the system wallpaper (for UI indicator). */
        fun rememberAppliedWallpaperPath(context: Context, absolutePath: String?) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putString(Settings.PREF_LAST_APPLIED_WALLPAPER_PATH, absolutePath)
                .apply()
        }

        // Save wallpaper to database as recent
        fun recordWallpaper(context: Context, image: Bitmap?) {
            GlobalScope.launch {
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val settings = Settings(prefs)
                val providerId = settings.activeProviderId
                image?.let { bmp ->
                    val file = java.io.File(context.filesDir, "wallpaper_${providerId}_${System.currentTimeMillis()}.jpg")
                    java.io.FileOutputStream(file).use { out ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                    }
                    file.absolutePath
                }?.let { filePath ->
                    rememberAppliedWallpaperPath(context.applicationContext, filePath)
                    val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
                    val repo = com.baysoft.gallerywall.data.WallpaperRepository(db.wallpaperDao())
                    
                    val promptStr = if (providerId == "local_ai" || providerId == "procedural") {
                        try {
                            com.baysoft.gallerywall.ml.DynamicPromptParser.parse(context, settings.automationPrompt)
                        } catch (e: Exception) {
                            settings.automationPrompt
                        }
                    } else ""
                    
                    repo.addWallpaper(filePath, providerId, promptStr)

                    // Notify UI to update recents
                    context.sendBroadcast(Intent("com.baysoft.gallerywall.WALLPAPER_SET"))
                }
            }
        }
    }
}
