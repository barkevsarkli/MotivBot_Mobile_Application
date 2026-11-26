package com.motivbot.app

import com.motivbot.app.core.AppLanguage
import com.motivbot.app.data.session.FileSessionStore
import com.motivbot.app.data.session.SavedAnswer
import com.motivbot.app.data.session.SavedSession
import com.motivbot.app.domain.AppScreen
import com.motivbot.app.domain.MotivBotApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

// Port of PersistenceTests (IntegrationTests.swift).

class PersistenceTest {

    @get:Rule val main = MainDispatcherRule()
    @get:Rule val folder = TemporaryFolder()

    private fun directory() = File(folder.root, "Assessment-${System.nanoTime()}")

    private fun session(startedAt: Long = System.currentTimeMillis()) = SavedSession(
        language = AppLanguage.Persian,
        emotionText = Fixture.PERSIAN_TEXT,
        answers = listOf(SavedAnswer(1, 1, SavedAnswer.Value.Text("30"))),
        stage = SavedSession.Stage.Questionnaire,
        sectionIndex = 0,
        questionIndex = 1,
        startedAt = startedAt,
    )

    @Test
    fun fileStoreRoundTrips() {
        val directory = directory()
        val store = FileSessionStore(directory)
        val saved = session()
        store.saveSession(saved)

        assertEquals(saved, store.loadSession())
        assertFalse("no temporary file is left behind", File(directory, "session.json.tmp").exists())

        store.deleteSession()
        assertNull(store.loadSession())
    }

    @Test
    fun expiredSessionIsDiscarded() {
        val directory = directory()
        val started = 1_700_000_000_000L
        val day = 86_400_000L
        FileSessionStore(directory).saveSession(session(startedAt = started))

        assertNotNull(FileSessionStore(directory, now = { started + 6 * day }).loadSession())
        assertNull(FileSessionStore(directory, now = { started + 8 * day }).loadSession())
        assertNull(FileSessionStore(directory, now = { started }).loadSession())
    }

    @Test
    fun unknownOrCorruptFileIsDiscarded() {
        for (contents in listOf("""{"version": 99, "language": "tr"}""", "not json")) {
            val directory = directory().apply { mkdirs() }
            val file = File(directory, "session.json")
            file.writeText(contents)

            assertNull(FileSessionStore(directory).loadSession())
            assertFalse(contents, file.exists())
        }
    }

    @Test
    fun resumeRestoresLanguageAnswersAndPosition() {
        val store = FileSessionStore(directory())
        val first = makeViewModel(store = store)
        first.localization.setLanguage(AppLanguage.Persian)
        first.emotionText = Fixture.PERSIAN_TEXT
        first.submitEmotionAndProceed()
        first.freeTextAnswer = "30"
        first.nextQuestion()
        first.selectOption(2)
        first.nextQuestion()
        first.flushPendingSave()

        // Relaunch with a fresh, Turkish localization.
        val relaunched = makeViewModel(store = store)
        assertEquals(AppLanguage.Persian, relaunched.resumableSession?.language)
        assertEquals(2, relaunched.resumableSession?.answeredQuestions)
        assertFalse(relaunched.canChangeLanguage)

        relaunched.continueSession()
        assertEquals(AppLanguage.Persian, relaunched.localization.currentLanguage)
        assertTrue(relaunched.localization.isLocked)
        assertEquals(AppScreen.Questionnaire, relaunched.currentScreen)
        assertEquals(Fixture.PERSIAN_TEXT, relaunched.emotionText)
        assertEquals(2, relaunched.answers.size)
        // Demographics end after gender, so the session resumes at the first PHQ-9 question.
        assertEquals(1, relaunched.currentSectionIndex)
        assertEquals(7, relaunched.currentQuestion?.id)
        assertNull(relaunched.resumableSession)
    }

    @Test
    fun startOverDiscardsSavedSession() {
        val store = InMemorySessionStore()
        store.session = session()
        val viewModel = makeViewModel(store = store)
        assertNotNull(viewModel.resumableSession)

        viewModel.startOver()
        assertNull(store.session)
        assertNull(viewModel.resumableSession)
        assertTrue(viewModel.canChangeLanguage)
    }

    @Test
    fun sessionReferringToUnknownOptionIsDiscarded() {
        val store = InMemorySessionStore()
        store.session = session().copy(answers = listOf(SavedAnswer(1, 2, SavedAnswer.Value.Option(99))))

        val viewModel = makeViewModel(store = store)
        assertNull(viewModel.resumableSession)
        assertNull(store.session)
    }

    /** A session saved before the diagnosis question (id 5) was removed cannot be resumed. */
    @Test
    fun sessionWithRemovedDiagnosisAnswerIsDiscarded() {
        val store = InMemorySessionStore()
        store.session = session().copy(
            answers = listOf(
                SavedAnswer(1, 1, SavedAnswer.Value.Text("30")),
                SavedAnswer(1, 2, SavedAnswer.Value.Option(2)),
                SavedAnswer(1, 5, SavedAnswer.Value.Option(2)),
            ),
            questionIndex = 3,
        )

        val viewModel = makeViewModel(store = store)
        assertNull(viewModel.resumableSession)
        assertNull(store.session)
    }

    @Test
    fun resultReplacesSessionAndSurvivesRelaunchUntilRestart() {
        val store = InMemorySessionStore()
        val viewModel = makeViewModel(service = StubService.success(Fixture.response()), store = store)
        viewModel.emotionText = Fixture.TURKISH_TEXT
        viewModel.answers = Fixture.answers()
        viewModel.flushPendingSave()
        assertNotNull(store.session)

        viewModel.submitForAnalysis()
        assertNull(store.session)
        assertEquals(AppLanguage.Turkish, store.result?.language)
        assertEquals(true, store.result?.elevatedRisk)

        val relaunched = makeViewModel(store = store)
        assertEquals(AppScreen.Result, relaunched.currentScreen)
        assertTrue(relaunched.localization.isLocked)
        val summary = relaunched.resultSummary!!
        assertEquals(5, summary.dimensions.size)
        assertEquals(3, summary.messages.size)
        assertTrue(summary.elevatedRisk)
        assertEquals(AppLanguage.Turkish, summary.language)

        relaunched.restartAssessment()
        assertNull(store.result)
        assertEquals(AppScreen.EmotionInput, relaunched.currentScreen)
        assertFalse(relaunched.localization.isLocked)
    }

    @Test
    fun failedAnalysisKeepsTheSessionForLater() {
        val store = InMemorySessionStore()
        val viewModel = makeViewModel(service = StubService.failure(MotivBotApiError.Offline), store = store)
        viewModel.emotionText = Fixture.TURKISH_TEXT
        viewModel.answers = Fixture.answers()
        viewModel.flushPendingSave()

        viewModel.submitForAnalysis()
        assertNotNull(store.session)
        assertNull(store.result)
    }
}
