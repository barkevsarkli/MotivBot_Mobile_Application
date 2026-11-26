package com.motivbot.app

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.domain.AnalysisError
import com.motivbot.app.domain.AnswerValue
import com.motivbot.app.domain.AppScreen
import com.motivbot.app.domain.MotivBotApiError
import com.motivbot.app.domain.MotivBotApiException
import com.motivbot.app.domain.ReflectionRating
import com.motivbot.app.domain.ReflectionTopic
import com.motivbot.app.domain.TestData
import com.motivbot.app.ui.AnalysisPhase
import com.motivbot.app.ui.EmotionTextStatus
import com.motivbot.app.ui.WellBeingViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

// Port of WellBeingViewModelPredictionTests (PredictionTests.swift) and LanguageLockTests (IntegrationTests.swift).

class WellBeingViewModelTest {

    @get:Rule val main = MainDispatcherRule()

    private fun answeredViewModel(service: StubService, phq9: List<Int> = Fixture.phq9, csvSink: MemoryCsvSink = MemoryCsvSink()): WellBeingViewModel =
        makeViewModel(service = service, csvSink = csvSink).apply {
            emotionText = Fixture.TURKISH_TEXT
            answers = Fixture.answers(phq9 = phq9)
        }

    @Test
    fun showsResult() {
        val service = StubService.success(Fixture.response())
        val viewModel = answeredViewModel(service)

        viewModel.submitForAnalysis()

        assertEquals(1, service.sentRequests.size)
        assertEquals(AppScreen.Result, viewModel.currentScreen)
        assertEquals(AnalysisPhase.Idle, viewModel.analysisPhase)
        assertEquals(3, viewModel.prediction?.messages?.size)
        // Depression probability 0.62 is above the presence threshold.
        assertTrue(viewModel.elevatedRisk)
        val csv = viewModel.generateCsv()
        for (line in listOf(
            "PHQ-9 (0-27),10",
            "RSES (0-30),24",
            "RSES sent to model (10-40),34",
            "(0 = male; 0.5 = not specified; 1 = female),0.0",
            "Anxiety_Fear_Worry,0.880,High",
            "elevated_risk,true",
        )) {
            assertTrue(line, csv.contains(line))
        }
    }

    @Test
    fun selfHarmItemRaisesRiskEvenWithLowDepression() {
        val phq9 = List(9) { if (it == 8) 1 else 0 }
        val viewModel = answeredViewModel(StubService.success(Fixture.response(depression = 0.1)), phq9)
        viewModel.submitForAnalysis()
        assertTrue(viewModel.elevatedRisk)
    }

    @Test
    fun noRiskWithoutSignals() {
        val viewModel = answeredViewModel(StubService.success(Fixture.response(depression = 0.1)), List(9) { 0 })
        viewModel.submitForAnalysis()
        assertFalse(viewModel.elevatedRisk)
    }

    @Test
    fun reflectionRatingsAreRecordedInCsv() {
        val sink = MemoryCsvSink()
        val viewModel = answeredViewModel(StubService.success(Fixture.response()), csvSink = sink)
        viewModel.submitForAnalysis()
        assertTrue(viewModel.hasCsv)

        viewModel.rateReflection(ReflectionTopic.SelfEsteem, ReflectionRating.MuchBetter)
        viewModel.rateReflection(ReflectionTopic.HappinessAndHope, ReflectionRating.Same)
        viewModel.rateReflection(ReflectionTopic.Stress, ReflectionRating.MuchWorse)

        assertEquals(ReflectionRating.MuchBetter, viewModel.reflectionRatings[ReflectionTopic.SelfEsteem])
        assertNull(viewModel.reflectionRatings[ReflectionTopic.FearAndWorry])
        val csv = sink.csv!!
        for (part in listOf(
            "Message category,Relevance,Message\n",
            "Rating (1 = much worse; 2 = a bit worse; 3 = same; 4 = a bit better; 5 = much better)\n",
            "\nself_esteem,5\n",
            "\nfear_and_worry,\n",
            "\nhappiness_and_hope,3\n",
            "\nstress,1\n",
        )) {
            assertTrue(part, csv.contains(part))
        }

        viewModel.restartAssessment()
        assertTrue(viewModel.reflectionRatings.isEmpty())
        assertNull(sink.csv)
        assertFalse(viewModel.hasCsv)
    }

