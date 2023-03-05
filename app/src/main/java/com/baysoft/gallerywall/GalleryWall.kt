package com.baysoft.gallerywall

import android.app.WallpaperManager
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import androidx.preference.PreferenceManager
import java.util.concurrent.TimeUnit

/**
 * Created on 12.12.2020.
 * Copyright by oleg
 */
class GalleryWall {

    companion object {
        private const val jobId = 1102

        fun cancelSchedule(context: Context) {
            val jobScheduler =
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            jobScheduler.cancel(jobId)
        }

        fun schedule(context: Context, minutes: Long? = null) {
            val period = minutes
                    ?: Settings(PreferenceManager.getDefaultSharedPreferences(context)).period
            val jobScheduler =
                    context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler

            if (period > 0) {
                val component = ComponentName(context, GalleryWallService::class.java)
                val myJob = JobInfo.Builder(jobId, component)
                        .setBackoffCriteria(
                                TimeUnit.SECONDS.toMillis(10),
                                JobInfo.BACKOFF_POLICY_LINEAR
                        )
                        .setPeriodic(TimeUnit.MINUTES.toMillis(period))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .setRequiresDeviceIdle(false)
                        .setRequiresCharging(false)
                        .build()

                jobScheduler.cancel(jobId)
                jobScheduler.schedule(myJob)
            } else {
                jobScheduler.cancel(jobId)
            }
        }

        suspend fun fetchImageURL(context: Context): String {
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

}