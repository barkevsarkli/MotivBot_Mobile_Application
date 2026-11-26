package com.motivbot.app.ui.theme

import android.content.Context
import android.provider.Settings
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.localized

val LocalDSColors = staticCompositionLocalOf { DSColors.Light }
val LocalReduceMotion = staticCompositionLocalOf { false }
val LocalAppLanguage = staticCompositionLocalOf { AppLanguage.Default }

/** Shorthand for the design-system tokens, as `Color.ds…` on iOS. */
object DS {
    val colors: DSColors
        @Composable @ReadOnlyComposable get() = LocalDSColors.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current

    val isRtl: Boolean
        @Composable @ReadOnlyComposable get() = LocalLayoutDirection.current == LayoutDirection.Rtl
}

/**
 * Port of `DSFont`. Body text gets more line spacing in Persian, whose script needs more room
 * between lines than Latin script (`dsBodyText()` on iOS).
 */
object DSFont {
    private fun style(size: Int, weight: FontWeight = FontWeight.Normal, lineHeight: TextUnit = TextUnit.Unspecified) =
        TextStyle(fontSize = size.sp, fontWeight = weight, lineHeight = lineHeight)

    val display = style(32, FontWeight.Bold, 1.18.em)
    val title = style(22, FontWeight.SemiBold, 1.25.em)
    val heading = style(17, FontWeight.SemiBold, 1.3.em)
    val label = style(14, FontWeight.SemiBold, 1.3.em)
    val caption = style(12, FontWeight.Medium)

    @Composable @ReadOnlyComposable
    private fun bodyLine(): TextUnit = if (DS.isRtl) 1.6.em else 1.42.em

    val question: TextStyle @Composable @ReadOnlyComposable get() = style(20, FontWeight.Medium, bodyLine())
    val quote: TextStyle @Composable @ReadOnlyComposable get() = style(22, FontWeight.Medium, bodyLine())
    val body: TextStyle @Composable @ReadOnlyComposable get() = style(16, lineHeight = bodyLine())
    val bodyEmphasis: TextStyle @Composable @ReadOnlyComposable get() = style(16, FontWeight.SemiBold, bodyLine())
    val callout: TextStyle @Composable @ReadOnlyComposable get() = style(15, lineHeight = bodyLine())
    val footnote: TextStyle @Composable @ReadOnlyComposable get() = style(13, lineHeight = bodyLine())
}

/**
 * Applies the colours, the app language (strings, layout direction) and Reduce Motion.
 *
 * The language is switched by giving the composition a context configured for it, the way
 * `LocalizationManager` swaps the `.lproj` bundle on iOS: no activity restart, and the language is
 * not remembered by the system between launches.
 */
@Composable
fun MotivBotTheme(
    language: AppLanguage,
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val baseContext = LocalContext.current
    // Keyed on the configuration too, so rotation and font scale changes reach the localized resources.
    val baseConfiguration = LocalConfiguration.current
    val localizedContext = remember(baseContext, baseConfiguration, language) { LocalizedContext(baseContext, language) }
    val colors = if (darkTheme) DSColors.Dark else DSColors.Light
    val reduceMotion = remember(baseContext) { animationsDisabled(baseContext) }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandSoft,
            background = colors.backgroundTop, surface = colors.surface, onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary, outline = colors.stroke, surfaceContainerLow = colors.surface,
            surfaceContainerHigh = colors.surface,
        )
    } else {
        lightColorScheme(
            primary = colors.brand, onPrimary = colors.onBrand, primaryContainer = colors.brandSoft,
            background = colors.backgroundTop, surface = colors.surface, onSurface = colors.textPrimary,
            onSurfaceVariant = colors.textSecondary, outline = colors.stroke, surfaceContainerLow = colors.surface,
            surfaceContainerHigh = colors.surface,
        )
    }

    CompositionLocalProvider(
        LocalContext provides localizedContext,
        LocalConfiguration provides localizedContext.resources.configuration,
        LocalLayoutDirection provides if (language.isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
        LocalDSColors provides colors,
        LocalReduceMotion provides reduceMotion,
        LocalAppLanguage provides language,
        LocalTextSelectionColors provides TextSelectionColors(colors.brand, colors.brand.copy(alpha = 0.3f)),
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/** The activity context with its resources in [language]; activity calls still go to the activity. */
private class LocalizedContext(base: Context, language: AppLanguage) : android.content.ContextWrapper(base) {
    private val localized = base.localized(language)
    override fun getResources(): android.content.res.Resources = localized.resources
    override fun getAssets(): android.content.res.AssetManager = localized.assets
}

/** "Remove animations" in Android's accessibility settings, the counterpart of Reduce Motion. */
private fun animationsDisabled(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
