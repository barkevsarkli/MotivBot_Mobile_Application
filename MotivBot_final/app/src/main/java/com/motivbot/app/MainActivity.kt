package com.motivbot.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.core.app.ShareCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.motivbot.app.ui.MotivBotApp
import com.motivbot.app.ui.MotivBotCallbacks
import com.motivbot.app.ui.WellBeingViewModel
import com.motivbot.app.ui.theme.MotivBotTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Port of `MindWellApp.swift`.
 *
 * `AppCompatActivity` (rather than `ComponentActivity`) is required for
 * `AppCompatDelegate.setApplicationLocales` to apply the per-app locale below API 33 — that is the
 * mechanism replacing `LocalizationManager`'s hand-loaded bundle and the `layoutDirection`
 * environment value, since Android flips RTL automatically for Persian.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must precede super.onCreate: swaps Theme.MotivBot.Splash out for Theme.MotivBot once the
        // first frame is ready, so the robot mark covers the cold start instead of a blank window.
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val viewModel: WellBeingViewModel = hiltViewModel()
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            // Swift: `.preferredColorScheme(viewModel.isDarkMode ? .dark : .light)`
            MotivBotTheme(darkTheme = state.isDarkMode) {
                MotivBotApp(
                    state = state,
                    callbacks = MotivBotCallbacks(
                        onEmotionTextChange = viewModel::onEmotionTextChange,
                        onSubmitEmotion = viewModel::submitEmotionAndProceed,
                        onSelectOption = viewModel::selectOption,
                        onSelectGridOption = viewModel::selectGridOption,
                        onScaleChange = viewModel::onScaleValueChange,
                        onFreeTextChange = viewModel::onFreeTextChange,
                        onNumberChange = viewModel::onNumberChange,
                        onNextQuestion = viewModel::nextQuestion,
                        onPreviousQuestion = viewModel::previousQuestion,
                        onRetry = viewModel::retrySubmission,
                        onReflectionTextChange = viewModel::onReflectionTextChange,
                        onSelectFeeling = viewModel::selectFeeling,
                        onSubmitReflection = viewModel::submitReflection,
                        onRestart = viewModel::restartAssessment,
                        onShareCsv = {
                            state.csvUri?.let { uri -> shareCsv(Uri.parse(uri)) }
                            viewModel.onShareOffered()
                        },
                        onToggleTheme = { viewModel.toggleTheme() },
                        onToggleLanguage = { viewModel.toggleLanguage() },
                    ),
                )
            }
        }
    }

    /**
     * Port of `MailView.swift`'s `ShareSheet` (`UIActivityViewController`).
     *
     * `ShareCompat` builds the chooser and, critically, grants the receiving app read permission
     * on the `content://` URI — the step that has no iOS equivalent and that silently breaks
     * sharing if omitted.
     */
    private fun shareCsv(uri: Uri) {
        val intent = ShareCompat.IntentBuilder(this)
            .setType("text/csv")
            .setStream(uri)
            .setChooserTitle(R.string.share_csv_title)
            .createChooserIntent()
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

        startActivity(intent)
    }
}
