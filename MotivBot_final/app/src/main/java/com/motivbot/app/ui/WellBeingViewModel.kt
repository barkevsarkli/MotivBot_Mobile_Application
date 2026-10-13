package com.motivbot.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motivbot.app.R
import com.motivbot.app.core.AppError
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.AppResult
import com.motivbot.app.core.StringProvider
import com.motivbot.app.data.repository.MotivationRepository
import com.motivbot.app.data.repository.SessionRepository
import com.motivbot.app.data.repository.SettingsRepository
import com.motivbot.app.domain.model.AnswerValue
import com.motivbot.app.domain.model.AppScreen
import com.motivbot.app.domain.model.QuestionType
import com.motivbot.app.domain.model.SectionDisplayMode
import com.motivbot.app.domain.model.UserAnswer
import com.motivbot.app.domain.usecase.AnswerRowMapper
import com.motivbot.app.domain.usecase.BuildPromptUseCase
import com.motivbot.app.domain.usecase.CsvExporter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Port of `WellBeingViewModel.swift`.
 *
 * Structural changes from the Swift original:
 *  - `@Observable` + loose `var`s → a single `StateFlow<WellBeingUiState>` updated with `update {}`,
 *    which is atomic and therefore safe even though several coroutines write to it;
 *  - `@MainActor` → nothing needed: `viewModelScope` is main-dispatched, and every suspending
 *    dependency is main-safe on its own (Room and OkHttp both move off the main thread);
 *  - `withAnimation { … }` calls are gone. Animation is a rendering concern; the Compose screens
 *    express it with `AnimatedContent`/`animateFloatAsState` keyed on the same state values;
 *  - the API call and the CSV write are no longer interleaved in one method — the questionnaire is
 *    persisted first (so the data survives even if the network call dies), then the AI is asked.
 */
