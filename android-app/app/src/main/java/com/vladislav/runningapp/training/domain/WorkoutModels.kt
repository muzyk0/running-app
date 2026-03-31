package com.vladislav.runningapp.training.domain

const val DefaultWorkoutSchemaVersion = "mvp.v1"

enum class WorkoutStepType(
    val canonicalValue: String,
) {
    Warmup("warmup"),
    Run("run"),
    Walk("walk"),
    Cooldown("cooldown"),
    Rest("rest"),
    ;

    companion object {
        fun fromRawValue(value: String): WorkoutStepType = when (value.trim().lowercase()) {
            Warmup.canonicalValue,
            "warmup_walk" -> Warmup

            Run.canonicalValue -> Run
            Walk.canonicalValue -> Walk

            Cooldown.canonicalValue,
            "cooldown_walk" -> Cooldown

            Rest.canonicalValue -> Rest
            else -> Walk
        }
    }
}

data class WorkoutStep(
    val type: WorkoutStepType,
    val durationSec: Int,
    val voicePrompt: String,
)

data class Workout(
    val id: String,
    val schemaVersion: String,
    val title: String,
    val summary: String?,
    val goal: String?,
    val disclaimer: String?,
    val steps: List<WorkoutStep>,
) {
    val estimatedDurationSec: Int
        get() = steps.sumOf { step -> step.durationSec.coerceAtLeast(0) }
}
