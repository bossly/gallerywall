package com.baysoft.gallerywall

import android.content.SharedPreferences

class Settings(private val preferences: SharedPreferences) {

    val period: Long
        get() = preferences.getString(PREF_PERIOD, "0")?.toLong() ?: 0

    /** Comma-separated hex colors, e.g. `#RRGGBB` or `#AARRGGBB`. One color = solid; two or more = gradient. */
    val generatedColorsHex: String
        get() = preferences.getString(PREF_GENERATED_COLORS, DEFAULT_GENERATED_COLORS)
            ?: DEFAULT_GENERATED_COLORS

    val notification: Boolean
        get() = preferences.getBoolean(PREF_NOTIFICATION, true)

    companion object {
        const val PREF_PERIOD = "pref_period"
        const val PREF_GENERATED_COLORS = "pref_generated_colors"
        const val PREF_NOTIFICATION = "pref_notification"

        const val DEFAULT_GENERATED_COLORS = "#6750A4,#625B71,#7D5260"
    }
}
