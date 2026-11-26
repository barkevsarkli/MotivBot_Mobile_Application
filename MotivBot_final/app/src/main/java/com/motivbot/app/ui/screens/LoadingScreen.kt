package com.motivbot.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.ui.AnalysisPhase
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.Banner
import com.motivbot.app.ui.components.BannerKind
import com.motivbot.app.ui.components.BottomActionBar
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsAppear
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSSpacing
import kotlinx.coroutines.delay

private val tips = listOf(R.string.loading_tip_1, R.string.loading_tip_2, R.string.loading_tip_3)
private const val TIP_INTERVAL_MILLIS = 6_000L

@Composable
fun LoadingScreen(viewModel: WellBeingViewModel) {
    val phase = viewModel.analysisPhase
    val isFailed = phase is AnalysisPhase.Failed
    var tipIndex by rememberSaveable { mutableIntStateOf(0) }
    val view = LocalView.current
    val context = LocalContext.current

    LaunchedEffect(isFailed) {
        if (isFailed) return@LaunchedEffect
        while (true) {
            delay(TIP_INTERVAL_MILLIS)
            tipIndex = (tipIndex + 1) % tips.size
        }
    }
    // Changing tips would be noisy for TalkBack; the status changes are announced instead.
    LaunchedEffect(phase) {
        val text = when (phase) {
            AnalysisPhase.ModelWakingUp -> context.getString(R.string.loading_model_starting)
            is AnalysisPhase.Failed -> context.getString(phase.error.messageRes)
            else -> null
        }
        if (text != null) view.announceForAccessibility(text)
    }

    ScreenScaffold(
        topBar = { TopBar() },
        bottomBar = {
            AnimatedVisibility(isFailed, enter = fadeIn(DSMotion.standard()), exit = fadeOut(DSMotion.standard())) {
                BottomActionBar {
                    DSPrimaryButton(stringResource(R.string.retry_button), icon = Icons.Filled.Refresh) { viewModel.submitForAnalysis() }
                }
            }
        },
    ) { padding ->
        ScreenColumn(
            Modifier.padding(padding).padding(bottom = DSSpacing.l).dsAppear(),
            spacing = DSSpacing.xl,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(DSSpacing.xl))
            BreathingMark(isFailed)

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(DSSpacing.s)) {
                Text(
                    stringResource(if (isFailed) R.string.loading_failed_title else R.string.loading_title),
                    style = DSFont.title,
                    color = DS.colors.textPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.semantics { heading() },
                )
                val subtitle = when (phase) {
                    AnalysisPhase.ModelWakingUp -> R.string.loading_model_starting
                    is AnalysisPhase.Failed -> R.string.loading_failed_subtitle
                    else -> R.string.loading_subtitle
                }
                AnimatedContent(
                    subtitle,
                    transitionSpec = { fadeIn(DSMotion.gentle()) togetherWith fadeOut(DSMotion.gentle()) },
                    label = "subtitle",
                ) { res ->
                    Text(
                        stringResource(res),
                        style = DSFont.body,
                        color = DS.colors.textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            AnimatedContent(
                targetState = phase.statusKind,
                transitionSpec = { fadeIn(DSMotion.standard()) togetherWith fadeOut(DSMotion.standard()) },
                label = "status",
            ) { kind ->
                when (kind) {
                    StatusKind.Failed -> {
                        val error = (viewModel.analysisPhase as? AnalysisPhase.Failed)?.error
                        if (error != null) Banner(BannerKind.Notice, stringResource(error.messageRes))
                    }
                    StatusKind.WakingUp -> Column(verticalArrangement = Arrangement.spacedBy(DSSpacing.m)) {
                        Banner(BannerKind.Info, stringResource(R.string.loading_waking_banner))
                        Tip(tipIndex)
                    }
                    StatusKind.Working -> Tip(tipIndex)
                }
            }
        }
    }
}

private enum class StatusKind { Working, WakingUp, Failed }

private val AnalysisPhase.statusKind: StatusKind
    get() = when (this) {
        is AnalysisPhase.Failed -> StatusKind.Failed
        AnalysisPhase.ModelWakingUp -> StatusKind.WakingUp
        else -> StatusKind.Working
    }

@Composable
private fun Tip(index: Int) {
    AnimatedContent(
        index,
        transitionSpec = { fadeIn(DSMotion.gentle()) togetherWith fadeOut(DSMotion.gentle()) },
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics { },
        label = "tip",
    ) { current ->
        Text(
            stringResource(tips[current]),
            style = DSFont.callout,
            color = DS.colors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = DSSpacing.m),
        )
    }
}

/** Two soft circles that grow and shrink over four seconds, like a slow breath. */
@Composable
private fun BreathingMark(isFailed: Boolean) {
    val colors = DS.colors
    val reduceMotion = DS.reduceMotion
    val transition = rememberInfiniteTransition(label = "breath")
    val breath by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "breathValue",
    )
    val progress = if (reduceMotion) 1f else breath
    Box(Modifier.size(148.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(148.dp)
                .graphicsLayer {
                    val scale = 0.82f + 0.18f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .background(colors.brandSoft, CircleShape),
        )
        Box(
            Modifier
                .size(104.dp)
                .graphicsLayer {
                    val scale = 0.88f + 0.12f * progress
                    scaleX = scale
                    scaleY = scale
                }
                .background(colors.brand.copy(alpha = 0.18f), CircleShape),
        )
        AnimatedContent(
            isFailed,
            transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.7f)) togetherWith (fadeOut() + scaleOut(targetScale = 0.7f)) },
            label = "mark",
        ) { failed ->
            Icon(
                if (failed) Icons.Outlined.Cloud else Icons.Filled.Eco,
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}
