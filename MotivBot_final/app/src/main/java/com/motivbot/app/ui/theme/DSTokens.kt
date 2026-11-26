package com.motivbot.app.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Port of DSTokens.swift and the colour sets in Assets.xcassets: one source for every value the screens use.

@Immutable
data class DSColors(
    val backgroundTop: Color,
    val backgroundBottom: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val stroke: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val brand: Color,
    val brandSoft: Color,
    val onBrand: Color,
    val steady: Color,
    val steadySoft: Color,
    val gentle: Color,
    val gentleSoft: Color,
    val care: Color,
    val careSoft: Color,
    val peach: Color,
    val peachSoft: Color,
    val sky: Color,
    val skySoft: Color,
    val mint: Color,
    val mintSoft: Color,
    val isDark: Boolean,
) {
    companion object {
        val Light = DSColors(
            backgroundTop = Color(0xFFF6F4FB),
            backgroundBottom = Color(0xFFEEF3FA),
            surface = Color(0xFFFFFFFF),
            surfaceMuted = Color(0xFFF7F5FC),
            stroke = Color(0xFFE4E0EF),
            textPrimary = Color(0xFF1F1B2E),
            textSecondary = Color(0xFF5A5570),
            textTertiary = Color(0xFF6E6985),
            brand = Color(0xFF5B4BC4),
            brandSoft = Color(0xFFECE8FB),
            onBrand = Color(0xFFFFFFFF),
            steady = Color(0xFF2A6F5E),
            steadySoft = Color(0xFFE3F2EC),
            gentle = Color(0xFF83601A),
            gentleSoft = Color(0xFFF7EDD3),
            care = Color(0xFF8E4F7E),
            careSoft = Color(0xFFF4E6F0),
            peach = Color(0xFF9A4F2E),
            peachSoft = Color(0xFFFCEBE2),
            sky = Color(0xFF2D6194),
            skySoft = Color(0xFFE3EEF9),
            mint = Color(0xFF2D6B43),
            mintSoft = Color(0xFFE2F3E8),
            isDark = false,
        )

        val Dark = DSColors(
            backgroundTop = Color(0xFF12111C),
            backgroundBottom = Color(0xFF161A26),
            surface = Color(0xFF1E1C2B),
            surfaceMuted = Color(0xFF262435),
            stroke = Color(0xFF2E2B40),
            textPrimary = Color(0xFFF2F0FA),
            textSecondary = Color(0xFFB4AFC8),
            textTertiary = Color(0xFF9A95B0),
            brand = Color(0xFFA99BFF),
            brandSoft = Color(0xFF2A2550),
            onBrand = Color(0xFF17142B),
            steady = Color(0xFF7FD1B9),
            steadySoft = Color(0xFF1C3530),
            gentle = Color(0xFFE3C27A),
            gentleSoft = Color(0xFF3A3020),
            care = Color(0xFFE0A6CF),
            careSoft = Color(0xFF3A2435),
            peach = Color(0xFFF0B89A),
            peachSoft = Color(0xFF3B2A22),
            sky = Color(0xFF9CC8F2),
            skySoft = Color(0xFF1D2C3D),
            mint = Color(0xFF92D6A8),
            mintSoft = Color(0xFF1D3324),
            isDark = true,
        )
    }
}

object DSSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val s = 12.dp
    val m = 16.dp
    val l = 24.dp
    val xl = 32.dp
    val xxl = 48.dp
    val screen = 20.dp
}

object DSRadius {
    val small = 12.dp
    val medium = 16.dp
    val large = 24.dp
    val slide = 32.dp
}

object DSSize {
    val minTap = 44.dp
    val answerOptionMinHeight = 56.dp
    val buttonMinHeight = 54.dp
    val contentMaxWidth = 560.dp
}

object DSMotion {
    const val QUICK_MILLIS = 200
    const val STANDARD_MILLIS = 350
    const val GENTLE_MILLIS = 500
    const val AUTO_ADVANCE_DELAY_MILLIS = 380L

    fun <T> quick() = tween<T>(QUICK_MILLIS, easing = LinearOutSlowInEasing)
    fun <T> standard() = tween<T>(STANDARD_MILLIS, easing = FastOutSlowInEasing)
    fun <T> gentle() = tween<T>(GENTLE_MILLIS, easing = LinearOutSlowInEasing)
    fun <T> soft() = spring<T>(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}
