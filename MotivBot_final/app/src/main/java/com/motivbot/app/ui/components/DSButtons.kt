package com.motivbot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing

// Port of DSButtons.swift.

/** Scales a control down slightly while pressed (`DSPressableStyle`). */
fun Modifier.pressable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    pressedScale: Float = 0.98f,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val reduceMotion = DS.reduceMotion
    val scale by animateFloatAsState(if (pressed && !reduceMotion) pressedScale else 1f, DSMotion.quick(), label = "press")
    this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = role, onClick = onClick)
}

@Composable
private fun ButtonContent(title: String, icon: ImageVector?, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = DSFont.heading, color = color, textAlign = TextAlign.Center)
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun DSPrimaryButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = DS.colors
    val shape = RoundedCornerShape(DSRadius.medium)
    val background by animateColorAsState(if (enabled) colors.brand else colors.surfaceMuted, DSMotion.standard(), label = "fill")
    val content = if (enabled) colors.onBrand else colors.textTertiary
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DSSize.buttonMinHeight)
            .pressable(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .background(background, shape)
            .border(1.dp, if (enabled) Color.Transparent else colors.stroke, shape)
            .padding(horizontal = DSSpacing.l, vertical = DSSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(title, icon, content)
    }
}

@Composable
fun DSSecondaryButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = DS.colors
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DSSize.buttonMinHeight)
            .pressable(enabled = enabled, pressedScale = 0.97f, onClick = onClick)
            .background(colors.brandSoft, RoundedCornerShape(DSRadius.medium))
            .padding(horizontal = DSSpacing.m, vertical = DSSpacing.s),
        contentAlignment = Alignment.Center,
    ) {
        ButtonContent(title, icon, if (enabled) colors.brand else colors.textTertiary)
    }
}

@Composable
fun DSTertiaryButton(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val colors = DS.colors
    Row(
        modifier
            .defaultMinSize(minHeight = DSSize.minTap)
            .pressable(onClick = onClick)
            .padding(horizontal = DSSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
        Text(title, style = DSFont.label, color = colors.brand)
    }
}

/** A 44 dp round glass button with one icon (`GlassIconButton`). */
@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier
            .size(DSSize.minTap)
            .dsGlass(CircleShape)
            .pressable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = DS.colors.textPrimary, modifier = Modifier.size(20.dp))
    }
}
