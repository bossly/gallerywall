package com.baysoft.gallerywall

import android.app.WallpaperManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class GalleryWallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        val photo = intent?.getStringExtra("EXTRA_URL")

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                // device restarted
            }
        }

        context?.let {
            GalleryAppWidget.updateLoading(it)

            GlobalScope.launch {
                val imageUrl = loadImage(it)

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
                                GalleryAppWidget.updateLoaded(it)
                                // change wallpaper
                                updateWallpaper(it, resource)
                                return false
                            }
                        }).submit()
            }
        }
    }

    private suspend fun loadImage(context: Context): String {
        val settings = Settings(PreferenceManager.getDefaultSharedPreferences(context))
        val result = ImageProvider.serviceApi.loadPixabay(BuildConfig.PIXABAY_API, settings.query)
        result?.hits?.run {
            val index = indices.random()
            return get(index).imageURL
        }

        return ""
    }


    fun updateWallpaper(context: Context, image: Bitmap?) {
        image?.let {
            WallpaperManager.getInstance(context).setBitmap(image)
        }
    }

}