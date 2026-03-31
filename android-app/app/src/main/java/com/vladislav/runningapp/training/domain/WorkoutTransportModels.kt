package com.vladislav.runningapp.training.domain

data class WorkoutEnvelopeDto(
    val schemaVersion: String,
    val training: WorkoutDto,
)

data class WorkoutDto(
    val title: String,
    val summary: String? = null,
    val goal: String? = null,
    val estimatedDurationSec: Int? = null,
    val disclaimer: String? = null,
    val steps: List<WorkoutStepDto>,
)

data class WorkoutStepDto(
    val id: String,
    val type: String,
    val durationSec: Int,
    val voicePrompt: String,
)

fun WorkoutEnvelopeDto.toDomainWorkout(workoutId: String): Workout = Workout(
    id = workoutId,
    schemaVersion = schemaVersion,
    title = training.title.trim(),
    summary = training.summary?.trim().orNullIfBlank(),
    goal = training.goal?.trim().orNullIfBlank(),
    disclaimer = training.disclaimer?.trim().orNullIfBlank(),
    steps = training.steps.map { step ->
        WorkoutStep(
            type = WorkoutStepType.fromRawValue(step.type),
            durationSec = step.durationSec,
            voicePrompt = step.voicePrompt.trim(),
        )
    },
)

fun Workout.toTransportDto(): WorkoutEnvelopeDto = WorkoutEnvelopeDto(
    schemaVersion = schemaVersion,
    training = WorkoutDto(
        title = title,
        summary = summary,
        goal = goal,
        estimatedDurationSec = estimatedDurationSec,
        disclaimer = disclaimer,
        steps = steps.mapIndexed { index, step ->
            WorkoutStepDto(
                id = "step-${index + 1}",
                type = step.type.canonicalValue,
                durationSec = step.durationSec,
                voicePrompt = step.voicePrompt,
            )
        },
    ),
)

private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }
