package com.baysoft.gallerywall

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import org.junit.After
import org.junit.Assert.assertEquals
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
}
