package com.baysoft.gallerywall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WallpaperDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(wallpaper: WallpaperEntity)

    @Query("SELECT * FROM wallpapers WHERE dateAdded >= :from ORDER BY dateAdded DESC")
    suspend fun getRecentWallpapers(from: Long): List<WallpaperEntity>

    @Query("DELETE FROM wallpapers WHERE dateAdded < :olderThan")
    suspend fun deleteOlderThan(olderThan: Long)
}
