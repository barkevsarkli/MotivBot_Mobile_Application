package com.motivbot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motivbot.app.ui.theme.BrandBlue
import com.motivbot.app.ui.theme.BrandViolet

/**
 * The `.opacity(showContent ? 1 : 0).offset(y: showContent ? 0 : 20)` entrance the Swift views
 * repeated on almost every element, expressed once.
 */
@Composable
fun FadeInSlideUp(
    visible: Boolean,
    delayMillis: Int = 0,
    durationMillis: Int = 700,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(durationMillis, delayMillis)) +
            slideInVertically(tween(durationMillis, delayMillis)) { it / 4 },
    ) {
        content()
    }
}

/** The violet→blue gradient that fills every primary button and accent in the Swift design. */
val BrandGradient: Brush
    get() = Brush.horizontalGradient(listOf(BrandViolet, BrandBlue))

val BrandGradientDiagonal: Brush
    get() = Brush.linearGradient(listOf(BrandViolet, BrandBlue))

val DisabledGradient: Brush
    get() = Brush.horizontalGradient(listOf(Color.Gray.copy(alpha = 0.4f), Color.Gray.copy(alpha = 0.3f)))

/**
 * Compose stand-in for SwiftUI's `.background(.ultraThinMaterial)`.
 *
 * Compose has no runtime blur below API 31 and no material hierarchy at all, so the design intent
 * (a translucent card that lets the gradient through) is approximated with a low-alpha surface
 * plus a hairline border — visually the closest match that behaves identically on every API level.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderColor: Color = Color.Gray.copy(alpha = 0.1f),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.border(1.dp, borderColor, shape),
        shape = shape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
        tonalElevation = 0.dp,
        content = content,
    )
}

/** The full-width gradient CTA used on every screen ("Next", "Finish", "Complete session"). */
@Composable
fun GradientButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) BrandGradient else DisabledGradient)
            // Dropping the clickable modifier makes the button inert, but says nothing to
            // accessibility services — TalkBack would still announce it as actionable. The explicit
            // Button role plus `disabled()` is what actually communicates the state.
            .semantics {
                role = Role.Button
                if (!enabled) disabled()
            }
            .then(
                if (enabled) {
                    Modifier.clickableNoRipple(onClick)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            trailingIcon?.invoke()
        }
    }
}

/** A radio dot matching the Swift `Circle().stroke(...)` + inner filled circle pattern. */
@Composable
fun BrandRadioDot(
    selected: Boolean,
    outerSize: Dp = 24.dp,
    innerSize: Dp = 14.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(outerSize)
            .border(
                width = 2.dp,
                color = if (selected) BrandViolet else Color.Gray.copy(alpha = 0.3f),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .padding(1.dp)
                    .size(innerSize)
                    .clip(CircleShape)
                    .background(BrandGradientDiagonal),
            )
        }
    }
}

/**
 * `.buttonStyle(.plain)` — a tap target with no Material ripple or elevation, which is what every
 * custom-drawn control in the SwiftUI original behaves like.
 */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick,
    )
}
