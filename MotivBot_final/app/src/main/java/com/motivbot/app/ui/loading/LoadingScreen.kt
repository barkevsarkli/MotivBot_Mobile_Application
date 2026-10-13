package com.motivbot.app.ui.loading

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.ui.UiMessage
import com.motivbot.app.ui.components.FadeInSlideUp
import com.motivbot.app.ui.components.clickableNoRipple
import com.motivbot.app.ui.theme.BrandBlue
import com.motivbot.app.ui.theme.BrandViolet
import kotlinx.coroutines.delay

/** Swift: the `tips` array cycled by a 3-second `Timer.publish`. */
private val LoadingTips = listOf(
    R.string.loading_tip_1,
    R.string.loading_tip_2,
    R.string.loading_tip_3,
)

private const val TIP_ROTATION_MS = 3_000L

/**
 * Port of `LoadingView.swift` — screen 3.
 *
 * The Swift version drove its animations with `withAnimation(...).repeatForever` inside
 * `.onAppear`. Compose expresses the same thing declaratively with `rememberInfiniteTransition`,
 * which is automatically cancelled when the screen leaves composition — the SwiftUI version's
 * repeating animations and its `Timer` kept running until the view was torn down.
 */
@Composable
fun LoadingScreen(
    errorMessage: UiMessage?,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "loading")

    // Swift: `.linear(duration: 1.2).repeatForever(autoreverses: false)`
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "rotation",
    )

    // Swift: `.easeInOut(duration: 1.0).repeatForever(autoreverses: true)`
    val pulse by transition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1_000), repeatMode = RepeatMode.Reverse),
        label = "pulse",
    )

    var showText by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { showText = true }

    var tipIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(TIP_ROTATION_MS)
            tipIndex = (tipIndex + 1) % LoadingTips.size
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SpinnerWithIcon(rotation = rotation, pulse = pulse)

        FadeInSlideUp(visible = showText, delayMillis = 300, durationMillis = 600) {
            Column(
                modifier = Modifier.padding(top = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.loading_title),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = stringResource(R.string.loading_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                    textAlign = TextAlign.Center,
                )
            }
        }

        AnimatedContent(
            targetState = tipIndex,
            transitionSpec = { fadeIn(tween(500)).togetherWith(fadeOut(tween(500))) },
            label = "tip",
            modifier = Modifier.padding(top = 36.dp),
        ) { index ->
            Text(
                text = stringResource(LoadingTips[index]),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Swift showed the error inline here with a retry pill. Note that in this port the error
        // path also navigates on to the result screen with a fallback quote, exactly as before —
        // this block is what the user sees if they are still on Loading when it surfaces.
        errorMessage?.let { message ->
            Column(
                modifier = Modifier.padding(top = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(message.resId, *message.args.toTypedArray()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(BrandViolet)
                        .clickableNoRipple(onRetry)
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.retry_button),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/** The concentric ring + 30 %-arc spinner + pulsing brain icon from the Swift `ZStack`. */
@Composable
private fun SpinnerWithIcon(rotation: Float, pulse: Float) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val strokeWidth = 4.dp.toPx()
            val inset = strokeWidth / 2
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)

            // Outer static ring.
            drawArc(
                brush = Brush.linearGradient(
                    listOf(BrandViolet.copy(alpha = 0.3f), BrandBlue.copy(alpha = 0.1f)),
                ),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth),
            )

            // Spinning 30 % arc — Swift: `.trim(from: 0, to: 0.3)`.
            rotate(rotation) {
                drawArc(
                    brush = Brush.linearGradient(listOf(BrandViolet, BrandBlue)),
                    startAngle = -90f,
                    sweepAngle = 360f * 0.3f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }

        // The MotivBot mark pulsing inside the ring, replacing the `brain.head.profile` symbol.
        Image(
            painter = painterResource(R.drawable.motivbot_mark),
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .scale(pulse)
                .clip(RoundedCornerShape(14.dp)),
        )
    }
}
