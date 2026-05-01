package com.baysoft.gallerywall

import androidx.work.PeriodicWorkRequest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

class GalleryWallClampTest {

    @Test
    fun clampPeriodicIntervalMinutes_preservesValuesAboveMinimum() {
        assertEquals(30L, GalleryWall.clampPeriodicIntervalMinutes(30L))
        assertEquals(1440L, GalleryWall.clampPeriodicIntervalMinutes(1440L))
    }

    @Test
    fun clampPeriodicIntervalMinutes_raisesBelowMinimum() {
        val minMinutes =
            TimeUnit.MILLISECONDS.toMinutes(PeriodicWorkRequest.MIN_PERIODIC_INTERVAL_MILLIS)
        assertEquals(minMinutes, GalleryWall.clampPeriodicIntervalMinutes(1L))
        assertEquals(minMinutes, GalleryWall.clampPeriodicIntervalMinutes(minMinutes))
    }
}
