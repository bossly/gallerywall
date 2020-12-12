package com.baysoft.gallerywall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
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
        }

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
                                return false
                            }
                        }).submit()
            }
        }
    }
}