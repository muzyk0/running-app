package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationResult
import com.vladislav.runningapp.profile.AdditionalPromptField
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import java.io.IOException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RemoteTrainingGenerationRepositoryTest {
    @Test
    fun returnsGeneratedWorkoutPreviewOnSuccessfulResponse() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<GenerateTrainingResponseDto> = Response.success(sampleResponse())
            },
            gson = Gson(),
        )

        val result = repository.generateWorkout(profile = sampleUserProfile(), userNote = "  без спринтов  ")

        assertTrue(result is TrainingGenerationResult.Success)
        val success = result as TrainingGenerationResult.Success
        assertEquals(GeneratedWorkoutPreviewId, success.workout.id)
        assertEquals("Интервалы", success.workout.title)
        assertEquals(2, success.workout.steps.size)
        assertEquals(420, success.workout.estimatedDurationSec)
    }

    @Test
    fun mapsBackendValidationErrorsIntoDomainFailures() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<GenerateTrainingResponseDto> = Response.error(
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

        val result = repository.generateWorkout(profile = sampleUserProfile(), userNote = null)

        assertTrue(result is TrainingGenerationResult.Failure)
        val failure = result as TrainingGenerationResult.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidRequest, failure.error.code)
        assertEquals("profile.training_goal is required", failure.error.message)
    }

    @Test
    fun returnsInvalidResponseWhenBackendBodyIsMissing() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<GenerateTrainingResponseDto> = Response.success(null)
            },
            gson = Gson(),
        )

        val result = repository.generateWorkout(profile = sampleUserProfile(), userNote = null)

        assertTrue(result is TrainingGenerationResult.Failure)
        val failure = result as TrainingGenerationResult.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidResponse, failure.error.code)
    }

    @Test
    fun returnsInvalidResponseWhenBackendUsesUnsupportedStepType() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(
                    request: GenerateTrainingRequestDto,
                ): Response<GenerateTrainingResponseDto> = Response.success(
                    sampleResponse().copy(
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
                )
            },
            gson = Gson(),
        )

        val result = repository.generateWorkout(profile = sampleUserProfile(), userNote = null)

        assertTrue(result is TrainingGenerationResult.Failure)
        val failure = result as TrainingGenerationResult.Failure
        assertEquals(TrainingGenerationErrorCode.InvalidResponse, failure.error.code)
        assertTrue(failure.error.message.contains("Unsupported workout step type"))
    }

    @Test
    fun mapsNetworkIoExceptionsIntoNetworkFailures() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(request: GenerateTrainingRequestDto): Response<GenerateTrainingResponseDto> {
                    throw IOException("offline")
                }
            },
            gson = Gson(),
        )

        val result = repository.generateWorkout(profile = sampleUserProfile(), userNote = null)

        assertTrue(result is TrainingGenerationResult.Failure)
        val failure = result as TrainingGenerationResult.Failure
        assertEquals(TrainingGenerationErrorCode.Network, failure.error.code)
    }

    @Test(expected = CancellationException::class)
    fun rethrowsCancellationException() = runTest {
        val repository = DefaultTrainingGenerationRepository(
            apiService = object : TrainingGenerationApiService {
                override suspend fun generateTraining(request: GenerateTrainingRequestDto): Response<GenerateTrainingResponseDto> {
                    throw CancellationException("cancelled")
                }
            },
            gson = Gson(),
        )

        repository.generateWorkout(profile = sampleUserProfile(), userNote = null)
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
