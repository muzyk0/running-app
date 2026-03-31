package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class TrainingGenerationApiServiceTest {
    private lateinit var server: MockWebServer
    private lateinit var service: TrainingGenerationApiService
    private val gson = Gson()

    @Before
    fun setUp() {
        server = MockWebServer()
        service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(OkHttpClient())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(TrainingGenerationApiService::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesChunkedLogAndCompletedEventsAndSerializesRequestBody() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(
                    """
                    event: log
                    data: {"message":"Building training prompt"}

                    event: log
                    data: {"message":"Waiting for provider output"}

                    event: completed
                    data: {"schema_version":"mvp.v1","training":{"title":"Интервалы","summary":"Легкий старт","goal":"Build consistency","estimated_duration_sec":480,"disclaimer":"Приложение не является медицинской рекомендацией.","steps":[{"id":"step-1","type":"warmup","duration_sec":180,"voice_prompt":"Разминка."},{"id":"step-2","type":"run","duration_sec":300,"voice_prompt":"Легкий бег."}]}}

                    """.trimIndent(),
                    11,
                ),
        )

        val response = service.generateTraining(sampleGenerateTrainingRequestDto())

        assertTrue(response.isSuccessful)
        val events = requireNotNull(response.body()).use { body ->
            body.toTrainingGenerationEvents(gson).toList()
        }

        assertEquals(3, events.size)
        assertEquals(
            "Building training prompt",
            (events[0] as RemoteTrainingGenerationStreamEventDto.Log).payload.message,
        )
        assertEquals(
            "Waiting for provider output",
            (events[1] as RemoteTrainingGenerationStreamEventDto.Log).payload.message,
        )
        val completed = events[2] as RemoteTrainingGenerationStreamEventDto.Completed
        assertEquals("mvp.v1", completed.payload.schemaVersion)
        assertEquals("Интервалы", completed.payload.training.title)
        assertEquals(2, completed.payload.training.steps.size)
        assertEquals("run", completed.payload.training.steps[1].type)

        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/v1/trainings/generate", request.path)
        assertEquals("text/event-stream", request.getHeader("Accept"))
        assertTrue(requestBody.contains("\"locale\":\"ru-RU\""))
        assertTrue(requestBody.contains("\"training_goal\":\"Build consistency\""))
    }

    @Test
    fun parsesChunkedTerminalErrorEvent() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "text/event-stream")
                .setChunkedBody(
                    """
                    event: log
                    data: {"message":"Building training prompt"}

                    event: error
                    data: {"error":{"code":"provider_error","message":"training generation failed"}}

                    """.trimIndent(),
                    9,
                ),
        )

        val response = service.generateTraining(sampleGenerateTrainingRequestDto())

        assertTrue(response.isSuccessful)
        val events = requireNotNull(response.body()).use { body ->
            body.toTrainingGenerationEvents(gson).toList()
        }

        assertEquals(2, events.size)
        assertEquals(
            "Building training prompt",
            (events[0] as RemoteTrainingGenerationStreamEventDto.Log).payload.message,
        )
        val error = events[1] as RemoteTrainingGenerationStreamEventDto.Error
        assertEquals("provider_error", error.payload.error?.code)
        assertEquals("training generation failed", error.payload.error?.message)
    }
}

private fun sampleGenerateTrainingRequestDto(): GenerateTrainingRequestDto = GenerateTrainingRequestDto(
    profile = GenerateTrainingProfileDto(
        heightCm = 180,
        weightKg = 77,
        sex = "male",
        age = 31,
        trainingDaysPerWeek = 4,
        fitnessLevel = "beginner",
        injuriesAndLimitations = "none",
        trainingGoal = "Build consistency",
        additionalPromptFields = listOf(
            GenerateTrainingPromptFieldDto(
                label = "Поверхность",
                value = "Стадион",
            ),
        ),
    ),
    request = GenerateTrainingRequestContextDto(
        locale = "ru-RU",
        userNote = "Без ускорений",
    ),
)
