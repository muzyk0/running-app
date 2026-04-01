package com.vladislav.runningapp.ai.data.remote

import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationFailureSource
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
import com.vladislav.runningapp.core.i18n.SupportedAppLocale
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutDto
import com.vladislav.runningapp.training.domain.WorkoutEnvelopeDto
import com.vladislav.runningapp.training.domain.WorkoutStepDto
import com.vladislav.runningapp.training.domain.toDomainWorkout

internal fun UserProfile.toGenerateTrainingRequestDto(
    userNote: String?,
    locale: SupportedAppLocale,
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
        locale = locale.languageTag,
        userNote = userNote?.trim()?.takeIf { value -> value.isNotEmpty() },
    ),
)

internal fun GenerateTrainingResponseDto.toWorkoutEnvelopeDto(): WorkoutEnvelopeDto {
    val normalizedSchemaVersion = requireField(schemaVersion, "schema_version")
    require(normalizedSchemaVersion == DefaultWorkoutSchemaVersion) {
        "Backend вернул неподдерживаемую версию схемы: $normalizedSchemaVersion."
    }

    val trainingPayload = requireNotNull(training) {
        "Backend не прислал training."
    }
    val steps = requireNotNull(trainingPayload.steps) {
        "Backend не прислал training.steps."
    }
    require(steps.isNotEmpty()) {
        "Backend вернул тренировку без шагов."
    }

    return WorkoutEnvelopeDto(
        schemaVersion = normalizedSchemaVersion,
        training = WorkoutDto(
            title = requireField(trainingPayload.title, "training.title"),
            summary = trainingPayload.summary,
            goal = trainingPayload.goal,
            estimatedDurationSec = trainingPayload.estimatedDurationSec,
            disclaimer = trainingPayload.disclaimer,
            steps = steps.mapIndexed { index, step ->
                WorkoutStepDto(
                    id = requireField(step.id, "training.steps[$index].id"),
                    type = requireField(step.type, "training.steps[$index].type"),
                    durationSec = requirePositiveField(
                        value = step.durationSec,
                        fieldName = "training.steps[$index].duration_sec",
                    ),
                    voicePrompt = requireField(
                        value = step.voicePrompt,
                        fieldName = "training.steps[$index].voice_prompt",
                    ),
                )
            },
        ),
    )
}

internal fun GenerateTrainingResponseDto.toGeneratedWorkout(): Workout =
    toWorkoutEnvelopeDto().toDomainWorkout(workoutId = GeneratedWorkoutPreviewId)

internal fun RemoteTrainingGenerationStreamEventDto.toDomainUpdate(): TrainingGenerationUpdate = when (this) {
    is RemoteTrainingGenerationStreamEventDto.Log ->
        TrainingGenerationUpdate.Log(
            message = requireField(payload.message, "log.message"),
        )

    is RemoteTrainingGenerationStreamEventDto.Completed -> {
        val workout = payload.toGeneratedWorkout()
        TrainingGenerationUpdate.Completed(workout = workout)
    }

    is RemoteTrainingGenerationStreamEventDto.Error ->
        TrainingGenerationUpdate.Failure(
            error = payload.toTrainingGenerationError(),
            source = TrainingGenerationFailureSource.Stream,
        )
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

private fun requireField(
    value: String?,
    fieldName: String,
): String = value?.trim().takeUnless { it.isNullOrEmpty() }
    ?: throw IllegalArgumentException("Backend не прислал $fieldName.")

private fun requirePositiveField(
    value: Int?,
    fieldName: String,
): Int {
    requireNotNull(value) {
        "Backend не прислал $fieldName."
    }
    require(value > 0) {
        "Backend прислал недопустимое значение $fieldName."
    }
    return value
}
