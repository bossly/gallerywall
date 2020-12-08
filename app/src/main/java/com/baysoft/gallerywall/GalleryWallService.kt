package com.baysoft.gallerywall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobParameters
import android.app.job.JobService
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.text.format.DateFormat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.util.*


// https://developer.android.com/training/notify-user/expanded#kotlin
class GalleryWallService : JobService() {

    companion object {
        const val CHANNEL_ID = "_gallerywall"
        const val NOTIFICATION_ID = 1

        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        @RequiresApi(Build.VERSION_CODES.O)
        fun createNotificationChannel(context: Context) {
            val name = context.getString(R.string.app_name)
            val description = context.getString(R.string.app_name)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(CHANNEL_ID, name, importance)
            channel.description = description
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            context.getSystemService(NotificationManager::class.java)?.run {
                createNotificationChannel(channel)
            }
        }
    }

    private suspend fun loadImage(): String {
        val settings = Settings(PreferenceManager.getDefaultSharedPreferences(this))
        val result = ImageProvider.serviceApi.loadPixabay(BuildConfig.PIXABAY_API, settings.query)
        result?.hits?.run {
            val index = indices.random()
            return get(index).imageURL
        }

        return ""
    }

    private fun showNotification() {
        val context: Context = this

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // check if wifi
        if (Settings(PreferenceManager.getDefaultSharedPreferences(context)).wifiOnly) {
            val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = connManager.getNetworkCapabilities(connManager.activeNetwork)

            if (!active.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                return
            }
        }

        GalleryAppWidget.updateLoading(this)

        GlobalScope.launch {
            val photo = loadImage()
            Glide.with(context).asBitmap().load(photo)
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
                            manager.notify(NOTIFICATION_ID, buildNotification(photo, resource))

                            // change wallpaper
                            val activateIntent = Intent(context, GalleryWallReceiver::class.java)
                            activateIntent.putExtra("EXTRA_URL", photo)
                            sendBroadcast(activateIntent)

                            return false
                        }
                    }).submit()
        }
    }

    private fun buildNotification(url: String, image: Bitmap?): Notification? {
        val resultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val activateIntent = Intent(this, GalleryWallReceiver::class.java)
        activateIntent.putExtra("EXTRA_URL", url)

        val activatePending = PendingIntent.getBroadcast(
                this, 1, activateIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        val resultPendingIntent = PendingIntent.getActivity(
                this, 1, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )
        val inActivity = Intent(this, MainActivity::class.java)
        inActivity.putExtra("key", "value1")
        val activityIntent = PendingIntent.getActivity(
                this, 2, inActivity, PendingIntent.FLAG_ONE_SHOT
        )

        val datestamp = DateFormat.getLongDateFormat(this).format(Date())
        val timestamp = DateFormat.getTimeFormat(this).format(Date())

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.notification_title_set))
            .setContentText("$datestamp at $timestamp")
            .setContentIntent(activityIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        image?.let {
            builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
            )
        }

        builder.addAction(R.drawable.ic_launch_gray_24, "Next", resultPendingIntent)
        builder.addAction(R.drawable.ic_refresh_gray_32, "Activate", activatePending)

        builder.setOngoing(false).setAutoCancel(true)

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // return super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        showNotification()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

}
