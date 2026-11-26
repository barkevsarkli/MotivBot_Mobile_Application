package com.motivbot.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.StringProvider
import com.motivbot.app.data.remote.PredictionProgress
import com.motivbot.app.data.remote.PredictionService
import com.motivbot.app.data.session.SavedResult
import com.motivbot.app.data.session.SavedSession
import com.motivbot.app.data.session.SessionStore
import com.motivbot.app.data.session.restore
import com.motivbot.app.data.session.saved
import com.motivbot.app.domain.AnalysisError
import com.motivbot.app.domain.AnswerValue
import com.motivbot.app.domain.AppScreen
import com.motivbot.app.domain.CsvBuilder
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.PipelineLanguage
import com.motivbot.app.domain.PredictionRequest
import com.motivbot.app.domain.PredictionRequestBuilder
import com.motivbot.app.domain.PredictionResponse
import com.motivbot.app.domain.Question
import com.motivbot.app.domain.QuestionType
import com.motivbot.app.domain.ReflectionRating
import com.motivbot.app.domain.ReflectionTopic
import com.motivbot.app.domain.ResultSummary
import com.motivbot.app.domain.SectionDisplayMode
import com.motivbot.app.domain.TestData
import com.motivbot.app.domain.TestSection
import com.motivbot.app.domain.UserAnswer
import com.motivbot.app.domain.WellBeingDimension
import com.motivbot.app.domain.graphemeCount
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Writes and removes the shared CSV (SurveyAnswers.csv). */
interface CsvSink {
    /** Returns true when the file was written. */
    fun write(csv: String): Boolean
    fun delete()
}

/** Everything the view model needs from outside, so tests can swap each part. */
class WellBeingDependencies(
    val service: PredictionService,
    val store: SessionStore,
    val strings: StringProvider,
    val csvSink: CsvSink,
    val localization: LocalizationManager = LocalizationManager(),
    val saveDelayMillis: Long = 500,
    val now: () -> Long = System::currentTimeMillis,
)

/** A saved, unfinished assessment the UI can offer to continue. */
data class ResumableSession(
    val language: AppLanguage,
    val startedAtMillis: Long,
    val answeredQuestions: Int,
    val totalQuestions: Int,
)

sealed interface AnalysisPhase {
    data object Idle : AnalysisPhase
    data object Connecting : AnalysisPhase

    /** The Space is starting; this can take about a minute. */
    data object ModelWakingUp : AnalysisPhase
    data object Analysing : AnalysisPhase
    data class Failed(val error: AnalysisError) : AnalysisPhase

    val isInProgress: Boolean get() = this == Connecting || this == ModelWakingUp || this == Analysing
}

enum class EmotionTextStatus { TooShort, TooLong, WrongLanguage, Valid }

/**
 * Port of `WellBeingViewModel.swift`. State is Compose snapshot state, so screens read it directly,
 * the way SwiftUI views read the `@Observable` model on iOS.
 */
@HiltViewModel
class WellBeingViewModel @Inject constructor(dependencies: WellBeingDependencies) : ViewModel() {

    private val service = dependencies.service
    private val store = dependencies.store
    private val strings = dependencies.strings
    private val csvSink = dependencies.csvSink
    private val saveDelayMillis = dependencies.saveDelayMillis
    private val now = dependencies.now
    val localization = dependencies.localization

    val sections: List<TestSection> = TestData.sections

    var currentScreen by mutableStateOf(AppScreen.EmotionInput)
        private set

    private var emotionTextState by mutableStateOf("")

    /** Starting to type locks the language; clearing the text on the first screen unlocks it again. */
    var emotionText: String
        get() = emotionTextState
        set(value) {
            if (value == emotionTextState) return
            emotionTextState = value
            updateLanguageLock()
            scheduleSave()
        }

    // Questionnaire state
    var currentSectionIndex by mutableIntStateOf(0)
        private set
    var currentQuestionIndex by mutableIntStateOf(0)
        private set
    var selectedOptionId by mutableStateOf<Int?>(null)
    var scaleValue by mutableIntStateOf(5)
    var freeTextAnswer by mutableStateOf("")
    /** Settable from tests in this module, as on iOS; the screens only read it. */
    var answers by mutableStateOf<List<UserAnswer>>(emptyList())
        internal set

    /** questionId to selected option id, for all-at-once sections. */
    var gridAnswers by mutableStateOf<Map<Int, Int>>(emptyMap())

