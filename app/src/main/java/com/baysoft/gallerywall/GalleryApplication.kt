package com.baysoft.gallerywall

import android.app.Application
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry

class GalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Settings.migrateLegacyPrefsIfNeeded(PreferenceManager.getDefaultSharedPreferences(this))
        GalleryWallNotifications.createNotificationChannel(this)

        if (BuildConfig.DEBUG) {
            WallpaperProviderRegistry.register(ColorProvider)
        }
    }

}