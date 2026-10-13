package com.motivbot.app.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * The palette from the SwiftUI views, converted from `Color(red:green:blue:)` floats to ARGB hex.
 * Every literal below appears verbatim somewhere in the original screens, so the two apps render
 * the same brand.
 */

/** `Color(red: 0.55, green: 0.48, blue: 0.85)` — the primary violet used by every accent. */
val BrandViolet = Color(0xFF8C7AD9)

/** `Color(red: 0.42, green: 0.68, blue: 0.84)` — the blue end of every gradient. */
val BrandBlue = Color(0xFF6BADD6)

/** `Color(red: 0.85, green: 0.65, blue: 0.30)` / `0.90, 0.75, 0.40` — the "sparkles" gold. */
val AccentGoldStart = Color(0xFFD9A64D)
val AccentGoldEnd = Color(0xFFE6BF66)

/** `Color(red: 0.30, green: 0.78, blue: 0.55)` / `0.25, 0.65, 0.50` — completion checkmark. */
val SuccessGreenStart = Color(0xFF4DC78C)
val SuccessGreenEnd = Color(0xFF40A680)

/** Dark-mode background gradient stops. */
val DarkBackgroundStart = Color(0xFF141426)
val DarkBackgroundMid = Color(0xFF1F1A38)
val DarkBackgroundEnd = Color(0xFF0F0F1F)

/** Light-mode background gradient stops. */
val LightBackgroundStart = Color(0xFFEDE8FA)
val LightBackgroundMid = Color(0xFFDEEBFC)
val LightBackgroundEnd = Color(0xFFF2E6F5)

val DarkSurface = Color(0xFF1A1A2E)
val LightSurface = Color(0xFFFFFFFF)

val DarkOnSurface = Color(0xFFECE9F5)
val LightOnSurface = Color(0xFF1B1B24)

val ErrorRed = Color(0xFFE05A5A)
