package com.baysoft.gallerywall.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext


class WallpaperRepository(private val dao: WallpaperDao) {
    suspend fun addWallpaper(filePath: String) {
        val now = System.currentTimeMillis()
        dao.insert(WallpaperEntity(filePath = filePath, dateAdded = now))
    }

    suspend fun getRecentWallpapers(): List<WallpaperEntity> {
        val oneMonthAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        return dao.getRecentWallpapers(oneMonthAgo)
    }

    suspend fun cleanupOldWallpapers() {
        val threeMonthsAgo = System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000
        dao.deleteOlderThan(threeMonthsAgo)
    }
}
