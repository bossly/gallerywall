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
        private const val EXTRA_PATH = "image.path"

        fun updateIntent(context: Context, imagePath: String?): Intent {
            val intent = Intent(context, GalleryWallReceiver::class.java)
            imagePath?.let {
                intent.putExtra(EXTRA_PATH, it)
            }
            return intent
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        val photo = intent?.getStringExtra(EXTRA_PATH)

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // device restarted, we need to reschedule events
                context?.let {
                    GalleryWall.schedule(it)
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
                                    return false
                                }
                                override fun onResourceReady(
                                    resource: Bitmap?, model: Any?, target: Target<Bitmap>?,
                                    dataSource: DataSource?, isFirstResource: Boolean
                                ): Boolean {
                                    // change wallpaper
                                    GalleryWall.updateWallpaper(it, resource)
                                    // Save bitmap to file and store file path in recents
                                    GalleryWall.recordWallpaper(it, resource)
                                    return true
                                }
                            }).submit()
                    }
                }
            }
        }
    }
}