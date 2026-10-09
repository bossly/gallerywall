package com.baysoft.gallerywall

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.data.AutomationHistoryEntity
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperRepository
import com.baysoft.gallerywall.provider.ProviderReadiness
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import java.io.File

/**
 * Evaluates system state against scheduling constraints and logs execution records.
 */
object AutomationLogger {

    data class ConstraintEvaluation(
        val isCharging: Boolean,
        val chargingRequired: Boolean,
        val isDeviceIdle: Boolean,
        val idleRequired: Boolean,
        val batteryLevel: Int,
        val isPowerSaveMode: Boolean,
        val batteryLowRequired: Boolean,
        val isModelReady: Boolean,
        val prompt: String,
        val providerId: String,
        val canStart: Boolean,
        val skipReason: String?
    )

    fun evaluateConstraints(context: Context): ConstraintEvaluation {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val settings = Settings(prefs)

        val chargingRequired = settings.constraintCharging
        val idleRequired = settings.constraintIdle
        val batteryLowRequired = settings.constraintBatteryLow
        val providerId = settings.activeProviderId
        val prompt = if (providerId == "local_ai" || providerId == "procedural") {
            settings.automationPrompt
        } else {
            ""
        }

        // 1. Check Battery and Charging status
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val plugged = batteryIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
        val isPlugged = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && plugged == BatteryManager.BATTERY_PLUGGED_DOCK) ||
                plugged > 0

        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val bmIsCharging = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            batteryManager?.isCharging == true
        } else {
            false
        }

        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL ||
                isPlugged ||
                bmIsCharging

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryLevel = if (level >= 0 && scale > 0) {
            ((level / scale.toFloat()) * 100).toInt()
        } else {
            -1
        }

        // 2. Check PowerManager for Power Save, Device Idle, and Interactive status
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isPowerSaveMode = powerManager?.isPowerSaveMode == true
        val isDeviceIdle = powerManager?.isDeviceIdleMode == true || powerManager?.isInteractive == false

        // 3. Check Provider readiness
        val isModelReady = if (providerId == "local_ai") {
            val modelPath = settings.activeModelPath
            !modelPath.isNullOrEmpty() && File(modelPath).exists()
        } else {
            val provider = WallpaperProviderRegistry.get(providerId)
            provider != null && provider.isReady(context) != ProviderReadiness.NONE
        }

        // 4. Determine eligibility & skip reasons
        val skipReasons = mutableListOf<String>()

        if (batteryLowRequired) {
            if (isPowerSaveMode) {
                skipReasons.add(context.getString(R.string.reason_skipped_power_save))
            } else if (batteryLevel in 0..14) {
                skipReasons.add(context.getString(R.string.reason_skipped_battery_level, batteryLevel))
            }
        }

        if (chargingRequired && !isCharging) {
            skipReasons.add(context.getString(R.string.reason_skipped_charging))
        }

        if (idleRequired && !isDeviceIdle) {
            skipReasons.add(context.getString(R.string.reason_skipped_idle))
        }

        if (!isModelReady) {
            skipReasons.add(context.getString(R.string.reason_skipped_no_model))
        }

        val canStart = skipReasons.isEmpty()
        val skipReason = if (!canStart) skipReasons.joinToString("; ") else null

        return ConstraintEvaluation(
            isCharging = isCharging,
            chargingRequired = chargingRequired,
            isDeviceIdle = isDeviceIdle,
            idleRequired = idleRequired,
            batteryLevel = batteryLevel,
            isPowerSaveMode = isPowerSaveMode,
            batteryLowRequired = batteryLowRequired,
            isModelReady = isModelReady,
            prompt = prompt,
            providerId = providerId,
            canStart = canStart,
            skipReason = skipReason
        )
    }

    suspend fun createInitialRecord(
        context: Context,
        evaluation: ConstraintEvaluation,
        timestamp: Long = System.currentTimeMillis()
    ): AutomationHistoryEntity {
        return AutomationHistoryEntity(
            timestamp = timestamp,
            prompt = evaluation.prompt,
            providerId = evaluation.providerId,
            isCharging = evaluation.isCharging,
            chargingRequired = evaluation.chargingRequired,
            isDeviceIdle = evaluation.isDeviceIdle,
            idleRequired = evaluation.idleRequired,
            batteryLevel = evaluation.batteryLevel,
            isPowerSaveMode = evaluation.isPowerSaveMode,
            batteryLowRequired = evaluation.batteryLowRequired,
            isModelReady = evaluation.isModelReady,
            canStart = evaluation.canStart,
            status = if (evaluation.canStart) AutomationHistoryEntity.STATUS_RUNNING else AutomationHistoryEntity.STATUS_SKIPPED,
            statusReason = evaluation.skipReason ?: context.getString(R.string.status_running),
            wallpaperFilePath = null,
            durationMs = 0L
        )
    }
}
