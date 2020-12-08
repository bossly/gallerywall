package com.baysoft.gallerywall

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import java.util.concurrent.TimeUnit

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<ListPreference>(Settings.PREF_PERIOD)?.run {
            setOnPreferenceChangeListener { pref, newValue ->

                newValue.toString().toLongOrNull()?.run {
                    when (this) {
                        0L -> cancelSchedule()
                        else -> {
                            schedule(this)
                        }
                    }
                }

                true
            }
        }

    }

    private fun clearPrefs() {
        preferenceManager.sharedPreferences.edit().clear().apply()
    }

    private fun cancelSchedule() {
        val jobId = 1102
        val jobScheduler =
            requireActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(jobId)
    }

    // https://habr.com/ru/post/339012/
    private fun schedule(minutes: Long) {
        val component = ComponentName(requireActivity(), GalleryWallService::class.java)
        val jobId = 1102
        val myJob = JobInfo.Builder(jobId, component)
            .setBackoffCriteria(
                TimeUnit.SECONDS.toMillis(10),
                JobInfo.BACKOFF_POLICY_LINEAR
            )
            .setPeriodic(TimeUnit.MINUTES.toMillis(minutes))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
            .setRequiresDeviceIdle(false)
            .setRequiresCharging(false)
            .build()

        val jobScheduler =
            requireActivity().getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
        jobScheduler.cancel(jobId)
        jobScheduler.schedule(myJob)
    }

}