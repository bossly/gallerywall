package com.baysoft.gallerywall.ui

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import android.content.SharedPreferences
import androidx.compose.ui.res.painterResource
import androidx.preference.PreferenceManager
import com.baysoft.gallerywall.Settings
import com.baysoft.gallerywall.provider.ProviderReadiness
import com.baysoft.gallerywall.provider.WallpaperProviderRegistry
import com.baysoft.gallerywall.ui.theme.GalleryWallTheme
import java.io.File

enum class GalleryTab(val title: String, val icon: ImageVector) {
    GALLERY("Gallery", Icons.Default.Home),
    PROVIDERS("Providers", Icons.Default.List),
    SETTINGS("Settings", Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(GalleryTab.GALLERY) }
    var showPromptOverlay by remember { mutableStateOf(false) }
    var triggerDirectGeneration by remember { mutableStateOf(false) }

    val prefs = remember { PreferenceManager.getDefaultSharedPreferences(context) }
    val settings = remember(prefs) { Settings(prefs) }

    var activeProviderId by remember { mutableStateOf(settings.activeProviderId) }
    var activeModelPath by remember { mutableStateOf(settings.activeModelPath) }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == Settings.PREF_WALLPAPER_PROVIDER) {
                activeProviderId = settings.activeProviderId
            } else if (key == Settings.PREF_ACTIVE_MODEL_PATH) {
                activeModelPath = settings.activeModelPath
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val activeProvider = remember(activeProviderId) {
        WallpaperProviderRegistry.get(activeProviderId)
    }
    val readiness = remember(activeProvider, activeModelPath) {
        activeProvider?.isReady(context) ?: ProviderReadiness.NONE
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar(modifier = Modifier.testTag("BottomNavigationBar")) {
                GalleryTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(tab.icon, contentDescription = tab.title) },
                        label = { Text(tab.title) }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedTab == GalleryTab.GALLERY && readiness != ProviderReadiness.NONE) {
                FloatingActionButton(
                    onClick = {
                        if (readiness == ProviderReadiness.PROMPT) {
                            showPromptOverlay = true
                        } else {
                            triggerDirectGeneration = true
                        }
                    },
                    modifier = Modifier.testTag("GenerateFAB")
                ) {
                    if (readiness == ProviderReadiness.PROMPT) {
                        Icon(Icons.Default.Create, contentDescription = "Generate wallpaper")
                    } else {
                        Icon(
                            painter = painterResource(id = com.baysoft.gallerywall.R.drawable.ic_dice),
                            contentDescription = "Generate wallpaper directly"
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "ScreenTransition"
            ) { tab ->
                when (tab) {
                    GalleryTab.GALLERY -> GalleryScreen(
                        onNavigateToProviders = { selectedTab = GalleryTab.PROVIDERS },
                        showPromptOverlay = showPromptOverlay,
                        onDismissPromptOverlay = { showPromptOverlay = false },
                        triggerDirectGeneration = triggerDirectGeneration,
                        onDirectGenerationStarted = { triggerDirectGeneration = false }
                    )
                    GalleryTab.PROVIDERS -> ProvidersScreen()
                    GalleryTab.SETTINGS -> SettingsScreen()
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    GalleryWallTheme {
        MainScreen()
    }
}
