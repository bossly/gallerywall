package com.baysoft.gallerywall

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.baysoft.gallerywall.ml.DynamicPromptParser

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31])
class DynamicPromptParserTest {

    @Test
    fun parse_replacesTimeOfDayAndOtherPlaceholders() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val template = "cute pastel pattern, [TimeOfDay], [Season], [Weather]"
        val parsed = DynamicPromptParser.parse(context, template)
        
        // Assert all brackets were successfully resolved
        assertFalse(parsed.contains("[TimeOfDay]"))
        assertFalse(parsed.contains("[Season]"))
        assertFalse(parsed.contains("[Weather]"))
        
        // Verify output contains valid replaced terms
        val hasTime = parsed.contains("morning") || parsed.contains("afternoon") || parsed.contains("evening") || parsed.contains("night")
        val hasSeason = parsed.contains("spring") || parsed.contains("summer") || parsed.contains("autumn") || parsed.contains("winter")
        val hasWeather = parsed.contains("sunny") || parsed.contains("rainy") || parsed.contains("cloudy") || parsed.contains("snowy") || parsed.contains("overcast") || parsed.contains("sun") || parsed.contains("sky")

        assertTrue("Should contain a valid time of day", hasTime)
        assertTrue("Should contain a valid season", hasSeason)
        assertTrue("Should contain a valid weather representation", hasWeather)
    }

    @Test
    fun parse_handlesStaticPrompts() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        val template = "retro circles pattern, minimal vector art"
        val parsed = DynamicPromptParser.parse(context, template)
        
        assertTrue("Static prompt should remain identical", parsed == template)
    }
}
