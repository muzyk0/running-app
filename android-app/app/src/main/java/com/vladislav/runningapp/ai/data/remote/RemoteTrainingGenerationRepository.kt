package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationRepository
import com.vladislav.runningapp.ai.domain.TrainingGenerationResult
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.training.domain.toDomainWorkout
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import retrofit2.Response

internal const val GeneratedWorkoutPreviewId = "generated-preview"

@Singleton
class DefaultTrainingGenerationRepository @Inject constructor(
    private val apiService: TrainingGenerationApiService,
    private val gson: Gson,
) : TrainingGenerationRepository {
    override suspend fun generateWorkout(
        profile: UserProfile,
        userNote: String?,
    ): TrainingGenerationResult {
        return try {
            val response = apiService.generateTraining(profile.toGenerateTrainingRequestDto(userNote))
            if (!response.isSuccessful) {
                TrainingGenerationResult.Failure(response.toTrainingGenerationError(gson))
            } else {
                val body = response.body()
                if (body == null) {
                    TrainingGenerationResult.Failure(
                        TrainingGenerationError(
                            code = TrainingGenerationErrorCode.InvalidResponse,
                            message = "Backend вернул пустой ответ вместо тренировки.",
                        ),
                    )
                } else {
                    val workout = body.toWorkoutEnvelopeDto().toDomainWorkout(workoutId = GeneratedWorkoutPreviewId)
                    if (workout.steps.isEmpty()) {
                        TrainingGenerationResult.Failure(
                            TrainingGenerationError(
                                code = TrainingGenerationErrorCode.InvalidResponse,
                                message = "Backend вернул тренировку без шагов.",
                            ),
                        )
                    } else {
                        TrainingGenerationResult.Success(workout = workout)
                    }
                }
            }
        } catch (_: IOException) {
            TrainingGenerationResult.Failure(
                TrainingGenerationError(
                    code = TrainingGenerationErrorCode.Network,
                    message = "Не удалось связаться с backend. Проверьте адрес сервиса и соединение.",
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            TrainingGenerationResult.Failure(
                TrainingGenerationError(
                    code = TrainingGenerationErrorCode.InvalidResponse,
                    message = error.message?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: "Backend вернул ответ в неподдерживаемом формате.",
                ),
            )
        } catch (error: Exception) {
            TrainingGenerationResult.Failure(
                TrainingGenerationError(
                    code = TrainingGenerationErrorCode.Unknown,
                    message = error.message?.trim().takeUnless { it.isNullOrEmpty() }
                        ?: "Не удалось обработать ответ backend.",
                ),
            )
        }
    }
}

private fun Response<*>.toTrainingGenerationError(
    gson: Gson,
): TrainingGenerationError {
    val apiError = errorBody()?.charStream()?.use { reader ->
        runCatching { gson.fromJson(reader, ApiErrorEnvelopeDto::class.java) }.getOrNull()?.error
    }
    val code = when (apiError?.code?.trim()) {
        "invalid_request" -> TrainingGenerationErrorCode.InvalidRequest
        "invalid_json" -> TrainingGenerationErrorCode.InvalidResponse
        "service_unavailable" -> TrainingGenerationErrorCode.ServiceUnavailable
        "provider_error" -> TrainingGenerationErrorCode.ProviderError
        "request_timeout" -> TrainingGenerationErrorCode.Timeout
        else -> when (code()) {
            400 -> TrainingGenerationErrorCode.InvalidRequest
            408, 504 -> TrainingGenerationErrorCode.Timeout
            502 -> TrainingGenerationErrorCode.ProviderError
            503 -> TrainingGenerationErrorCode.ServiceUnavailable
            else -> TrainingGenerationErrorCode.Unknown
        }
    }

    val message = apiError?.message?.trim().takeUnless { it.isNullOrEmpty() } ?: when (code) {
        TrainingGenerationErrorCode.InvalidRequest ->
            "Запрос не прошел проверку backend. Проверьте профиль и параметры генерации."
        TrainingGenerationErrorCode.InvalidResponse ->
            "Backend вернул ответ в неподдерживаемом формате."
        TrainingGenerationErrorCode.Network ->
            "Не удалось связаться с backend. Проверьте адрес сервиса и соединение."
        TrainingGenerationErrorCode.ServiceUnavailable ->
            "Сервис генерации сейчас недоступен."
        TrainingGenerationErrorCode.ProviderError ->
            "AI-провайдер не смог собрать тренировку. Повторите запрос позже."
        TrainingGenerationErrorCode.Timeout ->
            "Генерация заняла слишком много времени. Повторите запрос."
        TrainingGenerationErrorCode.Unknown ->
            "Backend вернул ошибку без подробностей."
    }

    return TrainingGenerationError(
        code = code,
        message = message,
    )
}