    // Analysis state
    var analysisPhase by mutableStateOf<AnalysisPhase>(AnalysisPhase.Idle)
        private set
    var prediction by mutableStateOf<PredictionResponse?>(null)
        private set

    /** The request that produced [prediction]. Not restored after relaunch. */
    var submittedRequest by mutableStateOf<PredictionRequest?>(null)
        private set
    private var resultDateMillis by mutableStateOf<Long?>(null)

    /** Shows the support card: PHQ-9 item 9 above "not at all", or a likely presence of depression in the text. */
    var elevatedRisk by mutableStateOf(false)
        private set

    // Save & resume
    var resumableSession by mutableStateOf<ResumableSession?>(null)
        private set

    // Reflection
    var reflectionText by mutableStateOf("")
    var reflectionRatings by mutableStateOf<Map<ReflectionTopic, ReflectionRating>>(emptyMap())
        private set
    var isCompleted by mutableStateOf(false)
        private set

    /** True while SurveyAnswers.csv holds the current result. */
    var hasCsv by mutableStateOf(false)
        private set

    private var pendingSession: Pair<SavedSession, List<UserAnswer>>? = null
    private var saveJob: Job? = null
    private var analysisJob: Job? = null
    private var sessionStartedAt: Long? = null

    init {
        restoreSavedState()
    }

    // MARK: - Derived state

    /** The result for PDF / image export, or null before a result exists. */
    val resultSummary: ResultSummary?
        get() {
            val prediction = prediction ?: return null
            val date = resultDateMillis ?: return null
            return ResultSummary.of(prediction, date, localization.currentLanguage, elevatedRisk)
        }

    /** The language can change only on the first screen, before the assessment starts. */
    val canChangeLanguage: Boolean
        get() = currentScreen == AppScreen.EmotionInput && !localization.isLocked && resumableSession == null

    val currentSection: TestSection? get() = sections.getOrNull(currentSectionIndex)
    val currentQuestion: Question? get() = currentSection?.questions?.getOrNull(currentQuestionIndex)

    val totalQuestions: Int get() = sections.sumOf { shownQuestionCount(it) }
    val answeredQuestions: Int get() = answers.size

    val overallProgress: Double
        get() = if (totalQuestions > 0) answeredQuestions.toDouble() / totalQuestions else 0.0

    /** Whether [question] is asked given the answers so far. A conditional question counts until its condition is answered. */
    fun isShown(question: Question): Boolean {
        val condition = question.showIf ?: return true
        val answer = answers.lastOrNull { it.questionId == condition.questionId }?.answerValue as? AnswerValue.Option
            ?: return true
        return answer.option.id == condition.optionId
    }

    fun shownQuestionCount(section: TestSection): Int = section.questions.count(::isShown)

    private fun firstShownQuestionIndex(section: TestSection, from: Int): Int? =
        section.questions.indices.firstOrNull { it >= from && isShown(section.questions[it]) }

    private fun lastShownQuestionIndex(section: TestSection, before: Int): Int =
        section.questions.indices.lastOrNull { it < before && isShown(section.questions[it]) } ?: 0

    val isLastQuestion: Boolean
        get() {
            val section = currentSection ?: return true
            val isLastInSection = firstShownQuestionIndex(section, currentQuestionIndex + 1) == null
            return isLastInSection && currentSectionIndex >= sections.size - 1
        }

    val emotionTextStatus: EmotionTextStatus
        get() {
            val text = emotionText.trim()
            val length = text.graphemeCount()
            return when {
                length < PredictionRequest.TEXT_LENGTH_RANGE.first -> EmotionTextStatus.TooShort
                length > PredictionRequest.TEXT_LENGTH_RANGE.last -> EmotionTextStatus.TooLong
                PipelineLanguage.detect(text) != PipelineLanguage.of(localization.currentLanguage) -> EmotionTextStatus.WrongLanguage
                else -> EmotionTextStatus.Valid
            }
        }

    val canProceedEmotion: Boolean get() = emotionTextStatus == EmotionTextStatus.Valid

    val canProceedQuestion: Boolean
        get() {
            val section = currentSection ?: return false
            if (section.displayMode == SectionDisplayMode.AllAtOnce) {
                return section.questions.all { gridAnswers[it.id] != null }
            }
            val question = currentQuestion ?: return false
            return when (val type = question.type) {
                QuestionType.SingleChoice -> selectedOptionId != null
                is QuestionType.Scale -> true
                QuestionType.FreeText -> question.isOptional || freeTextAnswer.isNotBlank()
                is QuestionType.IntegerInput -> integerAnswer(type.range) != null
            }
        }

