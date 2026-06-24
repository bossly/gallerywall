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

    /** When false, wallpaper is generated but not applied until user confirms via notification. */
    val autoApplyWallpaper: Boolean
        get() = preferences.getBoolean(PREF_AUTO_APPLY_WALLPAPER, true)

    val constraintWifi: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_WIFI, false)

    val constraintCharging: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_CHARGING, true)

    val constraintIdle: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_IDLE, true)

    val constraintBatteryLow: Boolean
        get() = preferences.getBoolean(PREF_CONSTRAINT_BATTERY_LOW, true)

    /** Comma-separated hex colors for [com.baysoft.gallerywall.provider.GradientProvider]. */
    val generatedColorsHex: String
        get() = preferences.getString(PREF_GENERATED_COLORS, DEFAULT_GENERATED_COLORS)
            ?: DEFAULT_GENERATED_COLORS

    val notification: Boolean
        get() = preferences.getBoolean(PREF_NOTIFICATION, true)

    /** Selected wallpaper generator ([com.baysoft.gallerywall.provider.WallpaperProvider.id]). */
    val activeProviderId: String
        get() {
            val defaultId = if (BuildConfig.DEBUG) "random_color" else "local_ai"
            return preferences.getString(PREF_WALLPAPER_PROVIDER, defaultId) ?: defaultId
        }

    /** Absolute path of the gallery file last applied as system wallpaper (may be null). */
    val lastAppliedWallpaperPath: String?
        get() = preferences.getString(PREF_LAST_APPLIED_WALLPAPER_PATH, null)

    val automationPrompt: String
        get() = preferences.getString(PREF_AUTOMATION_PROMPT, DEFAULT_AUTOMATION_PROMPT) ?: DEFAULT_AUTOMATION_PROMPT

    val postProcessingFilter: String
        get() = preferences.getString(PREF_POST_PROCESSING_FILTER, "none") ?: "none"

    val activeModelPath: String?
        get() = preferences.getString(PREF_ACTIVE_MODEL_PATH, null)

    val periodUnit: String
        get() = preferences.getString(PREF_PERIOD_UNIT, DEFAULT_PERIOD_UNIT) ?: DEFAULT_PERIOD_UNIT

    /** Scale factor applied to the native 64×64 model output (e.g. 2 → 128×128). */
    val scaleFactor: Int
        get() = preferences.getInt(PREF_SCALE_FACTOR, DEFAULT_SCALE_FACTOR)

    companion object {
        const val PREF_PERIOD = "pref_period"
        const val PREF_AUTO_WALLPAPER_ENABLED = "pref_auto_wallpaper_enabled"
        const val PREF_AUTO_APPLY_WALLPAPER = "pref_auto_apply_wallpaper"
        const val PREF_CONSTRAINT_WIFI = "pref_constraint_wifi"
        const val PREF_CONSTRAINT_CHARGING = "pref_constraint_charging"
        const val PREF_CONSTRAINT_IDLE = "pref_constraint_idle"
        const val PREF_CONSTRAINT_BATTERY_LOW = "pref_constraint_battery_low"
        const val PREF_GENERATED_COLORS = "pref_generated_colors"
        const val PREF_NOTIFICATION = "pref_notification"
        const val PREF_WALLPAPER_PROVIDER = "pref_wallpaper_provider"
        const val PREF_WALLPAPER_SOURCE_NAV = "pref_wallpaper_source_nav"
        const val PREF_LAST_APPLIED_WALLPAPER_PATH = "pref_last_applied_wallpaper_path"
        
        const val PREF_AUTOMATION_PROMPT = "pref_automation_prompt"
        const val PREF_POST_PROCESSING_FILTER = "pref_post_processing_filter"
        const val PREF_ACTIVE_MODEL_PATH = "pref_active_model_path"
        const val PREF_PERIOD_UNIT = "pref_period_unit"
        const val PREF_SCALE_FACTOR = "pref_scale_factor"

        const val DEFAULT_GENERATED_COLORS = "#6750A4,#625B71,#7D5260"
        const val DEFAULT_AUTOMATION_PROMPT = "seamless cute pastel floral pattern"
        const val DEFAULT_PERIOD_UNIT = "HOURS"
        const val DEFAULT_SCALE_FACTOR = 2

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
