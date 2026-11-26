package com.motivbot.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.motivbot.app.domain.AppScreen
import com.motivbot.app.ui.components.AppBackground
import com.motivbot.app.ui.screens.EmotionInputScreen
import com.motivbot.app.ui.screens.LoadingScreen
import com.motivbot.app.ui.screens.QuestionnaireScreen
import com.motivbot.app.ui.screens.ResultScreen
import com.motivbot.app.ui.screens.ResumePromptScreen
import com.motivbot.app.ui.screens.WelcomeScreen
import com.motivbot.app.ui.theme.DS
import com.motivbot.app.ui.theme.DSMotion

private sealed interface Route {
    data object Resume : Route
    data object Welcome : Route
    data class Screen(val screen: AppScreen) : Route
}

/** Port of `ContentView`: picks the screen and animates between them. */
@Composable
fun MotivBotApp(viewModel: WellBeingViewModel) {
    // True once the user leaves the welcome screen. View state only: the view model starts on the emotion
    // input screen, and the welcome screen is shown in front of it until the user taps Start.
    var hasStarted by rememberSaveable { mutableStateOf(false) }
    var lastScreen by rememberSaveable { mutableStateOf(viewModel.currentScreen) }
    val reduceMotion = DS.reduceMotion
    val lift = with(LocalDensity.current) { 16.dp.roundToPx() }

    // A restart returns to the emotion input screen; show the welcome screen again.
    LaunchedEffect(viewModel.currentScreen) {
        if (viewModel.currentScreen == AppScreen.EmotionInput && lastScreen != AppScreen.EmotionInput) hasStarted = false
        lastScreen = viewModel.currentScreen
    }

    val route = when {
        viewModel.resumableSession != null -> Route.Resume
        viewModel.currentScreen == AppScreen.EmotionInput && !hasStarted -> Route.Welcome
        else -> Route.Screen(viewModel.currentScreen)
    }

    Box(Modifier.fillMaxSize()) {
        AppBackground()
        AnimatedContent(
            targetState = route,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(DSMotion.standard()) togetherWith fadeOut(DSMotion.standard())
                } else {
                    (fadeIn(DSMotion.standard()) + slideInVertically(DSMotion.standard()) { lift }) togetherWith
                        fadeOut(DSMotion.standard())
                }
            },
            label = "route",
        ) { current ->
            when (current) {
                Route.Resume -> ResumePromptScreen(viewModel) { hasStarted = true }
                Route.Welcome -> WelcomeScreen(viewModel.localization) { hasStarted = true }
                is Route.Screen -> when (current.screen) {
                    AppScreen.EmotionInput -> EmotionInputScreen(viewModel) { hasStarted = false }
                    AppScreen.Questionnaire -> QuestionnaireScreen(viewModel)
                    AppScreen.Loading -> LoadingScreen(viewModel)
                    AppScreen.Result -> ResultScreen(viewModel)
                }
            }
        }
    }
}
