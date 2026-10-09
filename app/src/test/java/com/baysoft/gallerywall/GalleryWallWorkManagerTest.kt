package com.baysoft.gallerywall

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = GalleryApplication::class)
class GalleryWallWorkManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, true)
            .commit()
    }

    @After
    fun tearDown() {
        WorkManager.getInstance(context).cancelAllWork()
    }

    @Test
    fun schedule_enqueuesUniquePeriodicWork() {
        GalleryWall.schedule(context, 15L)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(GalleryWall.UNIQUE_WORK_NAME)
            .get(10, TimeUnit.SECONDS)

        assertEquals(1, infos.size)
        val state = infos.single().state
        assertTrue(
            state == WorkInfo.State.ENQUEUED ||
                state == WorkInfo.State.RUNNING ||
                state == WorkInfo.State.BLOCKED
        )
    }

    @Test
    fun cancelSchedule_removesOrFinishesUniqueWork() {
        GalleryWall.schedule(context, 30L)
        GalleryWall.cancelSchedule(context)

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(GalleryWall.UNIQUE_WORK_NAME)
            .get(10, TimeUnit.SECONDS)

        assertTrue(infos.isEmpty() || infos.all { it.state.isFinished })
    }

    @Test
    fun workerExecution_recordsAutomationHistory() {
        kotlinx.coroutines.runBlocking {
            val worker = androidx.work.testing.TestListenableWorkerBuilder.from(context, GalleryWallRefreshWorker::class.java)
                .build()
            val result = worker.doWork()

            val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
            val history = db.automationHistoryDao().getRecentHistory(10)

            assertTrue(history.isNotEmpty())
            val record = history.first()
            assertNotNull(record.status)
            assertNotNull(record.statusReason)
        }
    }

    @Test
    fun workerExecution_whenPluggedUsbAt100PercentAndProviderReady_generatesWallpaperSuccessfully() {
        kotlinx.coroutines.runBlocking {
            val testProvider = TestReadyProvider()
            com.baysoft.gallerywall.provider.WallpaperProviderRegistry.register(testProvider)

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, true)
                .putString(Settings.PREF_WALLPAPER_PROVIDER, testProvider.id)
                .putBoolean(Settings.PREF_CONSTRAINT_CHARGING, true)
                .putBoolean(Settings.PREF_CONSTRAINT_IDLE, false)
                .commit()

            val batteryIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING)
                putExtra(android.os.BatteryManager.EXTRA_PLUGGED, android.os.BatteryManager.BATTERY_PLUGGED_USB)
                putExtra(android.os.BatteryManager.EXTRA_LEVEL, 100)
                putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            }
            context.sendStickyBroadcast(batteryIntent)

            val worker = androidx.work.testing.TestListenableWorkerBuilder.from(context, GalleryWallRefreshWorker::class.java)
                .build()
            val result = worker.doWork()

            assertEquals(androidx.work.ListenableWorker.Result.success(), result)

            val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
            val history = db.automationHistoryDao().getRecentHistory(10)

            assertTrue(history.isNotEmpty())
            val record = history.first()
            assertEquals(com.baysoft.gallerywall.data.AutomationHistoryEntity.STATUS_SUCCESS, record.status)
            assertTrue(record.canStart)
            assertTrue(record.isCharging)
            assertEquals(100, record.batteryLevel)
            assertNotNull(record.wallpaperFilePath)
            assertTrue(java.io.File(record.wallpaperFilePath!!).exists())

            com.baysoft.gallerywall.provider.WallpaperProviderRegistry.unregister(testProvider)
        }
    }

    @Test
    fun workerExecution_whenUnpluggedAndChargingRequired_skipsGeneration() {
        kotlinx.coroutines.runBlocking {
            val testProvider = TestReadyProvider()
            com.baysoft.gallerywall.provider.WallpaperProviderRegistry.register(testProvider)

            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            prefs.edit()
                .putBoolean(Settings.PREF_AUTO_WALLPAPER_ENABLED, true)
                .putString(Settings.PREF_WALLPAPER_PROVIDER, testProvider.id)
                .putBoolean(Settings.PREF_CONSTRAINT_CHARGING, true)
                .putBoolean(Settings.PREF_CONSTRAINT_IDLE, false)
                .commit()

            val batteryIntent = android.content.Intent(android.content.Intent.ACTION_BATTERY_CHANGED).apply {
                putExtra(android.os.BatteryManager.EXTRA_STATUS, android.os.BatteryManager.BATTERY_STATUS_DISCHARGING)
                putExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0)
                putExtra(android.os.BatteryManager.EXTRA_LEVEL, 80)
                putExtra(android.os.BatteryManager.EXTRA_SCALE, 100)
            }
            context.sendStickyBroadcast(batteryIntent)

            val worker = androidx.work.testing.TestListenableWorkerBuilder.from(context, GalleryWallRefreshWorker::class.java)
                .build()
            val result = worker.doWork()

            assertEquals(androidx.work.ListenableWorker.Result.success(), result)

            val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(context)
            val history = db.automationHistoryDao().getRecentHistory(10)

            assertTrue(history.isNotEmpty())
            val record = history.first()
            assertEquals(com.baysoft.gallerywall.data.AutomationHistoryEntity.STATUS_SKIPPED, record.status)
            assertFalse(record.canStart)
            assertFalse(record.isCharging)
            assertEquals(context.getString(R.string.reason_skipped_charging), record.statusReason)

            com.baysoft.gallerywall.provider.WallpaperProviderRegistry.unregister(testProvider)
        }
    }
}

private class TestReadyProvider(override val id: String = "test_ready_provider") : com.baysoft.gallerywall.provider.WallpaperProvider {
    override val titleRes = R.string.app_name
    override val summaryRes = R.string.app_name
    override fun generateBitmap(context: Context, onStateUpdate: (com.baysoft.gallerywall.provider.ProviderState) -> Unit): android.graphics.Bitmap {
        return android.graphics.Bitmap.createBitmap(10, 10, android.graphics.Bitmap.Config.ARGB_8888)
    }
}
