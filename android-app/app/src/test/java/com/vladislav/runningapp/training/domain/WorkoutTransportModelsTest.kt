package com.vladislav.runningapp.training.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutTransportModelsTest {
    @Test
    fun toDomainWorkoutNormalizesTransportFieldsAndRecomputesDuration() {
        val dto = WorkoutEnvelopeDto(
            schemaVersion = "draft-1",
            training = WorkoutDto(
                title = " Интервалы для новичка ",
                summary = " Чередование ходьбы и бега ",
                goal = " Вернуться к регулярным пробежкам ",
                estimatedDurationSec = 9999,
                disclaimer = " ",
                steps = listOf(
                    WorkoutStepDto(
                        id = "step-1",
                        type = "warmup_walk",
                        durationSec = 300,
                        voicePrompt = " Разминаемся быстрым шагом. ",
                    ),
                    WorkoutStepDto(
                        id = "step-2",
                        type = "run",
                        durationSec = 180,
                        voicePrompt = " Легкий бег три минуты. ",
                    ),
                ),
            ),
        )

        val workout = dto.toDomainWorkout(workoutId = "local-1")

        assertEquals("draft-1", workout.schemaVersion)
        assertEquals("Интервалы для новичка", workout.title)
        assertEquals("Чередование ходьбы и бега", workout.summary)
        assertEquals("Вернуться к регулярным пробежкам", workout.goal)
        assertNull(workout.disclaimer)
        assertEquals(WorkoutStepType.Warmup, workout.steps[0].type)
        assertEquals(WorkoutStepType.Run, workout.steps[1].type)
        assertEquals(480, workout.estimatedDurationSec)
    }

    @Test
    fun toTransportDtoUsesCanonicalTypesAndGeneratedStepIds() {
        val workout = Workout(
            id = "local-7",
            schemaVersion = DefaultWorkoutSchemaVersion,
            title = "Интервалы",
            summary = null,
            goal = "Адаптация к беговой нагрузке",
            disclaimer = "Приложение не является медицинской рекомендацией.",
            steps = listOf(
                WorkoutStep(
                    type = WorkoutStepType.Warmup,
                    durationSec = 180,
                    voicePrompt = "Разминка.",
                ),
                WorkoutStep(
                    type = WorkoutStepType.Walk,
                    durationSec = 120,
                    voicePrompt = "Шагаем.",
                ),
            ),
        )

        val dto = workout.toTransportDto()

        assertEquals(DefaultWorkoutSchemaVersion, dto.schemaVersion)
        assertEquals(300, dto.training.estimatedDurationSec)
        assertEquals("step-1", dto.training.steps[0].id)
        assertEquals("warmup", dto.training.steps[0].type)
        assertEquals("step-2", dto.training.steps[1].id)
        assertEquals("walk", dto.training.steps[1].type)
    }

    @Test(expected = IllegalArgumentException::class)
    fun toDomainWorkoutRejectsUnsupportedStepTypes() {
        WorkoutEnvelopeDto(
            schemaVersion = DefaultWorkoutSchemaVersion,
            training = WorkoutDto(
                title = "Интервалы",
                steps = listOf(
                    WorkoutStepDto(
                        id = "step-1",
                        type = "sprint",
                        durationSec = 120,
                        voicePrompt = "Ускорение.",
                    ),
                ),
            ),
        ).toDomainWorkout(workoutId = "local-unsupported")
    }
}
