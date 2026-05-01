package com.baysoft.gallerywall

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat

/**
 * Notification channel and refresh notifications for periodic wallpaper updates (posted from
 * [GalleryWallRefreshWorker], not from a foreground service).
 */
object GalleryWallNotifications {

    const val CHANNEL_ID = "_gallerywall"
    const val NOTIFICATION_ID = 1

    fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.app_name)
        val description = context.getString(R.string.app_name)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun buildRefreshNotification(context: Context, image: Bitmap?): Notification {
        val updateIntent = GalleryWallReceiver.updateIntent(context)
        val activatePending = PendingIntent.getBroadcast(
            context,
            1,
            updateIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inActivity = Intent(context, MainActivity::class.java)
        val activityIntent = PendingIntent.getActivity(
            context,
            2,
            inActivity,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(context.getString(R.string.notification_title_set))
            .setContentIntent(activityIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        image?.let {
            builder.setLargeIcon(it)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(it)
            )
        }

        builder.addAction(
            R.drawable.ic_refresh_gray_32,
            context.getString(R.string.notification_action_next),
            activatePending
        )
        builder.setOngoing(false).setAutoCancel(true)

        return builder.build()
    }
}
