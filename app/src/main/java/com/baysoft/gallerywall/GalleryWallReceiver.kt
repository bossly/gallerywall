package com.baysoft.gallerywall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.os.Build
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GalleryWallReceiver : BroadcastReceiver() {

    companion object {
        private const val EXTRA_URL = "image.url"

        fun updateIntent(context: Context, imageUrl: String?): Intent {
            val intent = Intent(context, GalleryWallReceiver::class.java)
            imageUrl?.let {
                intent.putExtra(EXTRA_URL, it)
            }
            return intent
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val photo = intent?.getStringExtra(EXTRA_URL)

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // device restarted, we need to reschedule events
                context?.let {
                    GalleryWall.schedule(it)
                }
            }
            "com.baysoft.gallerywall.WIDGET_REFRESH" -> {
                context?.let {
                    GalleryAppWidget.updateLoading(it)
                    GlobalScope.launch {
                        val imageUrl = photo ?: GalleryWall.fetchImageURL(it)
                        Glide.with(it).asBitmap().load(imageUrl)
                            .addListener(object : RequestListener<Bitmap> {
                                override fun onLoadFailed(
                                    e: GlideException?, model: Any?,
                                    target: Target<Bitmap>?, isFirstResource: Boolean
                                ): Boolean {
                                    // Update all widget instances to ensure state is reset
                                    GalleryAppWidget.updateLoaded(it)
                                    return false
                                }
                                override fun onResourceReady(
                                    resource: Bitmap?, model: Any?, target: Target<Bitmap>?,
                                    dataSource: DataSource?, isFirstResource: Boolean
                                ): Boolean {
                                    // change wallpaper
                                    GalleryWall.updateWallpaper(it, resource)
                                    GalleryAppWidget.updateLoaded(it)
                                    // Show notification
                                    val channelId = "_gallerywall"
                                    val notificationManager = it.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val channel = NotificationChannel(channelId, it.getString(R.string.app_name), NotificationManager.IMPORTANCE_DEFAULT)
                                        notificationManager.createNotificationChannel(channel)
                                    }
                                    val builder = NotificationCompat.Builder(it, channelId)
                                        .setSmallIcon(R.drawable.icon_notification)
                                        .setContentTitle(it.getString(R.string.notification_title_set))
                                        .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                                        .setOngoing(false)
                                        .setAutoCancel(true)
                                    resource?.let { bmp ->
                                        builder.setLargeIcon(bmp)
                                        builder.setStyle(NotificationCompat.BigPictureStyle().bigPicture(bmp))
                                    }
                                    notificationManager.notify(1, builder.build())
                                    // Save bitmap to file and store file path in recents
                                    GlobalScope.launch {
                                        val filePath = resource?.let { bmp ->
                                            val file = java.io.File(it.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
                                            java.io.FileOutputStream(file).use { out ->
                                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                            }
                                            file.absolutePath
                                        } ?: imageUrl
                                        val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(it)
                                        val repo = com.baysoft.gallerywall.data.WallpaperRepository(db.wallpaperDao())
                                        repo.addWallpaper(filePath)
                                        val updateIntent = Intent("com.baysoft.gallerywall.WALLPAPER_SET")
                                        it.sendBroadcast(updateIntent)
                                    }
                                    return false
                                }
                            }).submit()
                    }
                }
            }
            else -> {
                context?.let {
                    // Always reset widget state after any wallpaper update
                    GlobalScope.launch {
                        val imageUrl = photo ?: GalleryWall.fetchImageURL(it)
                        Glide.with(it).asBitmap().load(imageUrl)
                            .addListener(object : RequestListener<Bitmap> {
                                override fun onLoadFailed(
                                    e: GlideException?, model: Any?,
                                    target: Target<Bitmap>?, isFirstResource: Boolean
                                ): Boolean {
                                    GalleryAppWidget.updateLoaded(it)
                                    return false
                                }
                                override fun onResourceReady(
                                    resource: Bitmap?, model: Any?, target: Target<Bitmap>?,
                                    dataSource: DataSource?, isFirstResource: Boolean
                                ): Boolean {
                                    // change wallpaper
                                    GalleryWall.updateWallpaper(it, resource)
                                    // Always reset widget state
                                    GalleryAppWidget.updateLoaded(it)
                                    // Save bitmap to file and store file path in recents
                                    GlobalScope.launch {
                                        val filePath = resource?.let { bmp ->
                                            val file = java.io.File(it.filesDir, "wallpaper_${System.currentTimeMillis()}.jpg")
                                            java.io.FileOutputStream(file).use { out ->
                                                bmp.compress(Bitmap.CompressFormat.JPEG, 95, out)
                                            }
                                            file.absolutePath
                                        } ?: imageUrl
                                        val db = com.baysoft.gallerywall.data.WallpaperDatabase.getInstance(it)
                                        val repo = com.baysoft.gallerywall.data.WallpaperRepository(db.wallpaperDao())
                                        repo.addWallpaper(filePath)
                                        val updateIntent = Intent("com.baysoft.gallerywall.WALLPAPER_SET")
                                        it.sendBroadcast(updateIntent)
                                    }
                                    return false
                                }
                            }).submit()
                    }
                }
            }
        }
    }
}