package com.baysoft.gallerywall

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.baysoft.gallerywall.ui.MainScreen
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme

/**
 * Main application Entry Activity. Launches fully themed Material 3 Jetpack Compose UI.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            GalleryWallTheme {
                MainScreen()
            }
        }
    }
}
