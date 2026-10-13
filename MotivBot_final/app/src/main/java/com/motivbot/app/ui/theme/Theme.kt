package com.motivbot.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColors = darkColorScheme(
    primary = BrandViolet,
    onPrimary = LightSurface,
    secondary = BrandBlue,
    background = DarkBackgroundStart,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    error = ErrorRed,
)

private val LightColors = lightColorScheme(
    primary = BrandViolet,
    onPrimary = LightSurface,
    secondary = BrandBlue,
    background = LightBackgroundStart,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    error = ErrorRed,
)

/**
 * SwiftUI's `.font(.system(size:weight:design: .rounded))` has no exact Compose counterpart; the
 * closest platform-wide match is the default sans family, which on Android is already rounded-ish
 * (Roboto/Google Sans). If pixel-parity matters, drop a rounded TTF into `res/font` and set it as
 * [defaultFontFamily] — every text style below inherits from it.
 */
private val defaultFontFamily = FontFamily.Default

private val MotivBotTypography = Typography(
    displaySmall = TextStyle(fontFamily = defaultFontFamily, fontSize = 28.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontFamily = defaultFontFamily, fontSize = 22.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = defaultFontFamily, fontSize = 20.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontFamily = defaultFontFamily, fontSize = 18.sp, fontWeight = FontWeight.Bold),
    bodyLarge = TextStyle(fontFamily = defaultFontFamily, fontSize = 17.sp, fontWeight = FontWeight.Medium),
    bodyMedium = TextStyle(fontFamily = defaultFontFamily, fontSize = 15.sp),
    bodySmall = TextStyle(fontFamily = defaultFontFamily, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = defaultFontFamily, fontSize = 12.sp, fontWeight = FontWeight.Bold),
)

/**
 * Swift: `.preferredColorScheme(viewModel.isDarkMode ? .dark : .light)` in `MindWellApp`.
 *
 * Dynamic color is deliberately not used — the app's identity is the violet/blue gradient, and
 * Material You would repaint it per wallpaper.
 */
@Composable
fun MotivBotTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MotivBotTypography,
        content = content,
    )
}