    /** The typed number for an integer question, or null if it is not a whole number in [range]. */
    fun integerAnswer(range: IntRange): Int? {
        if (range == PredictionRequestBuilder.AGE_RANGE) return PredictionRequestBuilder.parseAge(freeTextAnswer)
        return freeTextAnswer.trim().toIntOrNull()?.takeIf { it in range }
    }

    // MARK: - Navigation

    fun submitEmotionAndProceed() {
        if (!canProceedEmotion) return
        localization.lock()
        currentScreen = AppScreen.Questionnaire
        scheduleSave()
        // The questionnaire takes minutes; wake the Space now.
        prewarmService()
    }

    /** Pings the Space in the background. Call at launch; safe to call repeatedly. */
    fun prewarmService() {
        viewModelScope.launch { service.prewarm() }
    }

    fun selectOption(optionId: Int) {
        selectedOptionId = optionId
    }

    fun selectGridOption(questionId: Int, optionId: Int) {
        gridAnswers = gridAnswers + (questionId to optionId)
    }

    fun nextQuestion() {
        val section = currentSection ?: return
        if (section.displayMode == SectionDisplayMode.AllAtOnce) {
            val recorded = section.questions.mapNotNull { question ->
                val option = question.options?.firstOrNull { it.id == gridAnswers[question.id] } ?: return@mapNotNull null
                UserAnswer(section.id, question.id, AnswerValue.Option(option))
            }
            answers = answers + recorded
            gridAnswers = emptyMap()
            advanceSectionOrFinish()
            scheduleSave()
            return
        }
        val question = currentQuestion ?: return
        val value: AnswerValue = when (val type = question.type) {
            QuestionType.SingleChoice -> {
                val option = question.options?.firstOrNull { it.id == selectedOptionId } ?: return
                AnswerValue.Option(option)
            }
            is QuestionType.Scale -> AnswerValue.Scale(scaleValue)
            QuestionType.FreeText -> AnswerValue.Text(freeTextAnswer.trim())
            is QuestionType.IntegerInput -> AnswerValue.Text((integerAnswer(type.range) ?: return).toString())
        }
        answers = answers + UserAnswer(section.id, question.id, value)

        selectedOptionId = null
        scaleValue = 5
        freeTextAnswer = ""

        val next = firstShownQuestionIndex(section, currentQuestionIndex + 1)
        if (next != null) {
            currentQuestionIndex = next
        } else {
            advanceSectionOrFinish()
        }
        scheduleSave()
    }

    private fun advanceSectionOrFinish() {
        if (currentSectionIndex < sections.size - 1) {
            currentSectionIndex += 1
            currentQuestionIndex = 0
        } else {
            // All questions answered: go to loading and send them.
            currentScreen = AppScreen.Loading
            submitForAnalysis()
        }
    }

    fun previousQuestion() {
        if (currentSectionIndex == 0 && currentQuestionIndex == 0) return
        val section = currentSection
        if (currentQuestionIndex > 0 || section?.displayMode == SectionDisplayMode.AllAtOnce) {
            if (section?.displayMode == SectionDisplayMode.AllAtOnce) {
                gridAnswers = emptyMap()
                if (currentSectionIndex > 0) {
                    currentSectionIndex -= 1
                    val previous = sections[currentSectionIndex]
                    if (previous.displayMode == SectionDisplayMode.AllAtOnce) {
                        answers = answers.filterNot { it.sectionId == previous.id }
                    } else {
                        currentQuestionIndex = lastShownQuestionIndex(previous, previous.questions.size)
                        answers = answers.dropLast(1)
                    }
                }
            } else if (section != null) {
                currentQuestionIndex = lastShownQuestionIndex(section, currentQuestionIndex)
                answers = answers.dropLast(1)
            }
        } else if (currentSectionIndex > 0) {
            currentSectionIndex -= 1
            val previous = sections[currentSectionIndex]
            if (previous.displayMode == SectionDisplayMode.AllAtOnce) {
                answers = answers.filterNot { it.sectionId == previous.id }
                currentQuestionIndex = 0
            } else {
                currentQuestionIndex = lastShownQuestionIndex(previous, previous.questions.size)
                answers = answers.dropLast(1)
            }
        }
        selectedOptionId = null
        scaleValue = 5
        freeTextAnswer = ""
        scheduleSave()
    }