    @Test
    fun reflectionNeedsEveryTopicRated() {
        val viewModel = answeredViewModel(StubService.success(Fixture.response()))
        viewModel.submitForAnalysis()

        viewModel.reflectionText = "Biraz rahatladım"
        viewModel.rateReflection(ReflectionTopic.SelfEsteem, ReflectionRating.SlightlyBetter)
        viewModel.rateReflection(ReflectionTopic.FearAndWorry, ReflectionRating.SlightlyWorse)
        viewModel.rateReflection(ReflectionTopic.HappinessAndHope, ReflectionRating.Same)
        viewModel.submitReflection()
        assertFalse(viewModel.canSubmitReflection)
        assertFalse(viewModel.isCompleted)

        // The note stays optional.
        viewModel.reflectionText = ""
        viewModel.rateReflection(ReflectionTopic.Stress, ReflectionRating.MuchBetter)
        viewModel.submitReflection()
        assertTrue(viewModel.isCompleted)
    }

    @Test
    fun unreachableServerKeepsUserWaitingWithError() {
        val viewModel = answeredViewModel(StubService.failure(MotivBotApiError.Offline))
        val screen = viewModel.currentScreen

        viewModel.submitForAnalysis()

        assertEquals(AnalysisPhase.Failed(AnalysisError.Offline), viewModel.analysisPhase)
        assertNull(viewModel.prediction)
        assertEquals(screen, viewModel.currentScreen)
    }

    @Test
    fun mapsServiceErrorsToUserFacingErrors() {
        val cases = listOf(
            MotivBotApiError.SpaceStarting to AnalysisError.ServiceWakingUp,
            MotivBotApiError.QuotaExceeded to AnalysisError.ServiceBusy,
            MotivBotApiError.RateLimited to AnalysisError.ServiceBusy,
            MotivBotApiError.Network(MotivBotApiError.NetworkKind.TimedOut) to AnalysisError.Offline,
            MotivBotApiError.Decoding to AnalysisError.Unexpected,
            MotivBotApiError.InvalidInput("Value 5 is less than minimum value 10.") to AnalysisError.Unexpected,
        )
        for ((error, expected) in cases) {
            val viewModel = answeredViewModel(StubService.failure(error))
            viewModel.submitForAnalysis()
            assertEquals("$error", AnalysisPhase.Failed(expected), viewModel.analysisPhase)
        }
    }

    @Test
    fun retryAfterFailureShowsResult() {
        val service = StubService(
            listOf(Result.failure(MotivBotApiException(MotivBotApiError.Offline)), Result.success(Fixture.response())),
        )
        val viewModel = answeredViewModel(service)
        viewModel.submitForAnalysis()
        assertTrue(viewModel.analysisPhase is AnalysisPhase.Failed)

        viewModel.submitForAnalysis()
        assertEquals(AppScreen.Result, viewModel.currentScreen)
        assertEquals(2, service.sentRequests.size)
    }

