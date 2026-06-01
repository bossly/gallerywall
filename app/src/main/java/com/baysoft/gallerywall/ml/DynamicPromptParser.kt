package com.baysoft.gallerywall.ml

import android.content.Context
import java.util.Calendar

/**
 * Utility to parse prompt strings and replace dynamic dynamic bracket placeholders like
 * [TimeOfDay], [Season], and [Weather] with active real-world variables, offline.
 */
object DynamicPromptParser {

    /**
     * Replaces standard dynamic variables inside a prompt string.
     */
    fun parse(context: Context, template: String): String {
        var parsed = template
        val calendar = Calendar.getInstance()

        // 1. Resolve Time of Day
        if (parsed.contains("[TimeOfDay]")) {
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val timeOfDay = when (hour) {
                in 6..11 -> "morning"
                in 12..17 -> "afternoon"
                in 18..21 -> "evening"
                else -> "night"
            }
            parsed = parsed.replace("[TimeOfDay]", timeOfDay)
        }

        // 2. Resolve Season
        if (parsed.contains("[Season]")) {
            val month = calendar.get(Calendar.MONTH) // 0-indexed
            val season = when (month) {
                Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY -> "winter"
                Calendar.MARCH, Calendar.APRIL, Calendar.MAY -> "spring"
                Calendar.JUNE, Calendar.JULY, Calendar.AUGUST -> "summer"
                else -> "autumn"
            }
            parsed = parsed.replace("[Season]", season)
        }

        // 3. Resolve Weather (Uses month/time-of-day offsets to yield realistic offline variations)
        if (parsed.contains("[Weather]")) {
            val month = calendar.get(Calendar.MONTH)
            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            
            // Offline mock weather calculation based on date/time offsets to ensure stable variations
            val weather = when {
                month in listOf(Calendar.NOVEMBER, Calendar.DECEMBER, Calendar.JANUARY, Calendar.FEBRUARY) -> {
                    if ((hour + month) % 2 == 0) "snowy" else "chilly overcast"
                }
                month in listOf(Calendar.MARCH, Calendar.APRIL, Calendar.MAY, Calendar.OCTOBER) -> {
                    if ((hour + month) % 2 == 0) "rainy" else "cloudy"
                }
                else -> {
                    if ((hour + month) % 3 == 0) "sunny" else if ((hour + month) % 3 == 1) "golden hour sun" else "clear blue sky"
                }
            }
            parsed = parsed.replace("[Weather]", weather)
        }

        return parsed
    }
}
