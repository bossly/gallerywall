package com.baysoft.gallerywall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single execution or evaluation record of the automated wallpaper scheduler.
 */
@Entity(tableName = "automation_history")
data class AutomationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long, // Epoch millis when evaluation started
    val prompt: String = "",
    val providerId: String = "",
    
    // Constraints & Device status
    val isCharging: Boolean = false,
    val chargingRequired: Boolean = false,
    val isDeviceIdle: Boolean = false,
    val idleRequired: Boolean = false,
    val batteryLevel: Int = -1, // Battery percentage 0-100
    val isPowerSaveMode: Boolean = false,
    val batteryLowRequired: Boolean = false,
    val isModelReady: Boolean = true,
    
    // Execution eligibility & result
    val canStart: Boolean = false,
    val status: String = STATUS_SKIPPED, // SUCCESS, SKIPPED, FAILED, RUNNING
    val statusReason: String = "",
    val wallpaperFilePath: String? = null,
    val durationMs: Long = 0L
) {
    companion object {
        const val STATUS_SUCCESS = "SUCCESS"
        const val STATUS_SKIPPED = "SKIPPED"
        const val STATUS_FAILED = "FAILED"
        const val STATUS_RUNNING = "RUNNING"
    }
}
