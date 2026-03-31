package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
import com.vladislav.runningapp.profile.AdditionalPromptField
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RemoteTrainingGenerationRepositoryTest {
    @Test
    fun emitsLogUpdatesAndCompletedWorkoutOnSuccessfulStream() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<ResponseBody> = Response.success(successfulStreamBody())
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = "  без спринтов  ").toList()

        assertEquals(3, updates.size)
        assertEquals("Building training prompt", (updates[0] as TrainingGenerationUpdate.Log).message)
        assertEquals("Waiting for provider output", (updates[1] as TrainingGenerationUpdate.Log).message)
        val completed = updates[2] as TrainingGenerationUpdate.Completed
        assertEquals(GeneratedWorkoutPreviewId, completed.workout.id)
        assertEquals("Интервалы", completed.workout.title)
        assertEquals(2, completed.workout.steps.size)
        assertEquals(420, completed.workout.estimatedDurationSec)
    }

    @Test
    fun mapsBackendValidationErrorsIntoDomainFailures() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<ResponseBody> = Response.error(
                    400,
                    """
                    {
                      "error": {
                        "code": "invalid_request",
                        "message": "profile.training_goal is required"
                      }
                    }
                    """.trimIndent().toResponseBody("application/json".toMediaType()),
                )
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()

        assertEquals(1, updates.size)
        val failure = updates.single() as TrainingGenerationUpdate.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidRequest, failure.error.code)
        assertEquals("profile.training_goal is required", failure.error.message)
    }

    @Test
    fun emitsTerminalStreamErrorAsDomainFailure() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<ResponseBody> = Response.success(errorStreamBody())
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()

        assertEquals(2, updates.size)
        assertEquals("Building training prompt", (updates[0] as TrainingGenerationUpdate.Log).message)
        val failure = updates[1] as TrainingGenerationUpdate.Failure
        assertEquals(TrainingGenerationErrorCode.Timeout, failure.error.code)
        assertEquals("generation timed out", failure.error.message)
    }

    @Test
    fun emitsInvalidResponseWhenStreamEndsWithoutTerminalEvent() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<ResponseBody> = Response.success(
                    """
                    event: log
                    data: {"message":"Building training prompt"}

                    """.trimIndent().toResponseBody("text/event-stream".toMediaType()),
                )
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()

        assertEquals(2, updates.size)
        assertEquals("Building training prompt", (updates[0] as TrainingGenerationUpdate.Log).message)
        val failure = updates[1] as TrainingGenerationUpdate.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidResponse, failure.error.code)
        assertEquals("Backend завершил поток без terminal event.", failure.error.message)
    }

    @Test
    fun emitsInvalidResponseWhenCompletedPayloadUsesUnsupportedStepType() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<ResponseBody> = Response.success(
                    successfulStreamBody(
                        response = sampleResponse().copy(
                            training = sampleResponse().training.copy(
                                steps = listOf(
                                    RemoteWorkoutStepDto(
                                        id = "step-1",
                                        type = "sprint",
                                        durationSec = 180,
                                        voicePrompt = "Ускорение.",
                                    ),
                                ),
                            ),
                        ),
                    ),
                )
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()

        assertEquals(3, updates.size)
        val failure = updates.last() as TrainingGenerationUpdate.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidResponse, failure.error.code)
        assertTrue(failure.error.message.contains("Unsupported workout step type"))
    }

    @Test
    fun mapsNetworkIoExceptionsIntoNetworkFailures() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(request: GenerateTrainingRequestDto): Response<ResponseBody> {
                    throw IOException("offline")
                }
            },
            gson = Gson(),
        )

        val updates = repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()

        assertEquals(1, updates.size)
        val failure = updates.single() as TrainingGenerationUpdate.Failure
        assertEquals(TrainingGenerationErrorCode.Network, failure.error.code)
    }

    @Test(expected = CancellationException::class)
    fun rethrowsCancellationException() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(request: GenerateTrainingRequestDto): Response<ResponseBody> {
                    throw CancellationException("cancelled")
                }
            },
            gson = Gson(),
        )

        repository.generateWorkout(profile = sampleUserProfile(), userNote = null).toList()
    }
}

private fun sampleUserProfile(): UserProfile = UserProfile(
    heightCm = 180,
    weightKg = 77,
    sex = UserSex.Male,
    age = 31,
    trainingDaysPerWeek = 4,
    fitnessLevel = FitnessLevel.Beginner,
    injuriesAndLimitations = "none",
    trainingGoal = "Build consistency",
    additionalPromptFields = listOf(
        AdditionalPromptField(
            label = "Поверхность",
            value = "Стадион",
        ),
    ),
)

private fun sampleResponse(): GenerateTrainingResponseDto = GenerateTrainingResponseDto(
    schemaVersion = "mvp.v1",
    training = RemoteWorkoutDto(
        title = "Интервалы",
        summary = "Чередование легкого бега и восстановления",
        goal = "Build consistency",
        estimatedDurationSec = 420,
        disclaimer = "Приложение не является медицинской рекомендацией.",
        steps = listOf(
            RemoteWorkoutStepDto(
                id = "step-1",
                type = "warmup",
                durationSec = 180,
                voicePrompt = "Разминка.",
            ),
            RemoteWorkoutStepDto(
                id = "step-2",
                type = "run",
                durationSec = 240,
                voicePrompt = "Бежим.",
            ),
        ),
    ),
)

private fun successfulStreamBody(
    response: GenerateTrainingResponseDto = sampleResponse(),
): ResponseBody = """
    event: log
    data: {"message":"Building training prompt"}

    event: log
    data: {"message":"Waiting for provider output"}

    event: completed
    data: ${Gson().toJson(response)}

    """.trimIndent().toResponseBody("text/event-stream".toMediaType())

private fun errorStreamBody(): ResponseBody = """
    event: log
    data: {"message":"Building training prompt"}

    event: error
    data: {"error":{"code":"request_timeout","message":"generation timed out"}}

    """.trimIndent().toResponseBody("text/event-stream".toMediaType())
