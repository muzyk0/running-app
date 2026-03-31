package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationResult
import com.vladislav.runningapp.profile.AdditionalPromptField
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class RemoteTrainingGenerationRepositoryTest {
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
