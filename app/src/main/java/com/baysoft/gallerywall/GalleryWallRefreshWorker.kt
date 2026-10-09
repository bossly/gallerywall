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
        Log.d(TAG, "Automated wallpaper refresh worker triggered")
        val context = applicationContext
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)
        val startTime = System.currentTimeMillis()

        val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
        val historyRepo = com.baysoft.gallerywall.data.AutomationHistoryRepository(db.automationHistoryDao())

        val evaluation = AutomationLogger.evaluateConstraints(context)
        val initialRecord = AutomationLogger.createInitialRecord(context, evaluation, startTime)
        val recordId = historyRepo.logRecord(initialRecord)

        if (!evaluation.canStart) {
            Log.i(TAG, "Skipping wallpaper refresh: ${evaluation.skipReason}")
            return@withContext Result.success()
        }

        try {
            val bitmap = GalleryWall.createWallpaperBitmap(context)
            if (bitmap == null) {
                Log.w(TAG, "No bitmap produced")
                val duration = System.currentTimeMillis() - startTime
                historyRepo.updateRecord(
                    initialRecord.copy(
                        id = recordId,
                        status = com.baysoft.gallerywall.data.AutomationHistoryEntity.STATUS_FAILED,
                        statusReason = context.getString(R.string.error_generation_failed),
                        durationMs = duration
                    )
                )
                return@withContext Result.retry()
            }

            val autoApply = settings.autoApplyWallpaper
            if (autoApply) {
                GalleryWall.updateWallpaper(context, bitmap)
            }

            val filePath = GalleryWall.recordWallpaperSync(context, bitmap, applied = autoApply)

            if (settings.notification) {
                val notificationManager =
                    context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                val notification = GalleryWallNotifications.buildRefreshNotification(
                    context,
                    bitmap,
                    filePath = filePath,
                    isAlreadyApplied = autoApply
                )
                notificationManager.notify(GalleryWallNotifications.NOTIFICATION_ID, notification)
            }

            val duration = System.currentTimeMillis() - startTime
            historyRepo.updateRecord(
                initialRecord.copy(
                    id = recordId,
                    status = com.baysoft.gallerywall.data.AutomationHistoryEntity.STATUS_SUCCESS,
                    statusReason = context.getString(R.string.reason_success),
                    wallpaperFilePath = filePath,
                    durationMs = duration
                )
            )

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Automated wallpaper refresh failed", e)
            val duration = System.currentTimeMillis() - startTime
            historyRepo.updateRecord(
                initialRecord.copy(
                    id = recordId,
                    status = com.baysoft.gallerywall.data.AutomationHistoryEntity.STATUS_FAILED,
                    statusReason = e.message ?: context.getString(R.string.error_generation_failed),
                    durationMs = duration
                )
            )
            Result.retry()
        }
    }

    private fun isBatteryLowOrSaving(context: Context): Boolean {
        // 1. Check system power saver mode
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (powerManager?.isPowerSaveMode == true) {
            return true
        }

        // 2. Check battery level percentage
        val filter = android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        val batteryStatus = context.registerReceiver(null, filter)
        val level = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level >= 0 && scale > 0) {
            val percentage = (level / scale.toFloat()) * 100
            if (percentage < 15.0f) {
                return true
            }
        }
        return false
    }

    companion object {
        private const val TAG = "GalleryWallRefreshWorker"
    }
}
