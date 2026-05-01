package com.baysoft.gallerywall

import android.app.Application
import androidx.preference.PreferenceManager

class GalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Settings.migrateLegacyPrefsIfNeeded(PreferenceManager.getDefaultSharedPreferences(this))
        GalleryWallNotifications.createNotificationChannel(this)
    }

}