@HiltViewModel
class WellBeingViewModel @Inject constructor(
    private val motivationRepository: MotivationRepository,
    private val sessionRepository: SessionRepository,
    private val settingsRepository: SettingsRepository,
    private val buildPrompt: BuildPromptUseCase,
    private val answerRowMapper: AnswerRowMapper,
    private val csvExporter: CsvExporter,
    private val stringProvider: StringProvider,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WellBeingUiState())
    val uiState: StateFlow<WellBeingUiState> = _uiState.asStateFlow()

    /** Row id of the session currently being filled in; null before the questionnaire finishes. */
    private var activeSessionId: Long? = null

    /** Held so a retry can cancel an in-flight attempt instead of racing it. */
    private var submissionJob: Job? = null

    init {
        // Theme and language are owned by the settings store, not by this ViewModel; mirror them
        // into the UI state so the screens still read everything from one place.
        viewModelScope.launch {
            combine(
                settingsRepository.isDarkMode,
                settingsRepository.language,
                ::Pair,
            ).collect { (isDarkMode, language) ->
                // Keep the process locale in step with the stored preference, so the toggle badge
                // and the strings on screen can never disagree.
                settingsRepository.ensureLocaleApplied(language)
                _uiState.update { it.copy(isDarkMode = isDarkMode, language = language) }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Screen 1 — emotion input
    // ─────────────────────────────────────────────────────────────────────────────

    fun onEmotionTextChange(text: String) = _uiState.update { it.copy(emotionText = text) }

    /** Swift: `submitEmotionAndProceed()`. */
    fun submitEmotionAndProceed() {
        if (!_uiState.value.canProceedEmotion) return
        goToScreen(AppScreen.Questionnaire)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Screen 2 — questionnaire
    // ─────────────────────────────────────────────────────────────────────────────

    /** Swift: `selectOption(_:)`. */
    fun selectOption(optionId: Int) = _uiState.update { it.copy(selectedOptionId = optionId) }

    /** Swift: `selectGridOption(questionId:optionId:)`. */
    fun selectGridOption(questionId: Int, optionId: Int) = _uiState.update {
        it.copy(gridAnswers = it.gridAnswers + (questionId to optionId))
    }

    fun onScaleValueChange(value: Int) = _uiState.update { it.copy(scaleValue = value) }

    fun onFreeTextChange(text: String) = _uiState.update { it.copy(freeTextAnswer = text) }

    /**
     * Accepts digits only, capped at three of them.
     *
     * Filtering at the source means the state can never hold a sign, a decimal point, letters, or a
     * 10 000-digit paste — so `toIntOrNull()` downstream cannot overflow and the field cannot be
     * used as an injection vector into the AI prompt.
     */
    fun onNumberChange(text: String) = _uiState.update {
        it.copy(numberAnswer = text.filter(Char::isDigit).take(MAX_NUMBER_DIGITS))
    }

    /**
     * Swift: `nextQuestion()`.
     *
     * Records the current answer(s), then advances — question, then section, then out of the
     * questionnaire entirely. Behaviour is identical to the Swift version, including the fact that
     * an `AllAtOnce` section commits all of its answers in one go.
     */
    fun nextQuestion() {
        val state = _uiState.value
        val section = state.currentSection ?: return
        if (!state.canProceedQuestion) return

        if (section.displayMode == SectionDisplayMode.AllAtOnce) {
            val gridAnswers = section.questions.mapNotNull { question ->
                val optionId = state.gridAnswers[question.id] ?: return@mapNotNull null
                val option = question.options?.firstOrNull { it.id == optionId }
                    ?: return@mapNotNull null
                UserAnswer(section.id, question.id, AnswerValue.Option(option))
            }

            _uiState.update { it.copy(answers = it.answers + gridAnswers, gridAnswers = emptyMap()) }
            advanceSection()
            return
        }

        val question = state.currentQuestion ?: return

        val answerValue: AnswerValue = when (question.type) {
            is QuestionType.SingleChoice -> {
                val option = question.options
                    ?.firstOrNull { it.id == state.selectedOptionId }
                    ?: return // Swift bailed out the same way rather than recording a partial answer.
                AnswerValue.Option(option)
            }

            is QuestionType.Scale -> AnswerValue.Scale(state.scaleValue)

            is QuestionType.FreeText -> AnswerValue.Text(state.freeTextAnswer.trim())

            // `canProceedQuestion` already gated this, so the value is in range; bail rather than
            // record a bogus answer if that ever stops being true.
            is QuestionType.Number ->
                AnswerValue.Number(state.numberAnswerValue ?: return)
        }

        _uiState.update {
            it.copy(
                answers = it.answers + UserAnswer(section.id, question.id, answerValue),
                // Reset the per-question inputs, exactly as the Swift version did.
                selectedOptionId = null,
                scaleValue = WellBeingUiState.DEFAULT_SCALE_VALUE,
                freeTextAnswer = "",
                numberAnswer = "",
            )
        }

        if (state.currentQuestionIndex < section.questions.lastIndex) {
            _uiState.update { it.copy(currentQuestionIndex = it.currentQuestionIndex + 1) }
        } else {
            advanceSection()
        }
    }

    /** Moves to the next section, or finishes the assessment if this was the last one. */
    private fun advanceSection() {
        val state = _uiState.value
        if (state.currentSectionIndex < state.sections.lastIndex) {
            _uiState.update {
                it.copy(currentSectionIndex = it.currentSectionIndex + 1, currentQuestionIndex = 0)
            }
        } else {
            goToScreen(AppScreen.Loading)
            finishAssessment()
        }
    }

    /**
     * Swift: `previousQuestion()`.
     *
     * The rewind rules are subtle and are reproduced as-is: stepping back inside a sequential
     * section drops the last recorded answer, while stepping back into (or out of) an `AllAtOnce`
     * section clears that whole section's answers so the grid is re-answered from scratch.
     */
    fun previousQuestion() {
        val state = _uiState.value
        if (state.currentSectionIndex == 0 && state.currentQuestionIndex == 0) return

        val isGridSection = state.currentSection?.displayMode == SectionDisplayMode.AllAtOnce

        when {
            // Leaving a grid section backwards: discard the (uncommitted) grid selections.
            isGridSection -> {
                if (state.currentSectionIndex > 0) {
                    val previousIndex = state.currentSectionIndex - 1
                    val previousSection = state.sections[previousIndex]

                    _uiState.update { current ->
                        if (previousSection.displayMode == SectionDisplayMode.AllAtOnce) {
                            current.copy(
                                gridAnswers = emptyMap(),
                                currentSectionIndex = previousIndex,
                                answers = current.answers.filterNot { it.sectionId == previousSection.id },
                            )
                        } else {
                            current.copy(
                                gridAnswers = emptyMap(),
                                currentSectionIndex = previousIndex,
                                currentQuestionIndex = previousSection.questions.lastIndex,
                                answers = current.answers.dropLast(1),
                            )
                        }
                    }
                } else {
                    _uiState.update { it.copy(gridAnswers = emptyMap()) }
                }
            }

            // Ordinary step back inside a sequential section.
            state.currentQuestionIndex > 0 -> _uiState.update {
                it.copy(
                    answers = it.answers.dropLast(1),
                    currentQuestionIndex = it.currentQuestionIndex - 1,
                )
            }

            // First question of a sequential section: step into the previous section.
            state.currentSectionIndex > 0 -> {
                val previousIndex = state.currentSectionIndex - 1
                val previousSection = state.sections[previousIndex]

                _uiState.update { current ->
                    if (previousSection.displayMode == SectionDisplayMode.AllAtOnce) {
                        current.copy(
                            currentSectionIndex = previousIndex,
                            currentQuestionIndex = 0,
                            answers = current.answers.filterNot { it.sectionId == previousSection.id },
                        )
                    } else {
                        current.copy(
                            currentSectionIndex = previousIndex,
                            currentQuestionIndex = previousSection.questions.lastIndex,
                            answers = current.answers.dropLast(1),
                        )
                    }
                }
            }
        }

        _uiState.update {
            it.copy(
                selectedOptionId = null,
                scaleValue = WellBeingUiState.DEFAULT_SCALE_VALUE,
                freeTextAnswer = "",
                numberAnswer = "",
            )
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Screen 3 — persistence + AI submission
    // ─────────────────────────────────────────────────────────────────────────────

    /**
     * Swift: `submitToAPI()`.
     *
     * Persist first, ask the AI second. The Swift version did the reverse-ish — it wrote a
     * throwaway CSV with `try?` and dropped the data on the floor if the write failed — so a
     * crash or a backgrounded app during the network call lost the entire assessment.
     */
    fun finishAssessment() {
        submissionJob?.cancel()
        submissionJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val state = _uiState.value
            val rows = answerRowMapper(state.answers, state.language)

            val sessionId = activeSessionId ?: runCatching {
                sessionRepository.startSession(
                    emotionText = state.emotionText,
                    language = state.language,
                    rows = rows,
                )
            }.getOrNull()
            activeSessionId = sessionId

            // Export the CSV for sharing (iOS: temp file handed to UIActivityViewController).
            csvExporter.writeCsv(rows).onSuccess { uri ->
                _uiState.update { it.copy(csvUri = uri.toString()) }
            }

            val prompt = buildPrompt(state.emotionText, state.answers, state.language)

            when (val result = motivationRepository.generateMotivation(prompt, state.language)) {
                is AppResult.Success -> {
                    sessionId?.let {
                        sessionRepository.saveAiResponse(it, result.data, usedFallback = false)
                    }
                    _uiState.update {
                        it.copy(isLoading = false, aiResponse = result.data, errorMessage = null)
                    }
                }

                is AppResult.Failure -> {
                    // Swift substituted a hard-coded English quote here, which meant a Turkish or
                    // Persian user saw English on failure even though `api_error_fallback` was
                    // already translated. Use the localized resource instead.
                    val fallback = stringProvider.getString(state.language, R.string.api_error_fallback)
                    sessionId?.let {
                        sessionRepository.saveAiResponse(it, fallback, usedFallback = true)
                    }
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            aiResponse = fallback,
                            errorMessage = result.error.toUiMessage(),
                        )
                    }
                }
            }

            goToScreen(AppScreen.Result)
            _uiState.update { it.copy(shouldOfferShare = it.csvUri != null) }
        }
    }

    /** Swift: the `retry_button` in `LoadingView` calling `submitToAPI()` again. */
    fun retrySubmission() {
        goToScreen(AppScreen.Loading)
        finishAssessment()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Screen 4 — result and reflection
    // ─────────────────────────────────────────────────────────────────────────────

    fun onReflectionTextChange(text: String) = _uiState.update { it.copy(reflectionText = text) }

    fun selectFeeling(index: Int) = _uiState.update { it.copy(selectedFeelingIndex = index) }

    /** Swift: `submitReflection()` — which only flipped a flag; the reflection was never stored. */
    fun submitReflection() {
        val state = _uiState.value
        viewModelScope.launch {
            activeSessionId?.let { sessionId ->
                sessionRepository.completeSession(
                    sessionId = sessionId,
                    reflection = state.reflectionText,
                    feelingIndex = state.selectedFeelingIndex,
                )
            }
            _uiState.update { it.copy(isCompleted = true) }
        }
    }

    fun onShareOffered() = _uiState.update { it.copy(shouldOfferShare = false) }

    fun dismissError() = _uiState.update { it.copy(errorMessage = null) }

    /**
     * Swift: `restartAssessment()`.
     *
     * Resets the run but keeps the user's theme and language — the Swift version reset only the
     * assessment fields too, and `isDarkMode` / the localization manager sat outside that block.
     */
    fun restartAssessment() {
        submissionJob?.cancel()
        activeSessionId = null
        _uiState.update { current ->
            WellBeingUiState(isDarkMode = current.isDarkMode, language = current.language)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Settings
    // ─────────────────────────────────────────────────────────────────────────────

    /** Swift: `toggleTheme()`. */
    fun toggleTheme() = viewModelScope.launch {
        settingsRepository.toggleTheme(_uiState.value.isDarkMode)
    }

    /** Swift: `LocalizationManager.toggleLanguage()`. */
    fun toggleLanguage() = viewModelScope.launch {
        settingsRepository.toggleLanguage(_uiState.value.language)
    }

    // ─────────────────────────────────────────────────────────────────────────────

    private companion object {
        /** Three digits is enough for any age and blocks overflow-sized pastes. */
        const val MAX_NUMBER_DIGITS = 3
    }

    private fun goToScreen(screen: AppScreen) = _uiState.update { it.copy(currentScreen = screen) }

    private fun AppError.toUiMessage(): UiMessage = when (this) {
        is AppError.Network -> UiMessage(R.string.error_network)
        is AppError.Api -> UiMessage(R.string.error_api, listOf(statusCode, body.take(200)))
        AppError.NoContent -> UiMessage(R.string.error_no_content)
        is AppError.Unknown -> UiMessage(R.string.error_unknown)
    }
}
