package com.vladislav.runningapp.ai.data.remote

import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.WorkoutDto
import com.vladislav.runningapp.training.domain.WorkoutEnvelopeDto
import com.vladislav.runningapp.training.domain.WorkoutStepDto

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
