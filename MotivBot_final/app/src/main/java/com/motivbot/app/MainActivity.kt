package com.motivbot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.motivbot.app.data.settings.ThemePreference
import com.motivbot.app.data.settings.ThemePreferenceStore
import com.motivbot.app.ui.MotivBotApp
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.components.LocalThemeController
import com.motivbot.app.ui.components.ThemeController
import com.motivbot.app.ui.export.ExportFiles
import com.motivbot.app.ui.theme.MotivBotTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: WellBeingViewModel by viewModels()

    @Inject lateinit var themeStore: ThemePreferenceStore

    /** Whether the screens are dark right now, so the system bar icons can be set again on demand. */
    private var darkBars = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Removing the splash restores the bar appearance it saved when it appeared, which can undo
        // the style set below if the first frame was drawn while the splash was still up (slow
        // devices). Set it again once the splash is gone.
        installSplashScreen().setOnExitAnimationListener { splash ->
            splash.remove()
            applySystemBars(darkBars)
        }
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // An export left behind when the app was closed with the share sheet open.
            ExportFiles.removeExports(this)
            // Wake the Space at launch so it is likely ready by the time the questionnaire is done.
            viewModel.prewarmService()
        }

        // Writes the debounced save right away when the app leaves the foreground.
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = viewModel.flushPendingSave()
        })

        setContent {
            val preference by themeStore.preference.collectAsState(initial = ThemePreference.System)
            val systemDark = isSystemInDarkTheme()
            val dark = when (preference) {
                ThemePreference.System -> systemDark
                ThemePreference.Light -> false
                ThemePreference.Dark -> true
            }
            val scope = rememberCoroutineScope()

            DisposableEffect(dark) {
                darkBars = dark
                applySystemBars(dark)
                onDispose {}
            }

            val controller = ThemeController(preference, dark) { next -> scope.launch { themeStore.set(next) } }
            CompositionLocalProvider(LocalThemeController provides controller) {
                MotivBotTheme(language = viewModel.localization.currentLanguage, darkTheme = dark) {
                    MotivBotApp(viewModel)
                }
            }
        }
    }

    /** Light icons on the dark theme, dark icons on the light one, with both bars see-through. */
    private fun applySystemBars(dark: Boolean) {
        val style = if (dark) {
            SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
        }
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}
