package com.vladislav.runningapp.ai.data.remote

import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutDto
import com.vladislav.runningapp.training.domain.WorkoutEnvelopeDto
import com.vladislav.runningapp.training.domain.WorkoutStepDto
import com.vladislav.runningapp.training.domain.toDomainWorkout

internal fun UserProfile.toGenerateTrainingRequestDto(
    userNote: String?,
): GenerateTrainingRequestDto = GenerateTrainingRequestDto(
    profile = GenerateTrainingProfileDto(
        heightCm = heightCm,
        weightKg = weightKg,
        sex = sex.storageValue,
        age = age,
        trainingDaysPerWeek = trainingDaysPerWeek,
        fitnessLevel = fitnessLevel.storageValue,
        injuriesAndLimitations = injuriesAndLimitations.trim(),
        trainingGoal = trainingGoal.trim(),
        additionalPromptFields = additionalPromptFields.map { field ->
            GenerateTrainingPromptFieldDto(
                label = field.label.trim(),
                value = field.value.trim(),
            )
        },
    ),
    request = GenerateTrainingRequestContextDto(
        locale = "ru-RU",
        userNote = userNote?.trim()?.takeIf { value -> value.isNotEmpty() },
    ),
)

internal fun GenerateTrainingResponseDto.toWorkoutEnvelopeDto(): WorkoutEnvelopeDto = WorkoutEnvelopeDto(
    schemaVersion = schemaVersion.trim().ifBlank { DefaultWorkoutSchemaVersion },
    training = WorkoutDto(
        title = training.title,
        summary = training.summary,
        goal = training.goal,
        estimatedDurationSec = training.estimatedDurationSec,
        disclaimer = training.disclaimer,
        steps = training.steps.map { step ->
            WorkoutStepDto(
                id = step.id,
                type = step.type,
                durationSec = step.durationSec,
                voicePrompt = step.voicePrompt,
            )
        },
    ),
)

internal fun GenerateTrainingResponseDto.toGeneratedWorkout(): Workout =
    toWorkoutEnvelopeDto().toDomainWorkout(workoutId = GeneratedWorkoutPreviewId)

internal fun RemoteTrainingGenerationStreamEventDto.toDomainUpdate(): TrainingGenerationUpdate = when (this) {
    is RemoteTrainingGenerationStreamEventDto.Log ->
        TrainingGenerationUpdate.Log(message = payload.message)

    is RemoteTrainingGenerationStreamEventDto.Completed -> {
        val workout = payload.toGeneratedWorkout()
        require(workout.steps.isNotEmpty()) {
            "Backend вернул тренировку без шагов."
        }
        TrainingGenerationUpdate.Completed(workout = workout)
    }

    is RemoteTrainingGenerationStreamEventDto.Error ->
        TrainingGenerationUpdate.Failure(error = payload.toTrainingGenerationError())
}

internal fun ApiErrorEnvelopeDto?.toTrainingGenerationError(
    statusCode: Int? = null,
): TrainingGenerationError {
    val code = when (this?.error?.code?.trim()) {
        "invalid_request" -> TrainingGenerationErrorCode.InvalidRequest
        "invalid_json" -> TrainingGenerationErrorCode.InvalidResponse
        "service_unavailable" -> TrainingGenerationErrorCode.ServiceUnavailable
        "provider_error" -> TrainingGenerationErrorCode.ProviderError
        "request_timeout" -> TrainingGenerationErrorCode.Timeout
        else -> when (statusCode) {
            400 -> TrainingGenerationErrorCode.InvalidRequest
            408, 504 -> TrainingGenerationErrorCode.Timeout
            502 -> TrainingGenerationErrorCode.ProviderError
            503 -> TrainingGenerationErrorCode.ServiceUnavailable
            else -> TrainingGenerationErrorCode.Unknown
        }
    }

    val message = this?.error?.message?.trim().takeUnless { it.isNullOrEmpty() } ?: when (code) {
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
