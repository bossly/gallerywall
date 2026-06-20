package com.baysoft.gallerywall.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

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

val ExpressiveShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(topStart = 32.dp, bottomEnd = 32.dp, topEnd = 8.dp, bottomStart = 8.dp),
    extraLarge = RoundedCornerShape(40.dp)
)

val ExpressiveTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 57.sp,
        lineHeight = 64.sp,
        letterSpacing = (-0.25).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
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
        shapes = ExpressiveShapes,
        typography = ExpressiveTypography,
        content = content
    )
}
