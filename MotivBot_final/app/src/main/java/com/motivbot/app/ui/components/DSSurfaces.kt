package com.motivbot.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Port of DSSurfaces.swift.

@Composable
fun AppBackground(modifier: Modifier = Modifier) {
    val colors = DS.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(colors.backgroundTop, colors.backgroundBottom))),
    )
}

/** The standard card: surface fill, 1 px stroke, soft shadow, large radius. */
fun Modifier.dsCard(fill: Color? = null, stroke: Color? = null, padding: Dp = DSSpacing.l - 4.dp): Modifier = composed {
    val colors = DS.colors
    val shape = RoundedCornerShape(DSRadius.large)
    this
        .fillMaxWidth()
        .shadow(elevation = 6.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.08f))
        .background(fill ?: colors.surface, shape)
        .border(1.dp, stroke ?: colors.stroke, shape)
        .padding(padding)
}

/**
 * Stand-in for iOS Liquid Glass: a translucent surface with a hairline stroke. Android has no
 * system glass material; this matches the iOS Reduce Transparency look.
 */
fun Modifier.dsGlass(shape: Shape): Modifier = composed {
    val colors = DS.colors
    this
        .shadow(elevation = 8.dp, shape = shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.10f))
        .background(colors.surface.copy(alpha = if (colors.isDark) 0.92f else 0.94f), shape)
        .border(1.dp, colors.stroke, shape)
}

/** Fades and lifts content in when it first appears, as `dsAppear(delay:)`. */
fun Modifier.dsAppear(delayMillis: Int = 0): Modifier = composed {
    val reduceMotion = DS.reduceMotion
    val progress = remember { Animatable(0f) }
    val lift = with(LocalDensity.current) { 14.dp.toPx() }
    LaunchedEffect(Unit) {
        if (!reduceMotion) delay(delayMillis.toLong())
        launch { progress.animateTo(1f, DSMotion.gentle()) }
    }
    graphicsLayer {
        alpha = progress.value
        translationY = if (reduceMotion) 0f else (1f - progress.value) * lift
    }
}
