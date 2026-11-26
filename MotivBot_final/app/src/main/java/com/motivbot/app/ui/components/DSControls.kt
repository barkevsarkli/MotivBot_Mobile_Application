package com.motivbot.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.data.settings.ThemePreference
import com.motivbot.app.ui.LocalizationManager
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing

// Port of DSControls.swift.

/** The stored theme choice and the appearance it currently produces. */
class ThemeController(val preference: ThemePreference, val isDark: Boolean, val set: (ThemePreference) -> Unit)

val LocalThemeController = staticCompositionLocalOf { ThemeController(ThemePreference.System, false) {} }

/**
 * One tap switches to the opposite of what is on screen; the next tap goes back to following the system.
 */
@Composable
fun ThemeToggle() {
    val controller = LocalThemeController.current
    val haptics = LocalHapticFeedback.current
    val next = if (controller.preference != ThemePreference.System) {
        ThemePreference.System
    } else if (controller.isDark) {
        ThemePreference.Light
    } else {
        ThemePreference.Dark
    }
    val targetIsDark = !controller.isDark
    val label = stringResource(if (targetIsDark) R.string.theme_switch_to_dark else R.string.theme_switch_to_light)
    Box(
        Modifier
            .size(DSSize.minTap)
            .dsGlass(CircleShape)
            .pressable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                controller.set(next)
            }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = targetIsDark,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.6f)) },
            label = "theme",
        ) { dark ->
            Icon(
                if (dark) Icons.Outlined.DarkMode else Icons.Outlined.LightMode,
                contentDescription = null,
                tint = DS.colors.textPrimary,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** Segmented Türkçe / فارسی picker; disabled while the language is locked. */
@Composable
fun LanguagePicker(localization: LocalizationManager) {
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .dsGlass(RoundedCornerShape(DSRadius.medium + 4.dp))
            .padding(DSSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xxs),
    ) {
        AppLanguage.entries.forEach { language ->
            val selected = localization.currentLanguage == language
            val colors = DS.colors
            val background by animateColorAsState(if (selected) colors.brand else Color.Transparent, DSMotion.standard(), label = "lang")
            Box(
                Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = DSSize.minTap)
                    .background(background, RoundedCornerShape(DSRadius.medium))
                    .pressable(enabled = !localization.isLocked, role = Role.Tab) {
                        if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        localization.setLanguage(language)
                    }
                    .semantics { this.selected = selected },
                contentAlignment = Alignment.Center,
            ) {
                // Each name is written in its own language, so it reads the same in both.
                Text(
                    text = stringResource(language.nameRes),
                    style = DSFont.heading,
                    color = if (selected) colors.onBrand else colors.textPrimary,
                )
            }
        }
    }
}

/** Leading content, trailing items, then the theme toggle. */
@Composable
fun TopBar(
    leading: @Composable RowScope.() -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val top = DS.colors.backgroundTop
    Row(
        Modifier
            .fillMaxWidth()
            // Content scrolls under the bar; fade it out behind the status bar and the buttons,
            // like the scroll edge effect on iOS.
            .background(Brush.verticalGradient(0f to top, 0.75f to top.copy(alpha = 0.95f), 1f to top.copy(alpha = 0f)))
            .statusBarsPadding()
            .padding(start = DSSpacing.screen - 4.dp, end = DSSpacing.screen - 4.dp, top = DSSpacing.xxs, bottom = DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
            trailing()
            ThemeToggle()
        }
    }
}

/** Floating glass bar at the bottom of the screen for the main action(s). */
@Composable
fun BottomActionBar(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = DSSize.contentMaxWidth)
                .padding(horizontal = DSSpacing.screen - 8.dp)
                .padding(bottom = DSSpacing.xs)
                .dsGlass(RoundedCornerShape(DSRadius.large))
                .padding(DSSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
            content = content,
        )
    }
}

/** Centred column at the readable width, with the screen margins. */
@Composable
fun ScreenColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = DSSpacing.l,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier
                .widthIn(max = DSSize.contentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = DSSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(spacing),
            horizontalAlignment = horizontalAlignment,
            content = content,
        )
    }
}

/**
 * A screen: top bar, scrolling content that runs under the bars, and an optional bottom bar,
 * as `safeAreaInset(edge:)` on iOS. The content box is at least as tall as the screen, so content
 * can be centred in it.
 */
@Suppress("ModifierParameter") // contentModifier styles the content box, not the scaffold.
@Composable
fun ScreenScaffold(
    topBar: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    contentModifier: Modifier = Modifier,
    content: @Composable BoxScope.(PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = topBar,
        bottomBar = bottomBar,
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val viewport = maxHeight
            Box(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .then(contentModifier),
            ) {
                Box(Modifier.fillMaxWidth().heightIn(min = viewport)) {
                    content(padding)
                }
            }
        }
    }
}
