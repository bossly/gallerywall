package com.baysoft.gallerywall

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], application = GalleryApplication::class)
class WallpaperProviderGenerateTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @Test
    fun localAIProvider_throwsException_whenNoModelLoaded() {
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_WALLPAPER_PROVIDER, "local_ai")
            .commit()
        assertThrows(IllegalStateException::class.java) {
            GalleryWall.createWallpaperBitmap(context)
        }
    }

    @Test
    fun colorProvider_cancellation() {
        val provider = com.baysoft.gallerywall.provider.ColorProvider
        val thread = Thread {
            assertThrows(java.util.concurrent.CancellationException::class.java) {
                provider.generateBitmap(context) {}
            }
        }
        thread.start()
        Thread.sleep(500)
        provider.stop(context)
        thread.join(3000)
    }

    @Test
    fun localAIProvider_stop_startsServiceWithStopAction() {
        val provider = com.baysoft.gallerywall.provider.LocalAIProvider
        provider.stop(context)
        val intent = org.robolectric.Shadows.shadowOf(context as android.app.Application).nextStartedService
        org.junit.Assert.assertNotNull(intent)
        org.junit.Assert.assertEquals(ImageGenerationService::class.java.name, intent.component?.className)
        org.junit.Assert.assertEquals(ImageGenerationService.ACTION_STOP, intent.action)
    }

    @Test
    fun stopIntent_broadcast_cancelsProgressNotification() {
        val receiver = GalleryWallReceiver()
        val intent = GalleryWallReceiver.stopIntent(context)
        
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putString(Settings.PREF_WALLPAPER_PROVIDER, "random_color")
            .commit()
            
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNm = org.robolectric.Shadows.shadowOf(nm)
        
        // Post a dummy progress notification to verify cancellation
        val dummyNotification = android.app.Notification.Builder(context, "dummy_channel")
            .setContentTitle("Dummy")
            .setSmallIcon(android.R.drawable.ic_menu_close_clear_cancel)
            .build()
        nm.notify(GalleryWallNotifications.PROGRESS_NOTIFICATION_ID, dummyNotification)
        
        org.junit.Assert.assertNotNull(shadowNm.getNotification(GalleryWallNotifications.PROGRESS_NOTIFICATION_ID))
        
        receiver.onReceive(context, intent)
        
        org.junit.Assert.assertNull(shadowNm.getNotification(GalleryWallNotifications.PROGRESS_NOTIFICATION_ID))
    }
}
