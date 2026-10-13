package com.motivbot.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.motivbot.app.domain.model.AppScreen
import com.motivbot.app.ui.components.LanguageToggleButton
import com.motivbot.app.ui.components.ThemeToggleButton
import com.motivbot.app.ui.emotion.EmotionInputScreen
import com.motivbot.app.ui.loading.LoadingScreen
import com.motivbot.app.ui.questionnaire.QuestionnaireScreen
import com.motivbot.app.ui.result.ResultScreen
import com.motivbot.app.ui.theme.DarkBackgroundEnd
import com.motivbot.app.ui.theme.DarkBackgroundMid
import com.motivbot.app.ui.theme.DarkBackgroundStart
import com.motivbot.app.ui.theme.LightBackgroundEnd
import com.motivbot.app.ui.theme.LightBackgroundMid
import com.motivbot.app.ui.theme.LightBackgroundStart

/**
 * Port of `ContentView.swift`: the gradient background, the screen switch, and the top bar with
 * the theme toggle on one side and the language toggle on the other.
 *
 * There is deliberately no Navigation component here. The Swift app switched on a single
 * `AppScreen` enum with no back stack, and the flow is strictly linear and state-driven —
 * introducing a NavHost would add a second source of truth for "where am I".
 */
@Composable
fun MotivBotApp(
    state: WellBeingUiState,
    callbacks: MotivBotCallbacks,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundGradient(state.isDarkMode)),
    ) {
        AnimatedContent(
            targetState = state.currentScreen,
            transitionSpec = {
                when (targetState) {
                    AppScreen.Loading ->
                        (fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.92f))
                            .togetherWith(fadeOut(tween(450)))

                    AppScreen.Result ->
                        (fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 3 })
                            .togetherWith(fadeOut(tween(450)))

                    else ->
                        (slideInHorizontally(tween(450)) { it } + fadeIn(tween(450)))
                            .togetherWith(slideOutHorizontally(tween(450)) { -it } + fadeOut(tween(450)))
                }
            },
            label = "screenTransition",
        ) { screen ->
            when (screen) {
                AppScreen.EmotionInput -> EmotionInputScreen(
                    emotionText = state.emotionText,
                    canProceed = state.canProceedEmotion,
                    onEmotionTextChange = callbacks.onEmotionTextChange,
                    onNext = callbacks.onSubmitEmotion,
                )

                AppScreen.Questionnaire -> QuestionnaireScreen(
                    state = state,
                    onSelectOption = callbacks.onSelectOption,
                    onSelectGridOption = callbacks.onSelectGridOption,
                    onScaleChange = callbacks.onScaleChange,
                    onFreeTextChange = callbacks.onFreeTextChange,
                    onNumberChange = callbacks.onNumberChange,
                    onNext = callbacks.onNextQuestion,
                    onPrevious = callbacks.onPreviousQuestion,
                )

                AppScreen.Loading -> LoadingScreen(
                    errorMessage = state.errorMessage,
                    onRetry = callbacks.onRetry,
                )

                AppScreen.Result -> ResultScreen(
                    aiResponse = state.aiResponse,
                    reflectionText = state.reflectionText,
                    selectedFeelingIndex = state.selectedFeelingIndex,
                    isCompleted = state.isCompleted,
                    shouldOfferShare = state.shouldOfferShare,
                    onReflectionTextChange = callbacks.onReflectionTextChange,
                    onSelectFeeling = callbacks.onSelectFeeling,
                    onSubmitReflection = callbacks.onSubmitReflection,
                    onRestart = callbacks.onRestart,
                    onShareRequested = callbacks.onShareCsv,
                )
            }
        }

        // Top bar overlay, drawn above the screen content exactly as the Swift `VStack` did.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ThemeToggleButton(isDarkMode = state.isDarkMode, onToggle = callbacks.onToggleTheme)
            LanguageToggleButton(language = state.language, onToggle = callbacks.onToggleLanguage)
        }
    }
}

/** Swift: the diagonal `LinearGradient` in `ContentView.backgroundGradient`. */
private fun backgroundGradient(isDarkMode: Boolean): Brush {
    val colors = if (isDarkMode) {
        listOf(DarkBackgroundStart, DarkBackgroundMid, DarkBackgroundEnd)
    } else {
        listOf(LightBackgroundStart, LightBackgroundMid, LightBackgroundEnd)
    }
    return Brush.linearGradient(
        colors = colors,
        start = Offset.Zero,
        end = Offset.Infinite,
    )
}

/**
 * All the user intents the shell forwards to the ViewModel, bundled so screens stay preview-able
 * without a ViewModel and the parameter list stays readable.
 */
data class MotivBotCallbacks(
    val onEmotionTextChange: (String) -> Unit,
    val onSubmitEmotion: () -> Unit,
    val onSelectOption: (Int) -> Unit,
    val onSelectGridOption: (Int, Int) -> Unit,
    val onScaleChange: (Int) -> Unit,
    val onFreeTextChange: (String) -> Unit,
    val onNumberChange: (String) -> Unit,
    val onNextQuestion: () -> Unit,
    val onPreviousQuestion: () -> Unit,
    val onRetry: () -> Unit,
    val onReflectionTextChange: (String) -> Unit,
    val onSelectFeeling: (Int) -> Unit,
    val onSubmitReflection: () -> Unit,
    val onRestart: () -> Unit,
    val onShareCsv: () -> Unit,
    val onToggleTheme: () -> Unit,
    val onToggleLanguage: () -> Unit,
)
