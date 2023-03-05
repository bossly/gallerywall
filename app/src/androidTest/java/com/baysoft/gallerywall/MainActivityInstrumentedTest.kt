package com.baysoft.gallerywall


import android.os.Bundle
import android.util.Log
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
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
    }

    @Test
    fun homeScreen() {
        // app has actionbar
        val actionBar = onView(withId(com.google.android.material.R.id.action_bar))
        actionBar.check(matches(isDisplayed()))

        Screengrab.screenshot("screen1")
    }

    @Test
    fun searchDialog() {
        val recyclerView = onView(withId(androidx.preference.R.id.recycler_view))
        recyclerView.check(matches(hasChildCount(5)))

        recyclerView.perform(
            RecyclerViewActions.actionOnItemAtPosition<RecyclerView.ViewHolder>(
                1, click()
            )
        )

        val textView = onView(withId(com.google.android.material.R.id.alertTitle))
        textView.check(matches(withText(R.string.pref_query_title)))

        Screengrab.screenshot("screen2")
    }

}
