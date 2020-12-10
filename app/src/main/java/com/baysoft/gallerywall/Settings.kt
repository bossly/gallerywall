package com.baysoft.gallerywall

import android.content.SharedPreferences

class Settings(private val preferences: SharedPreferences) {

    val period: Long
        get() = preferences.getString(PREF_PERIOD, "0")?.toLong() ?: 0

    val query: String
        get() = preferences.getString(PREF_QUERY, "cat")!!

    val wifiOnly: Boolean
        get() = preferences.getBoolean(PREF_WIFI_ONLY, false)

    val notification: Boolean
        get() = preferences.getBoolean(PREF_NOTIFICATION, true)

    companion object {
        const val PREF_PERIOD = "pref_period"
        const val PREF_WIFI_ONLY = "pref_wifi"
        const val PREF_QUERY = "pref_query"
        const val PREF_NOTIFICATION = "pref_notification"
    }
}
