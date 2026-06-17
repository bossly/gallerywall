package com.baysoft.gallerywall

import android.os.Bundle
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.baysoft.gallerywall.data.WallpaperDatabase
import com.baysoft.gallerywall.data.WallpaperEntity
import com.baysoft.gallerywall.provider.ColorProvider
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.runner.RunWith
import tools.fastlane.screengrab.Screengrab
import tools.fastlane.screengrab.UiAutomatorScreenshotStrategy
import tools.fastlane.screengrab.cleanstatusbar.CleanStatusBar
import tools.fastlane.screengrab.locale.LocaleTestRule
import java.io.File

@LargeTest
@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = GrantPermissionRule.grant(
        android.Manifest.permission.POST_NOTIFICATIONS,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @get:Rule
    val localeTestRule = LocaleTestRule()

    @Before
    fun before() {
        WallpaperProviderRegistry.unregister(ColorProvider)
        Screengrab.setDefaultScreenshotStrategy(UiAutomatorScreenshotStrategy())
        val extras: Bundle = InstrumentationRegistry.getArguments()
        // https://docs.fastlane.tools/getting-started/android/screenshots/#advanced-screengrab
        Log.d("extras", extras.toString())
        CleanStatusBar.enableWithDefaults()

        setupGallery()
    }

    private fun setupGallery() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = WallpaperDatabase.getInstance(context)
        val dao = db.wallpaperDao()

        runBlocking {
            // Clear existing wallpapers
            db.clearAllTables()

            // Generate and populate with test images
            val colors = listOf(
                android.graphics.Color.parseColor("#2196F3"), // Blue
                android.graphics.Color.parseColor("#4CAF50"), // Green
                android.graphics.Color.parseColor("#F44336"), // Red
                android.graphics.Color.parseColor("#FFEB3B"), // Yellow
                android.graphics.Color.parseColor("#9C27B0"), // Purple
                android.graphics.Color.parseColor("#FF9800")  // Orange
            )
            
            colors.forEachIndexed { index, color ->
                val fileName = "test_image_$index.jpg"
                val file = File(context.filesDir, fileName)
                
                val bitmap = android.graphics.Bitmap.createBitmap(1080, 1920, android.graphics.Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bitmap)
                canvas.drawColor(color)
                
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                
                dao.insert(WallpaperEntity(
                    filePath = file.absolutePath,
                    dateAdded = System.currentTimeMillis() - (index * 60000),
                    providerId = "test_provider",
                    prompt = "Sample Wallpaper ${index + 1}"
                ))
            }
        }
    }

    @After
    fun after() {
        CleanStatusBar.disable()
    }

    @Test
    fun captureScreenshots() {
        val barItems = composeTestRule.onNodeWithTag("BottomNavigationBar", useUnmergedTree = true)
            .onChild()
            .onChildren()
        
        // Capture screen1 (Gallery screen)
        composeTestRule.waitForIdle()
        // wait 1 second to allow screen to load wallpapers
        Thread.sleep(1000)
        Screengrab.screenshot("screen1")

        // Navigate to Providers screen (index 1) and capture screen2
        barItems[1].performClick()
        composeTestRule.waitForIdle()
        Screengrab.screenshot("screen2")

        // Navigate to Automation screen (index 2) and capture screen3
        barItems[2].performClick()
        composeTestRule.waitForIdle()
        Screengrab.screenshot("screen3")
    }

}