    /** Goes back one question and puts the earlier answer back in place, as `goBack()` does on iOS. */
    fun goBackRestoringAnswer() {
        val earlier = answers
        previousQuestion()
        val section = currentSection ?: return
        if (section.displayMode == SectionDisplayMode.AllAtOnce) {
            val restored = section.questions.mapNotNull { question ->
                val option = (earlier.firstOrNull { it.questionId == question.id }?.answerValue as? AnswerValue.Option)?.option
                option?.let { question.id to it.id }
            }
            gridAnswers = gridAnswers + restored
            return
        }
        val question = currentQuestion ?: return
        when (val value = earlier.lastOrNull { it.sectionId == section.id && it.questionId == question.id }?.answerValue) {
            is AnswerValue.Option -> selectedOptionId = value.option.id
            is AnswerValue.Scale -> scaleValue = value.value
            is AnswerValue.Text -> freeTextAnswer = value.text
            null -> Unit
        }
    }

    // MARK: - CSV

    fun generateCsv(): String = CsvBuilder.build(
        sections = sections,
        answers = answers,
        prediction = prediction,
        request = submittedRequest,
        elevatedRisk = elevatedRisk,
        reflectionRatings = reflectionRatings,
        string = { strings.getString(localization.currentLanguage, it) },
    )

    private fun saveCsv() {
        hasCsv = csvSink.write(generateCsv())
    }

    // MARK: - Analysis

    /**
     * Sends the answers to the pipeline and shows the result screen. The service retries while the Space
     * wakes up. On failure the user stays on the loading screen with a retry option.
     */
    fun submitForAnalysis() {
        if (analysisPhase.isInProgress) return
        prediction = null
        submittedRequest = null
        val request = try {
            PredictionRequestBuilder.build(emotionText, answers).also { it.validate() }
        } catch (_: Exception) {
            analysisPhase = AnalysisPhase.Failed(AnalysisError.Unexpected)
            return
        }
        analysisPhase = AnalysisPhase.Connecting
        analysisJob = viewModelScope.launch {
            try {
                val response = service.predict(request) { progress -> apply(progress) }
                analysisJob = null
                showResult(response, request)
            } catch (cancellation: CancellationException) {
                analysisPhase = AnalysisPhase.Idle
                throw cancellation
            } catch (exception: MotivBotApiException) {
                analysisJob = null
                analysisPhase = AnalysisPhase.Failed(exception.error.analysisError)
            } catch (_: Exception) {
                analysisJob = null
                analysisPhase = AnalysisPhase.Failed(AnalysisError.Unexpected)
            }
        }
    }

    private fun apply(progress: PredictionProgress) {
        if (!analysisPhase.isInProgress) return
        analysisPhase = when (progress) {
            PredictionProgress.Connecting -> AnalysisPhase.Connecting
            PredictionProgress.WakingUp -> AnalysisPhase.ModelWakingUp
            PredictionProgress.Analysing -> AnalysisPhase.Analysing
        }
    }

    private fun showResult(response: PredictionResponse, request: PredictionRequest) {
        submittedRequest = request
        prediction = response
        val date = now()
        resultDateMillis = date
        elevatedRisk = isElevatedRisk(response, request)
        saveCsv()
        analysisPhase = AnalysisPhase.Idle
        saveJob?.cancel()
        saveJob = null
        store.deleteSession()
        store.saveResult(
            SavedResult(language = localization.currentLanguage, completedAt = date, prediction = response, elevatedRisk = elevatedRisk),
        )
        currentScreen = AppScreen.Result
    }

    // MARK: - Save & resume

    /** Writes the pending save now. Call when the app leaves the foreground. */
    fun flushPendingSave() {
        if (saveJob == null) return
        saveJob?.cancel()
        saveJob = null
        saveSessionNow()
    }

    /** Restores the saved session: language (locked), text, answers and position. */
    fun continueSession() {
        val (session, restored) = pendingSession ?: return
        pendingSession = null
        resumableSession = null
        sessionStartedAt = session.startedAt
        localization.lock(session.language)
        emotionText = session.emotionText
        answers = restored
        currentSectionIndex = session.sectionIndex
        currentQuestionIndex = session.questionIndex
        when (session.stage) {
            SavedSession.Stage.EmotionInput -> currentScreen = AppScreen.EmotionInput
            SavedSession.Stage.Questionnaire -> {
                currentScreen = AppScreen.Questionnaire
                prewarmService()
            }
            SavedSession.Stage.Analysis -> {
                currentScreen = AppScreen.Loading
                submitForAnalysis()
            }
        }
    }

