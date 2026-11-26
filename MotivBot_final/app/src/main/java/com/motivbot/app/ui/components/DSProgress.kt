package com.motivbot.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSMotion

// Port of DSProgress.swift.

/** One segment per section, as wide as the section has questions, 4 dp apart. */
@Composable
fun SegmentedProgressBar(weights: List<Int>, fractions: List<Double>, label: String, value: String) {
    val colors = DS.colors
    Row(
        Modifier
            .fillMaxWidth()
            .height(6.dp)
            .semantics(mergeDescendants = true) {
                contentDescription = label
                stateDescription = value
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        weights.forEachIndexed { index, weight ->
            val target = fractions.getOrNull(index)?.toFloat()?.coerceIn(0f, 1f) ?: 0f
            val fraction by animateFloatAsState(target, DSMotion.soft(), label = "segment")
            Box(
                Modifier
                    .weight(maxOf(weight, 1).toFloat())
                    .fillMaxHeight()
                    .background(colors.brandSoft, CircleShape),
            ) {
                if (fraction > 0f) {
                    Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(colors.brand, CircleShape))
                }
            }
        }
    }
}

/** Circular progress; runs counter-clockwise in right-to-left languages. */
@Composable
fun ProgressRing(fraction: Double, modifier: Modifier = Modifier, lineWidth: Dp = 6.dp) {
    val colors = DS.colors
    val rtl = DS.isRtl
    Canvas(modifier) {
        val stroke = lineWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(size.width - stroke, size.height - stroke)
        drawArc(colors.brandSoft, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        scale(scaleX = if (rtl) -1f else 1f, scaleY = 1f) {
            drawArc(
                colors.brand, -90f, 360f * fraction.toFloat().coerceIn(0f, 1f), false,
                Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
            )
        }
    }
}

/** Three small bars: one filled for No, two for Low, three for High. */
@Composable
fun LevelIndicator(filledSteps: Int, color: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) { step ->
            val shape = RoundedCornerShape(3.dp)
            val modifier = Modifier.size(width = 18.dp, height = 8.dp)
            if (step < filledSteps) {
                Box(modifier.background(color, shape))
            } else {
                Box(modifier.border(1.25.dp, color.copy(alpha = 0.6f), shape))
            }
        }
    }
}
