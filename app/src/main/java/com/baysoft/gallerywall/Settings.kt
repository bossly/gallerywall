package com.baysoft.gallerywall

import android.content.SharedPreferences
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry

class Settings(private val preferences: SharedPreferences) {

    val period: Long
        get() = preferences.getString(PREF_PERIOD, DEFAULT_PERIOD_MINUTES_STRING)?.toLong()
            ?: DEFAULT_PERIOD_MINUTES

    /** When false, periodic refresh is not scheduled (master switch). */
    val autoWallpaperEnabled: Boolean
        get() = preferences.getBoolean(PREF_AUTO_WALLPAPER_ENABLED, false)

    val constraintWifi: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_WIFI, true)

    val constraintCharging: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_CHARGING, false)

    val constraintIdle: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_IDLE, false)

    /** Comma-separated hex colors for [com.baysoft.gallerywall.provider.GradientProvider]. */
    val generatedColorsHex: String
        get() = preferences.getString(PREF_GENERATED_COLORS, DEFAULT_GENERATED_COLORS)
            ?: DEFAULT_GENERATED_COLORS

    /**
     * Solid fill color for [com.baysoft.gallerywall.provider.ColorProvider].
     * If unset, derived from the first color of [generatedColorsHex] until saved explicitly.
     */
    val colorProviderSolidHex: String
        get() = preferences.getString(PREF_COLOR_PROVIDER_SOLID, null)
            ?: WallpaperGenerator.colorToHexString(
                WallpaperGenerator.parseColors(generatedColorsHex).first(),
            )

    val notification: Boolean
        get() = preferences.getBoolean(PREF_NOTIFICATION, true)

    /** Selected wallpaper generator ([com.baysoft.gallerywall.provider.WallpaperProvider.id]). */
    val activeProviderId: String
        get() = preferences.getString(PREF_WALLPAPER_PROVIDER, null)
            ?: WallpaperProviderRegistry.defaultProvider.id

    /** Absolute path of the gallery file last applied as system wallpaper (may be null). */
    val lastAppliedWallpaperPath: String?
        get() = preferences.getString(PREF_LAST_APPLIED_WALLPAPER_PATH, null)

    companion object {
        const val PREF_PERIOD = "pref_period"
        const val PREF_AUTO_WALLPAPER_ENABLED = "pref_auto_wallpaper_enabled"
        const val PREF_CONSTRAINT_WIFI = "pref_constraint_wifi"
        const val PREF_CONSTRAINT_CHARGING = "pref_constraint_charging"
        const val PREF_CONSTRAINT_IDLE = "pref_constraint_idle"
        const val PREF_GENERATED_COLORS = "pref_generated_colors"
        const val PREF_COLOR_PROVIDER_SOLID = "pref_color_provider_solid"
        const val PREF_NOTIFICATION = "pref_notification"
        const val PREF_WALLPAPER_PROVIDER = "pref_wallpaper_provider"
        const val PREF_WALLPAPER_SOURCE_NAV = "pref_wallpaper_source_nav"
        const val PREF_LAST_APPLIED_WALLPAPER_PATH = "pref_last_applied_wallpaper_path"

        const val DEFAULT_GENERATED_COLORS = "#6750A4,#625B71,#7D5260"

        const val DEFAULT_PERIOD_MINUTES = 360L
        const val DEFAULT_PERIOD_MINUTES_STRING = "360"

        /**
         * One-time migration from list periods that included "Off" (0): splits master enablement from interval,
         * removes 0, defaults interval when previously off.
         */
        fun migrateLegacyPrefsIfNeeded(prefs: SharedPreferences) {
            if (prefs.contains(PREF_AUTO_WALLPAPER_ENABLED)) return
            val periodStr = prefs.getString(PREF_PERIOD, null)
            val legacyPeriod = periodStr?.toLongOrNull()
            val edit = prefs.edit()
            when {
                legacyPeriod == null || legacyPeriod == 0L -> {
                    edit.putBoolean(PREF_AUTO_WALLPAPER_ENABLED, false)
                    edit.putString(PREF_PERIOD, DEFAULT_PERIOD_MINUTES_STRING)
                }
                else -> {
                    edit.putBoolean(PREF_AUTO_WALLPAPER_ENABLED, true)
                }
            }
            edit.apply()
        }
    }
}
