package com.baysoft.gallerywall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Curated colors for a sleek, ultra-premium theme
val Purple80 = androidx.compose.ui.graphics.Color(0xFFD0BCFF)
val PurpleGrey80 = androidx.compose.ui.graphics.Color(0xFFCCC2DC)
val Pink80 = androidx.compose.ui.graphics.Color(0xFFEFB8C8)

val Purple40 = androidx.compose.ui.graphics.Color(0xFF6750A4)
val PurpleGrey40 = androidx.compose.ui.graphics.Color(0xFF625B71)
val Pink40 = androidx.compose.ui.graphics.Color(0xFF7D5260)

val DarkBackground = androidx.compose.ui.graphics.Color(0xFF0F0E13)
val DarkSurface = androidx.compose.ui.graphics.Color(0xFF1D1B22)
val LightBackground = androidx.compose.ui.graphics.Color(0xFFFAF8FF)
val LightSurface = androidx.compose.ui.graphics.Color(0xFFF3EDF7)

val DarkText = androidx.compose.ui.graphics.Color(0xFF1D1B20)
val LightText = androidx.compose.ui.graphics.Color(0xFFE6E1E9)
val Purple20 = androidx.compose.ui.graphics.Color(0xFF381E72)
val PurpleGrey20 = androidx.compose.ui.graphics.Color(0xFF332D41)

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = DarkBackground,
    surface = DarkSurface,
    onPrimary = Purple20,
    onSecondary = PurpleGrey20,
    onBackground = LightText,
    onSurface = LightText
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = LightBackground,
    surface = LightSurface,
    onPrimary = LightText,
    onSecondary = LightText,
    onBackground = DarkText,
    onSurface = DarkText
)

@Composable
fun GalleryWallTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}
