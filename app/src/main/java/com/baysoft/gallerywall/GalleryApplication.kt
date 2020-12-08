package com.baysoft.gallerywall

import android.app.Application
import android.os.Build

class GalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            GalleryWallService.createNotificationChannel(this)
        }
    }

}