    /** Discards the saved session and stays on the first screen. */
    fun startOver() {
        pendingSession = null
        resumableSession = null
        store.deleteSession()
        localization.unlock()
    }

    private fun restoreSavedState() {
        val session = store.loadSession()
        if (session != null) {
            val restored = session.answers.mapNotNull { it.restore(sections) }
            val positionIsValid = session.sectionIndex in sections.indices &&
                session.questionIndex in sections[session.sectionIndex].questions.indices
            if (restored.size == session.answers.size && positionIsValid) {
                pendingSession = session to restored
                resumableSession = ResumableSession(
                    language = session.language,
                    startedAtMillis = session.startedAt,
                    answeredQuestions = restored.size,
                    totalQuestions = totalQuestions,
                )
                return
            }
            store.deleteSession()
        }
        val result = store.loadResult() ?: return
        localization.lock(result.language)
        prediction = result.prediction
        resultDateMillis = result.completedAt
        elevatedRisk = result.elevatedRisk
        currentScreen = AppScreen.Result
    }

    private fun updateLanguageLock() {
        if (currentScreen != AppScreen.EmotionInput || answers.isNotEmpty() || pendingSession != null) return
        if (emotionText.isBlank()) localization.unlock() else localization.lock()
    }

    private fun scheduleSave() {
        if (pendingSession != null || prediction != null) return
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(saveDelayMillis)
            saveJob = null
            saveSessionNow()
        }
    }

    private fun saveSessionNow() {
        if (pendingSession != null || prediction != null) return
        if (emotionText.isBlank() && answers.isEmpty()) {
            sessionStartedAt = null
            store.deleteSession()
            return
        }
        val startedAt = sessionStartedAt ?: now()
        sessionStartedAt = startedAt
        val stage = when (currentScreen) {
            AppScreen.EmotionInput -> SavedSession.Stage.EmotionInput
            AppScreen.Questionnaire -> SavedSession.Stage.Questionnaire
            AppScreen.Loading, AppScreen.Result -> SavedSession.Stage.Analysis
        }
        store.saveSession(
            SavedSession(
                language = localization.currentLanguage,
                emotionText = emotionText,
                answers = answers.map { it.saved },
                stage = stage,
                sectionIndex = currentSectionIndex,
                questionIndex = currentQuestionIndex,
                startedAt = startedAt,
            ),
        )
    }

    // MARK: - Reflection

    /** Records how the user feels about one topic and rewrites the CSV so the rating is shared with the results. */
    fun rateReflection(topic: ReflectionTopic, rating: ReflectionRating) {
        reflectionRatings = reflectionRatings + (topic to rating)
        if (prediction != null) saveCsv()
    }

    /** Every topic must be rated; the note stays optional. */
    val canSubmitReflection: Boolean get() = ReflectionTopic.entries.all { reflectionRatings[it] != null }

    fun submitReflection() {
        if (!canSubmitReflection) return
        isCompleted = true
    }

    /** Cancels any analysis and deletes the saved session, the last result and the CSV. Unlocks the language. */
    fun restartAssessment() {
        analysisJob?.cancel()
        analysisJob = null
        saveJob?.cancel()
        saveJob = null
        pendingSession = null
        resumableSession = null
        sessionStartedAt = null
        store.deleteSession()
        store.deleteResult()
        csvSink.delete()

        currentScreen = AppScreen.EmotionInput
        emotionText = ""
        currentSectionIndex = 0
        currentQuestionIndex = 0
        selectedOptionId = null
        scaleValue = 5
        freeTextAnswer = ""
        answers = emptyList()
        gridAnswers = emptyMap()
        analysisPhase = AnalysisPhase.Idle
        prediction = null
        submittedRequest = null
        resultDateMillis = null
        elevatedRisk = false
        reflectionText = ""
        reflectionRatings = emptyMap()
        isCompleted = false
        hasCsv = false
        localization.unlock()
    }

    companion object {
        fun isElevatedRisk(prediction: PredictionResponse, request: PredictionRequest): Boolean {
            val depression = prediction.probability(WellBeingDimension.Depression) ?: 0.0
            return request.selfHarmItem >= 1 || depression >= PredictionResponse.PRESENT_THRESHOLD
        }
    }
}
