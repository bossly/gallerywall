package com.baysoft.gallerywall

import android.app.Application

class GalleryApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        GalleryWallService.createNotificationChannel(this)
    }

}