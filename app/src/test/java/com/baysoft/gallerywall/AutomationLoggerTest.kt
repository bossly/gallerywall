package com.baysoft.gallerywall

import android.content.Context
import android.os.PowerManager
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.baysoft.gallerywall.data.AutomationHistoryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = GalleryApplication::class)
class AutomationLoggerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun evaluateConstraints_defaultSettings_returnsValidEvaluation() {
        val evaluation = AutomationLogger.evaluateConstraints(context)
        assertNotNull(evaluation)
        assertEquals("local_ai", evaluation.providerId)
    }

    @Test
    fun createInitialRecord_createsValidSkippedRecordWhenCannotStart() = runBlocking {
        val evaluation = AutomationLogger.ConstraintEvaluation(
            isCharging = false,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 50,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            prompt = "seamless test prompt",
            providerId = "local_ai",
            canStart = false,
            skipReason = "Skipped: Device is not charging"
        )

        val record = AutomationLogger.createInitialRecord(context, evaluation, 1000L)

        assertEquals(1000L, record.timestamp)
        assertEquals("seamless test prompt", record.prompt)
        assertEquals("local_ai", record.providerId)
        assertFalse(record.isCharging)
        assertTrue(record.chargingRequired)
        assertTrue(record.isDeviceIdle)
        assertEquals(50, record.batteryLevel)
        assertFalse(record.canStart)
        assertEquals(AutomationHistoryEntity.STATUS_SKIPPED, record.status)
        assertEquals("Skipped: Device is not charging", record.statusReason)
    }

    @Test
    fun createInitialRecord_createsValidRunningRecordWhenCanStart() = runBlocking {
        val evaluation = AutomationLogger.ConstraintEvaluation(
            isCharging = true,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 90,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            prompt = "seamless test pattern",
            providerId = "procedural",
            canStart = true,
            skipReason = null
        )

        val record = AutomationLogger.createInitialRecord(context, evaluation, 2000L)

        assertEquals(2000L, record.timestamp)
        assertTrue(record.canStart)
        assertEquals(AutomationHistoryEntity.STATUS_RUNNING, record.status)
        assertEquals(context.getString(R.string.status_running), record.statusReason)
    }

    @Test
    fun evaluateConstraints_whenPluggedUsbAndStatusNotCharging_evaluatesIsChargingTrue() {
        val batteryIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING)
            putExtra(android.os.BatteryManager.EXTRA_PLUGGED, android.os.BatteryManager.BATTERY_PLUGGED_USB)
            putExtra(android.os.BatteryManager.EXTRA_LEVEL, 100)
            putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        }
        context.sendStickyBroadcast(batteryIntent)

        val evaluation = AutomationLogger.evaluateConstraints(context)
        assertTrue(evaluation.isCharging)
        assertEquals(100, evaluation.batteryLevel)
    }

    @Test
    fun evaluateConstraints_whenPluggedAcAndStatusDischarging_evaluatesIsChargingTrue() {
        val batteryIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(android.os.BatteryManager.EXTRA_PLUGGED, android.os.BatteryManager.BATTERY_PLUGGED_AC)
            putExtra(android.os.BatteryManager.EXTRA_LEVEL, 100)
            putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        }
        context.sendStickyBroadcast(batteryIntent)

        val evaluation = AutomationLogger.evaluateConstraints(context)
        assertTrue(evaluation.isCharging)
    }

    @Test
    fun evaluateConstraints_whenUnpluggedAndDischarging_evaluatesIsChargingFalse() {
        val batteryIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
            putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
            putExtra(android.os.BatteryManager.EXTRA_LEVEL, 80)
            putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
        }
        context.sendStickyBroadcast(batteryIntent)

        val evaluation = AutomationLogger.evaluateConstraints(context)
        assertFalse(evaluation.isCharging)
    }
}
