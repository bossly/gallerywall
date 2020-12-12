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
import android.util.Log
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

    private fun showNotification(context: Context = this) {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val settings = Settings(PreferenceManager.getDefaultSharedPreferences(context))

        // check if wifi
        if (Settings(PreferenceManager.getDefaultSharedPreferences(context)).wifiOnly) {
            val connManager = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            val active = connManager.getNetworkCapabilities(connManager.activeNetwork)

            active?.let {
                if (!it.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                    return
                }
            }
        }

        GalleryAppWidget.updateLoading(context)

        GlobalScope.launch {
            val photo = GalleryWall.fetchImageURL(context)
            Log.d("GalleryWallService", photo)
            Glide.with(context).asBitmap().load(photo)
                    .addListener(object : RequestListener<Bitmap> {
                        override fun onLoadFailed(
                                e: GlideException?, model: Any?,
                                target: Target<Bitmap>?, isFirstResource: Boolean
                        ): Boolean {
                            GalleryAppWidget.updateLoaded(context)
                            return false
                        }

                        override fun onResourceReady(
                                resource: Bitmap?, model: Any?, target: Target<Bitmap>?,
                                dataSource: DataSource?, isFirstResource: Boolean
                        ): Boolean {
                            if (settings.notification) { // if I need to show notification
                                manager.notify(NOTIFICATION_ID, buildNotification(photo, resource))
                            }

                            // change wallpaper
                            context.sendBroadcast(GalleryWallReceiver.updateIntent(context, photo))

                            return false
                        }
                    }).submit()
        }
    }

    private fun buildNotification(url: String, image: Bitmap?): Notification? {

        // load next one
        val updateIntent = GalleryWallReceiver.updateIntent(this, null)
        val activatePending = PendingIntent.getBroadcast(
                this, 1, updateIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        // view the source
        val resultIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        val resultPendingIntent = PendingIntent.getActivity(
                this, 1, resultIntent, PendingIntent.FLAG_UPDATE_CURRENT
        )

        // open app
        val inActivity = Intent(this, MainActivity::class.java)
        val activityIntent = PendingIntent.getActivity(
                this, 1, inActivity, PendingIntent.FLAG_ONE_SHOT
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.icon_notification)
                .setContentTitle(getString(R.string.notification_title_set))
                .setContentIntent(activityIntent)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        image?.let {
            builder.setLargeIcon(image)
            builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                            .bigPicture(image)
            )
        }

        builder.addAction(R.drawable.ic_refresh_gray_32, getString(R.string.notification_action_next), activatePending)
        builder.addAction(R.drawable.ic_launch_gray_24, getString(R.string.notification_action_open), resultPendingIntent)

        builder.setOngoing(false).setAutoCancel(true)

        return builder.build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // return super.onStartCommand(intent, flags, startId)
        return START_NOT_STICKY
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        if (BuildConfig.DEBUG) {
            // immediately show notification when Job started
            showNotification()
        }

        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true
    }

}
