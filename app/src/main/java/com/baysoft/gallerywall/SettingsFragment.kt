package com.baysoft.gallerywall

import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)

        findPreference<ListPreference>(Settings.PREF_PERIOD)?.run {
            setOnPreferenceChangeListener { _, newValue ->

                newValue.toString().toLongOrNull()?.run {
                    when (this) {
                        0L -> GalleryWall.cancelSchedule(requireActivity())
                        else -> {
                            GalleryWall.schedule(requireActivity(), this)
                        }
                    }
                }

                true
            }
        }

    }

    private fun clearPrefs() {
        preferenceManager.sharedPreferences?.edit()?.clear()?.apply()
    }
}