package com.motivbot.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.motivbot.app.R
import com.motivbot.app.domain.PredictionRequest
import com.motivbot.app.ui.EmotionTextStatus
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.Banner
import com.motivbot.app.ui.components.BannerKind
import com.motivbot.app.ui.components.BottomActionBar
import com.motivbot.app.ui.components.DSPrimaryButton
import com.motivbot.app.ui.components.GlassIconButton
import com.motivbot.app.ui.components.ScreenColumn
import com.motivbot.app.ui.components.ScreenScaffold
import com.motivbot.app.ui.components.TopBar
import com.motivbot.app.ui.components.dsAppear
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSFont
import com.motivbot.app.ui.theme.DSMotion
import com.motivbot.app.ui.theme.DSRadius
import com.motivbot.app.ui.theme.DSSpacing

@Composable
fun EmotionInputScreen(viewModel: WellBeingViewModel, onBack: () -> Unit) {
    val focusManager = LocalFocusManager.current
    val canGoBack = viewModel.canChangeLanguage
    // Android's back gesture returns to the welcome screen while the language can still change.
    BackHandler(enabled = canGoBack) {
        focusManager.clearFocus()
        onBack()
    }

    ScreenScaffold(
        topBar = {
            TopBar(leading = {
                AnimatedVisibility(canGoBack, enter = fadeIn(DSMotion.standard()), exit = fadeOut(DSMotion.standard())) {
                    GlassIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back_button)) {
                        focusManager.clearFocus()
                        onBack()
                    }
                }
            })
        },
        bottomBar = {
            BottomActionBar {
                DSPrimaryButton(
                    stringResource(R.string.next_button),
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    enabled = viewModel.canProceedEmotion,
                ) {
                    focusManager.clearFocus()
                    viewModel.submitEmotionAndProceed()
                }
            }
        },
    ) { padding ->
        ScreenColumn(Modifier.padding(padding).padding(top = DSSpacing.s, bottom = DSSpacing.l)) {
            Column(Modifier.dsAppear(), verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
                Text(
                    stringResource(R.string.emotion_title),
                    style = DSFont.display,
                    color = DS.colors.textPrimary,
                    modifier = Modifier.semantics { heading() },
                )
                Text(stringResource(R.string.emotion_prompt), style = DSFont.body, color = DS.colors.textSecondary)
            }
            Editor(viewModel.emotionText, { viewModel.emotionText = it }, Modifier.dsAppear(100))
            Hint(viewModel.emotionTextStatus, viewModel.canProceedEmotion, Modifier.dsAppear(200))
        }
    }
}

@Composable
private fun Editor(text: String, onChange: (String) -> Unit, modifier: Modifier) {
    val colors = DS.colors
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(DSRadius.large)
    val stroke by animateColorAsState(if (focused) colors.brand else colors.stroke, DSMotion.quick(), label = "stroke")
    val strokeWidth by animateDpAsState(if (focused) 1.5.dp else 1.dp, DSMotion.quick(), label = "width")
    val label = stringResource(R.string.your_thoughts)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(DSSpacing.xs)) {
        Text(label, style = DSFont.caption, color = colors.textSecondary)
        BasicTextField(
            value = text,
            onValueChange = onChange,
            textStyle = DSFont.body.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .onFocusChanged { focused = it.isFocused }
                .semantics { contentDescription = label },
            decorationBox = { inner ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp)
                        .background(colors.surface, shape)
                        .border(strokeWidth, stroke, shape)
                        .padding(horizontal = DSSpacing.m, vertical = DSSpacing.m),
                ) {
                    if (text.isEmpty()) {
                        Text(stringResource(R.string.emotion_placeholder), style = DSFont.body, color = colors.textTertiary)
                    }
                    inner()
                }
            },
        )
    }
}

private enum class HintKind { WrongLanguage, TooLong, Ready, Hint }

@Composable
private fun Hint(status: EmotionTextStatus, canProceed: Boolean, modifier: Modifier) {
    val kind = when {
        status == EmotionTextStatus.WrongLanguage -> HintKind.WrongLanguage
        status == EmotionTextStatus.TooLong -> HintKind.TooLong
        canProceed -> HintKind.Ready
        else -> HintKind.Hint
    }
    AnimatedContent(
        targetState = kind,
        transitionSpec = { fadeIn(DSMotion.standard()) togetherWith fadeOut(DSMotion.standard()) },
        modifier = modifier,
        label = "hint",
    ) { current ->
        val colors = DS.colors
        when (current) {
            HintKind.WrongLanguage -> Banner(BannerKind.Notice, stringResource(R.string.emotion_wrong_language))
            HintKind.TooLong -> Banner(
                BannerKind.Notice,
                stringResource(R.string.emotion_too_long, PredictionRequest.TEXT_LENGTH_RANGE.last),
            )
            HintKind.Ready -> Row(
                horizontalArrangement = Arrangement.spacedBy(DSSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = colors.steady, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.emotion_ready), style = DSFont.footnote, color = colors.steady)
            }
            HintKind.Hint -> Text(
                stringResource(R.string.emotion_hint),
                style = DSFont.footnote,
                color = colors.textSecondary,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