    @Test
    fun ageMustBeValidToProceed() {
        val viewModel = makeViewModel()
        assertEquals(1, viewModel.currentQuestion?.id)
        viewModel.freeTextAnswer = "abc"
        assertFalse(viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "17"
        assertFalse(viewModel.canProceedQuestion)
        viewModel.freeTextAnswer = "۲۵"
        assertTrue(viewModel.canProceedQuestion)
        viewModel.nextQuestion()
        // The age is stored as normalised digits.
        assertEquals(AnswerValue.Text("25"), viewModel.answers.first().answerValue)
    }

    @Test
    fun demographicsAskOnlyAgeAndGender() {
        assertEquals(listOf(1, 2), TestData.sections[0].questions.map { it.id })
    }

    @Test
    fun genderLeadsStraightToPhq9() {
        val viewModel = makeViewModel()
        viewModel.freeTextAnswer = "30"
        viewModel.nextQuestion()
        viewModel.selectOption(2)
        viewModel.nextQuestion()
        assertEquals(1, viewModel.currentSectionIndex)
        assertEquals(7, viewModel.currentQuestion?.id)
        assertEquals(listOf(1, 2), viewModel.answers.map { it.questionId })
        assertEquals(2, viewModel.shownQuestionCount(viewModel.sections[0]))

        viewModel.previousQuestion()
        assertEquals(0, viewModel.currentSectionIndex)
        assertEquals(2, viewModel.currentQuestion?.id)
    }

    @Test
    fun goingBackPutsTheEarlierAnswerBack() {
        val viewModel = makeViewModel()
        viewModel.freeTextAnswer = "30"
        viewModel.nextQuestion()
        viewModel.selectOption(2)
        viewModel.nextQuestion()

        viewModel.goBackRestoringAnswer()
        assertEquals(2, viewModel.currentQuestion?.id)
        assertEquals(2, viewModel.selectedOptionId)
        assertEquals(listOf(1), viewModel.answers.map { it.questionId })

        viewModel.goBackRestoringAnswer()
        assertEquals(1, viewModel.currentQuestion?.id)
        assertEquals("30", viewModel.freeTextAnswer)
        assertTrue(viewModel.answers.isEmpty())
    }

    @Test
    fun answeringEveryQuestionStartsTheAnalysis() {
        val service = StubService.success(Fixture.response())
        val viewModel = makeViewModel(service = service)
        viewModel.emotionText = Fixture.TURKISH_TEXT
        viewModel.submitEmotionAndProceed()

        viewModel.freeTextAnswer = "25"
        viewModel.nextQuestion()
        while (viewModel.currentScreen == AppScreen.Questionnaire) {
            viewModel.selectOption(viewModel.currentQuestion!!.options!!.first().id)
            viewModel.nextQuestion()
        }

        assertEquals(AppScreen.Result, viewModel.currentScreen)
        assertEquals(viewModel.totalQuestions, viewModel.answers.size)
        assertEquals(1, service.sentRequests.size)
    }

    @Test
    fun reflectionScaleHasFiveSteps() {
        assertEquals(listOf(1, 2, 3, 4, 5), ReflectionRating.entries.map { it.rawValue })
    }

    // Language lock

    @Test
    fun typingLocksLanguageAndClearingUnlocksIt() {
        val viewModel = makeViewModel()
        assertTrue(viewModel.canChangeLanguage)

        viewModel.emotionText = "B"
        assertFalse(viewModel.canChangeLanguage)
        viewModel.localization.setLanguage(AppLanguage.Persian)
        viewModel.localization.toggleLanguage()
        assertEquals(AppLanguage.Turkish, viewModel.localization.currentLanguage)

        viewModel.emotionText = "  "
        assertTrue(viewModel.canChangeLanguage)
    }

    @Test
    fun languageStaysLockedThroughTheQuestionnaireUntilRestart() {
        val viewModel = makeViewModel()
        viewModel.emotionText = "Bugün kendimi çok yorgun hissediyorum."
        viewModel.submitEmotionAndProceed()
        assertEquals(AppScreen.Questionnaire, viewModel.currentScreen)
        assertTrue(viewModel.localization.isLocked)
        assertFalse(viewModel.canChangeLanguage)

        viewModel.restartAssessment()
        assertFalse(viewModel.localization.isLocked)
        assertTrue(viewModel.canChangeLanguage)
    }

    @Test
    fun refusesTextInAnotherLanguage() {
        val viewModel = makeViewModel()
        viewModel.emotionText = Fixture.PERSIAN_TEXT
        assertEquals(EmotionTextStatus.WrongLanguage, viewModel.emotionTextStatus)
        assertFalse(viewModel.canProceedEmotion)
        viewModel.submitEmotionAndProceed()
        assertEquals(AppScreen.EmotionInput, viewModel.currentScreen)

        viewModel.emotionText = ""
        viewModel.localization.setLanguage(AppLanguage.Persian)
        viewModel.emotionText = Fixture.PERSIAN_TEXT
        assertTrue(viewModel.canProceedEmotion)
        assertTrue(viewModel.localization.currentLanguage.isRtl)
    }

    @Test
    fun flagsTooLongAndTooShortText() {
        val viewModel = makeViewModel()
        viewModel.emotionText = "çok ".repeat(600)
        assertEquals(EmotionTextStatus.TooLong, viewModel.emotionTextStatus)
        viewModel.emotionText = "çok kötü"
        assertEquals(EmotionTextStatus.TooShort, viewModel.emotionTextStatus)
    }

    @Test
    fun submittingTheFeelingWakesTheSpace() {
        val service = StubService.success(Fixture.response())
        val viewModel = makeViewModel(service = service)
        viewModel.emotionText = Fixture.TURKISH_TEXT
        viewModel.submitEmotionAndProceed()
        assertEquals(1, service.prewarmCount)
    }
}
