package com.motivbot.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSize
import com.motivbot.app.ui.theme.DSSpacing

// Port of DSSelection.swift.

@Composable
fun AnswerOptionRow(title: String, isSelected: Boolean, onClick: () -> Unit) {
    val colors = DS.colors
    val shape = RoundedCornerShape(DSRadius.medium)
    val fill by animateColorAsState(if (isSelected) colors.brandSoft else colors.surface, DSMotion.quick(), label = "fill")
    val stroke by animateColorAsState(if (isSelected) colors.brand else colors.stroke, DSMotion.quick(), label = "stroke")
    val strokeWidth by animateDpAsState(if (isSelected) 1.5.dp else 1.dp, DSMotion.quick(), label = "width")
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = DSSize.answerOptionMinHeight)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .background(fill, shape)
            .border(strokeWidth, stroke, shape)
            .padding(horizontal = DSSpacing.m, vertical = DSSpacing.s)
            .semantics { selected = isSelected },
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SelectionIndicator(isSelected)
        Text(title, style = DSFont.body, color = colors.textPrimary, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SelectionIndicator(isSelected: Boolean) {
    val colors = DS.colors
    val ring by animateColorAsState(if (isSelected) colors.brand else colors.textTertiary, DSMotion.quick(), label = "ring")
    Box(Modifier.size(26.dp).border(2.dp, ring, CircleShape), contentAlignment = Alignment.Center) {
        AnimatedVisibility(
            visible = isSelected,
            enter = fadeIn(DSMotion.quick()) + scaleIn(DSMotion.quick(), initialScale = 0.5f),
            exit = fadeOut(DSMotion.quick()) + scaleOut(DSMotion.quick(), targetScale = 0.5f),
        ) {
            Box(Modifier.fillMaxSize().background(colors.brand, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onBrand, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun SelectableChip(title: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = DS.colors
    val fill by animateColorAsState(if (isSelected) colors.brand else colors.surface, DSMotion.quick(), label = "chip")
    Box(
        modifier
            .defaultMinSize(minHeight = DSSize.minTap)
            .pressable(role = Role.RadioButton, onClick = onClick)
            .background(fill, CircleShape)
            .border(1.dp, if (isSelected) fill else colors.stroke, CircleShape)
            .padding(horizontal = DSSpacing.m, vertical = DSSpacing.xs)
            .semantics { selected = isSelected },
        contentAlignment = Alignment.Center,
    ) {
        Text(title, style = DSFont.label, color = if (isSelected) colors.onBrand else colors.textPrimary, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipGroup(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(DSSpacing.xs),
    ) { content() }
}
