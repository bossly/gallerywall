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
    const val PROGRESS_NOTIFICATION_ID = 2027

    fun createNotificationChannel(context: Context) {
        val name = context.getString(R.string.app_name)
        val description = context.getString(R.string.app_name)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CHANNEL_ID, name, importance)
        channel.description = description
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    fun buildRefreshNotification(
        context: Context,
        image: Bitmap?,
        filePath: String? = null,
        isAlreadyApplied: Boolean = true
    ): Notification {
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

        val titleRes = if (isAlreadyApplied) R.string.notification_title_set else R.string.notification_title_generated
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(context.getString(titleRes))
            .setContentIntent(activityIntent)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        image?.let {
            builder.setLargeIcon(it)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(it)
            )
        }

        if (!isAlreadyApplied && filePath != null) {
            val applyIntent = GalleryWallReceiver.applyIntent(context, filePath)
            val applyPending = PendingIntent.getBroadcast(
                context,
                3,
                applyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(
                R.drawable.ic_play_gray_32, // Reusing an existing icon
                context.getString(R.string.notification_action_apply),
                applyPending
            )
        }

        builder.addAction(
            R.drawable.ic_refresh_gray_32,
            context.getString(R.string.notification_action_retry),
            activatePending
        )
        builder.setOngoing(false).setAutoCancel(true)

        return builder.build()
    }

    fun buildProgressNotification(
        context: Context,
        contentText: String,
        progress: Int = -1,
        max: Int = -1
    ): Notification {
        val pendingIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("GalleryWall Generation")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        if (max > 0 && progress >= 0) {
            builder.setProgress(max, progress, false)
        } else if (progress == -2) { // Indeterminate
            builder.setProgress(0, 0, true)
        }

        return builder.build()
    }
}
