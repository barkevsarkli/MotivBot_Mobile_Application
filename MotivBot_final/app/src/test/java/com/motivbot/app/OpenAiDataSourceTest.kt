package com.motivbot.app

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.motivbot.app.core.AppError
import com.motivbot.app.core.AppLanguage
import com.motivbot.app.core.AppResult
import com.motivbot.app.data.remote.OpenAiApi
import com.motivbot.app.data.remote.OpenAiDataSource
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

/**
 * Exercises the network layer against a local MockWebServer — the Swift `OpenAIService` could not
 * be tested at all, since it built its own `URLSession` request inline.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class OpenAiDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenAiDataSource

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }

        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        val api = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenAiApi::class.java)

        dataSource = OpenAiDataSource(api, UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() = server.shutdown()

    private fun enqueueSuccess(content: String) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"choices":[{"message":{"role":"assistant","content":"$content"}}]}"""),
        )
    }

    @Test
    fun `system prompt tells the model to answer in Turkish`() = runTest {
        enqueueSuccess("Merhaba")

        dataSource.fetchMotivation("questionnaire", AppLanguage.Turkish)

        val body = server.takeRequest().body.readUtf8()
        assertTrue("expected a Turkish directive, got: $body", body.contains("Turkish"))
        assertTrue(body.contains("entire response"))
    }

    @Test
    fun `system prompt tells the model to answer in Persian and mentions the script`() = runTest {
        enqueueSuccess("سلام")

        dataSource.fetchMotivation("questionnaire", AppLanguage.Persian)

        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("Persian"))
        assertTrue("RTL script hint missing", body.contains("right-to-left"))
    }

    @Test
    fun `request carries the model and the user prompt`() = runTest {
        enqueueSuccess("ok")

        dataSource.fetchMotivation("MY-PROMPT-MARKER", AppLanguage.Turkish)

        val request = server.takeRequest()
        assertEquals("/v1/chat/completions", request.path)
        assertEquals("POST", request.method)

        val body = request.body.readUtf8()
        assertTrue(body.contains("gpt-4o-mini"))
        assertTrue(body.contains("max_tokens"))
        assertTrue(body.contains("MY-PROMPT-MARKER"))
    }

    @Test
    fun `trimmed content comes back on success`() = runTest {
        enqueueSuccess("  Hayat guzeldir.  ")

        val result = dataSource.fetchMotivation("p", AppLanguage.Turkish)

        assertEquals(AppResult.Success("Hayat guzeldir."), result)
    }

    @Test
    fun `non-2xx responses surface the status code`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":"bad key"}"""))

        val result = dataSource.fetchMotivation("p", AppLanguage.Turkish)

        val error = (result as AppResult.Failure).error as AppError.Api
        assertEquals(401, error.statusCode)
        assertTrue(error.body.contains("bad key"))
    }

    @Test
    fun `an empty choices array is reported as no content`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"choices":[]}"""))

        val result = dataSource.fetchMotivation("p", AppLanguage.Turkish)

        assertEquals(AppResult.Failure(AppError.NoContent), result)
    }

    @Test
    fun `a null content field is reported as no content rather than crashing`() = runTest {
        // A content-filtered completion returns `"content": null`; the Swift non-optional String
        // would have thrown a decoding error here.
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"choices":[{"message":{"role":"assistant","content":null}}]}"""),
        )

        val result = dataSource.fetchMotivation("p", AppLanguage.Turkish)

        assertEquals(AppResult.Failure(AppError.NoContent), result)
    }
}
