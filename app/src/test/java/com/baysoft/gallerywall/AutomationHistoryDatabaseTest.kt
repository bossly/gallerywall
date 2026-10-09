package com.baysoft.gallerywall

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.baysoft.gallerywall.data.AutomationHistoryDao
import com.baysoft.gallerywall.data.AutomationHistoryEntity
import com.baysoft.gallerywall.data.AutomationHistoryRepository
import com.baysoft.gallerywall.data.WallpaperDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = GalleryApplication::class)
class AutomationHistoryDatabaseTest {

    private lateinit var database: WallpaperDatabase
    private lateinit var dao: AutomationHistoryDao
    private lateinit var repository: AutomationHistoryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, WallpaperDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = database.automationHistoryDao()
        repository = AutomationHistoryRepository(dao)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveHistory_ordersByTimestampDesc() = runBlocking {
        val record1 = AutomationHistoryEntity(
            timestamp = 1000L,
            prompt = "Prompt 1",
            providerId = "local_ai",
            isCharging = true,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 80,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            canStart = true,
            status = AutomationHistoryEntity.STATUS_SUCCESS,
            statusReason = "Success"
        )

        val record2 = AutomationHistoryEntity(
            timestamp = 2000L,
            prompt = "Prompt 2",
            providerId = "procedural",
            isCharging = false,
            chargingRequired = true,
            isDeviceIdle = true,
            idleRequired = true,
            batteryLevel = 40,
            isPowerSaveMode = false,
            batteryLowRequired = true,
            isModelReady = true,
            canStart = false,
            status = AutomationHistoryEntity.STATUS_SKIPPED,
            statusReason = "Skipped"
        )

        val id1 = repository.logRecord(record1)
        val id2 = repository.logRecord(record2)

        assertTrue(id1 > 0)
        assertTrue(id2 > 0)

        val history = repository.getRecentHistory(10)
        assertEquals(2, history.size)
        assertEquals("Prompt 2", history[0].prompt) // Newer first (2000L)
        assertEquals("Prompt 1", history[1].prompt) // Older second (1000L)
    }

    @Test
    fun clearAll_removesAllRecords() = runBlocking {
        val record = AutomationHistoryEntity(
            timestamp = 1000L,
            prompt = "Prompt Test",
            providerId = "local_ai"
        )
        repository.logRecord(record)
        assertEquals(1, repository.getRecentHistory().size)

        repository.clearAll()
        assertEquals(0, repository.getRecentHistory().size)
    }

    @Test
    fun updateRecord_updatesValues() = runBlocking {
        val initial = AutomationHistoryEntity(
            timestamp = 1000L,
            prompt = "Initial",
            status = AutomationHistoryEntity.STATUS_RUNNING
        )
        val id = repository.logRecord(initial)

        val updated = initial.copy(
            id = id,
            status = AutomationHistoryEntity.STATUS_SUCCESS,
            statusReason = "Completed in 3s",
            durationMs = 3000L
        )
        repository.updateRecord(updated)

        val history = repository.getRecentHistory()
        assertEquals(1, history.size)
        assertEquals(AutomationHistoryEntity.STATUS_SUCCESS, history[0].status)
        assertEquals(3000L, history[0].durationMs)
    }
}
