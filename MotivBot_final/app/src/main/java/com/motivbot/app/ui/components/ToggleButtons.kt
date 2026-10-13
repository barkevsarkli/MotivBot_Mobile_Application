package com.motivbot.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.core.AppLanguage

/** `moon.stars.fill` in the Swift original was tinted purple→blue; `sun.max.fill` orange→yellow. */
private val MoonTint = Color(0xFF9C7BE0)
private val SunTint = Color(0xFFF5A524)

/**
 * Port of `ThemeToggleView.swift`.
 *
 * The Swift version drove the spin with a `@State var isRotating` that it flipped on tap. Compose
 * derives it from the theme instead, so the icon can never drift out of sync with the actual
 * state — e.g. after process death and restore.
 */
@Composable
fun ThemeToggleButton(
    isDarkMode: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rotation by animateFloatAsState(
        targetValue = if (isDarkMode) 360f else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "themeToggleRotation",
    )

    val description = stringResource(R.string.theme_toggle)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickableNoRipple(onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (isDarkMode) Icons.Filled.DarkMode else Icons.Filled.LightMode,
            contentDescription = null,
            tint = if (isDarkMode) MoonTint else SunTint,
            modifier = Modifier
                .size(22.dp)
                .rotate(rotation),
        )
    }
}

/**
 * Port of `LanguageToggleView.swift` — the circular TR/FA badge in the top corner.
 *
 * The badge pops slightly whenever the language changes; keying the animation on the language
 * itself replaces the Swift `isAnimating` toggle-state with something derived.
 */
@Composable
fun LanguageToggleButton(
    language: AppLanguage,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Alternate between two scales per language so each switch produces a visible spring.
    val scale by animateFloatAsState(
        targetValue = if (language.ordinal % 2 == 0) 1f else 1.15f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMedium),
        label = "languageToggleScale",
    )

    val description = stringResource(R.string.language_toggle)

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f))
            .clickableNoRipple(onToggle)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = language.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.scale(scale),
        )
    }
}
