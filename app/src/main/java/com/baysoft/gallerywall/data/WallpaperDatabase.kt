package com.baysoft.gallerywall.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [WallpaperEntity::class, AutomationHistoryEntity::class], version = 3)
abstract class WallpaperDatabase : RoomDatabase() {
    abstract fun wallpaperDao(): WallpaperDao
    abstract fun automationHistoryDao(): AutomationHistoryDao

    companion object {
        @Volatile private var INSTANCE: WallpaperDatabase? = null

        fun getInstance(context: Context): WallpaperDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WallpaperDatabase::class.java,
                    "wallpaper_db"
                )
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
            }
    }
}
