package com.baysoft.gallerywall

import android.os.Bundle
import android.util.Log
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.locale.LocaleTestRule

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @get:Rule
    val localeTestRule = LocaleTestRule()

    @Before
    fun before() {
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val extras: Bundle = InstrumentationRegistry.getArguments()
        // https://docs.fastlane.tools/getting-started/android/screenshots/#advanced-screengrab
        Log.d("extras", extras.toString())
        CleanStatusBar.enableWithDefaults();
    }

    @After
    fun afterAll() {
        CleanStatusBar.disable()
    }

    @Test
    fun homeScreen() {
        // app has actionbar
        val actionBar = onView(withId(com.google.android.material.R.id.action_bar))
        actionBar.check(matches(isDisplayed()))

        Screengrab.screenshot("screen1")
    }

    @Test
    fun settingsScreen_showsPeriodPreference() {
        onView(withId(R.id.settingsFragment)).perform(click())

        onView(withText(R.string.pref_interval_title)).check(matches(isDisplayed()))

        Screengrab.screenshot("screen2")
    }

}
