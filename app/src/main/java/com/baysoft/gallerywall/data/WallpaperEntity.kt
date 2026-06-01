package com.baysoft.gallerywall.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wallpapers")
data class WallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val filePath: String,
    val dateAdded: Long, // Store as epoch millis
    val providerId: String = "",
    val prompt: String = ""
)
