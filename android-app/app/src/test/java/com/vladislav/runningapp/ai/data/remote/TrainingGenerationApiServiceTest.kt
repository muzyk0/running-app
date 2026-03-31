package com.vladislav.runningapp.ai.data.remote

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
    fun parsesSuccessfulResponseAndSerializesRequestBody() = runTest {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "schema_version": "mvp.v1",
                      "training": {
                        "title": "Интервалы",
                        "summary": "Легкий старт",
                        "goal": "Build consistency",
                        "estimated_duration_sec": 480,
                        "disclaimer": "Приложение не является медицинской рекомендацией.",
                        "steps": [
                          {
                            "id": "step-1",
                            "type": "warmup",
                            "duration_sec": 180,
                            "voice_prompt": "Разминка."
                          },
                          {
                            "id": "step-2",
                            "type": "run",
                            "duration_sec": 300,
                            "voice_prompt": "Легкий бег."
                          }
                        ]
                      }
                    }
                    """.trimIndent(),
                ),
        )

        val response = service.generateTraining(sampleGenerateTrainingRequestDto())

        assertTrue(response.isSuccessful)
        val body = requireNotNull(response.body())
        assertEquals("mvp.v1", body.schemaVersion)
        assertEquals("Интервалы", body.training.title)
        assertEquals(2, body.training.steps.size)
        assertEquals("run", body.training.steps[1].type)

        val request = server.takeRequest()
        val requestBody = request.body.readUtf8()
        assertEquals("POST", request.method)
        assertEquals("/v1/trainings/generate", request.path)
        assertTrue(requestBody.contains("\"locale\":\"ru-RU\""))
        assertTrue(requestBody.contains("\"training_goal\":\"Build consistency\""))
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
