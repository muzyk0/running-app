package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationFailureSource
import com.vladislav.runningapp.ai.domain.TrainingGenerationRepository
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
import com.vladislav.runningapp.profile.UserProfile
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import retrofit2.Response

internal const val GeneratedWorkoutPreviewId = "generated-preview"

@Singleton
class DefaultTrainingGenerationRepository @Inject constructor(
    private val apiService: TrainingGenerationApiService,
    private val gson: Gson,
) : TrainingGenerationRepository {
    override fun generateWorkout(
        profile: UserProfile,
        userNote: String?,
    ): Flow<TrainingGenerationUpdate> = flow {
        var streamResponseStarted = false
        try {
            val response = apiService.generateTraining(profile.toGenerateTrainingRequestDto(userNote))
            if (!response.isSuccessful) {
                emit(
                    TrainingGenerationUpdate.Failure(
                        error = response.toTrainingGenerationError(gson),
                        source = TrainingGenerationFailureSource.Request,
                    ),
                )
            } else {
                val body = response.body()
                if (body == null) {
                    emit(
                        TrainingGenerationUpdate.Failure(
                            TrainingGenerationError(
                                code = TrainingGenerationErrorCode.InvalidResponse,
                                message = "Backend вернул пустой поток генерации.",
                            ),
                            source = TrainingGenerationFailureSource.Stream,
                        ),
                    )
                } else {
                    streamResponseStarted = true
                    var terminalEventSeen = false
                    body.use { responseBody ->
                        responseBody.toTrainingGenerationEvents(gson).collect { event ->
                            val update = event.toDomainUpdate()
                            emit(update)
                            terminalEventSeen = update is TrainingGenerationUpdate.Completed ||
                                update is TrainingGenerationUpdate.Failure
                        }
                    }
                    if (!terminalEventSeen) {
                        emit(
                            TrainingGenerationUpdate.Failure(
                                TrainingGenerationError(
                                    code = TrainingGenerationErrorCode.InvalidResponse,
                                    message = "Backend завершил поток без terminal event.",
                                ),
                                source = TrainingGenerationFailureSource.Stream,
                            ),
                        )
                    }
                }
            }
        } catch (_: IOException) {
            emit(
                TrainingGenerationUpdate.Failure(
                    error = TrainingGenerationError(
                        code = TrainingGenerationErrorCode.Network,
                        message = "Не удалось связаться с backend. Проверьте адрес сервиса и соединение.",
                    ),
                    source = if (streamResponseStarted) {
                        TrainingGenerationFailureSource.Stream
                    } else {
                        TrainingGenerationFailureSource.Request
                    },
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            emit(
                TrainingGenerationUpdate.Failure(
                    error = TrainingGenerationError(
                        code = TrainingGenerationErrorCode.InvalidResponse,
                        message = error.message?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: "Backend вернул ответ в неподдерживаемом формате.",
                    ),
                    source = TrainingGenerationFailureSource.Stream,
                ),
            )
        } catch (error: JsonParseException) {
            emit(
                TrainingGenerationUpdate.Failure(
                    error = TrainingGenerationError(
                        code = TrainingGenerationErrorCode.InvalidResponse,
                        message = "Backend вернул ответ в неподдерживаемом формате.",
                    ),
                    source = TrainingGenerationFailureSource.Stream,
                ),
            )
        } catch (error: Exception) {
            emit(
                TrainingGenerationUpdate.Failure(
                    error = TrainingGenerationError(
                        code = TrainingGenerationErrorCode.Unknown,
                        message = error.message?.trim().takeUnless { it.isNullOrEmpty() }
                            ?: "Не удалось обработать ответ backend.",
                    ),
                    source = if (streamResponseStarted) {
                        TrainingGenerationFailureSource.Stream
                    } else {
                        TrainingGenerationFailureSource.Request
                    },
                ),
            )
        }
    }
}

private fun Response<*>.toTrainingGenerationError(
    gson: Gson,
): TrainingGenerationError {
    val apiError = errorBody()?.charStream()?.use { reader ->
        runCatching { gson.fromJson(reader, ApiErrorEnvelopeDto::class.java) }.getOrNull()
    }
    return apiError.toTrainingGenerationError(statusCode = code())
}
