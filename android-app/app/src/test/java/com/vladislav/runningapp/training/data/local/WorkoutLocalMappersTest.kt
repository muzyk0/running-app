package com.vladislav.runningapp.training.data.local

import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkoutLocalMappersTest {
    @Test
    fun toWorkoutStepEntitiesAssignsStableIndexesAndCanonicalTypes() {
        val workout = sampleWorkout()

        val steps = workout.toWorkoutStepEntities()

        assertEquals(listOf(0, 1), steps.map { step -> step.stepIndex })
        assertEquals(listOf("warmup", "run"), steps.map { step -> step.type })
        assertEquals(listOf("workout-1", "workout-1"), steps.map { step -> step.workoutId })
    }

    @Test
    fun toDomainModelSortsStepsByIndexAndKeepsSchemaVersion() {
        val record = WorkoutRecord(
            workout = WorkoutEntity(
                id = "workout-1",
                schemaVersion = DefaultWorkoutSchemaVersion,
                title = "Интервалы",
                summary = "Короткие интервалы",
                goal = null,
                disclaimer = null,
                updatedAtEpochMs = 1L,
            ),
            steps = listOf(
                WorkoutStepEntity(
                    workoutId = "workout-1",
                    stepIndex = 1,
                    type = "run",
                    durationSec = 120,
                    voicePrompt = "Бежим.",
                ),
                WorkoutStepEntity(
                    workoutId = "workout-1",
                    stepIndex = 0,
                    type = "warmup",
                    durationSec = 180,
                    voicePrompt = "Разминаемся.",
                ),
            ),
        )

        val workout = record.toDomainModel()

        assertEquals(DefaultWorkoutSchemaVersion, workout.schemaVersion)
        assertEquals(listOf(WorkoutStepType.Warmup, WorkoutStepType.Run), workout.steps.map { step -> step.type })
        assertEquals(300, workout.estimatedDurationSec)
    }

    private fun sampleWorkout(): Workout = Workout(
        id = "workout-1",
        schemaVersion = DefaultWorkoutSchemaVersion,
        title = "Интервалы",
        summary = "Короткие интервалы",
        goal = "Поддерживать форму",
        disclaimer = null,
        steps = listOf(
            WorkoutStep(
                type = WorkoutStepType.Warmup,
                durationSec = 180,
                voicePrompt = "Разминаемся.",
            ),
            WorkoutStep(
                type = WorkoutStepType.Run,
                durationSec = 120,
                voicePrompt = "Бежим.",
            ),
        ),
    )
}
