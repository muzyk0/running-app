package com.vladislav.runningapp.training.ui

import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutEditorReducerTest {
    @Test
    fun loadWorkoutPullsDomainDataIntoEditableState() {
        val state = WorkoutEditorReducer.reduce(
            state = WorkoutEditorState(),
            action = WorkoutEditorAction.LoadWorkout(sampleWorkout()),
        )

        assertEquals("workout-1", state.workoutId)
        assertEquals(DefaultWorkoutSchemaVersion, state.schemaVersion)
        assertEquals("Интервалы", state.title)
        assertEquals(300, state.totalDurationSec)
        assertEquals(WorkoutStepType.Run, state.steps[1].type)
    }

    @Test
    fun stepMutationsRecomputeDurationWithoutExtraState() {
        var state = WorkoutEditorReducer.reduce(
            state = WorkoutEditorState(),
            action = WorkoutEditorAction.LoadWorkout(null),
        )

        state = WorkoutEditorReducer.reduce(
            state = state,
            action = WorkoutEditorAction.SetStepDuration(index = 0, value = "180"),
        )
        state = WorkoutEditorReducer.reduce(
            state = state,
            action = WorkoutEditorAction.SetStepVoicePrompt(index = 0, value = "Разминка."),
        )
        state = WorkoutEditorReducer.reduce(
            state = state,
            action = WorkoutEditorAction.AddStep,
        )
        state = WorkoutEditorReducer.reduce(
            state = state,
            action = WorkoutEditorAction.SetStepDuration(index = 1, value = "90"),
        )

        assertEquals(270, state.totalDurationSec)

        state = WorkoutEditorReducer.reduce(
            state = state,
            action = WorkoutEditorAction.RemoveStep(index = 0),
        )

        assertEquals(90, state.totalDurationSec)
    }

    @Test
    fun validatorRejectsMissingTitleAndEmptyStepList() {
        val errors = WorkoutEditorValidator.validate(
            WorkoutEditorState(
                title = " ",
                steps = emptyList(),
            ),
        )

        assertTrue(errors.hasErrors)
        assertEquals("Укажите название тренировки.", errors.title)
        assertEquals("Добавьте хотя бы один шаг.", errors.stepsMessage)
    }

    @Test
    fun buildDuplicateWorkoutTitleAddsIncrementingSuffix() {
        val title = buildDuplicateWorkoutTitle(
            sourceTitle = "Интервалы",
            existingTitles = setOf(
                "Интервалы",
                "Интервалы (копия)",
                "Интервалы (копия 2)",
            ),
        )

        assertEquals("Интервалы (копия 3)", title)
    }

    private fun sampleWorkout(): Workout = Workout(
        id = "workout-1",
        schemaVersion = DefaultWorkoutSchemaVersion,
        title = "Интервалы",
        summary = "Легкий блок",
        goal = "Поддерживать форму",
        disclaimer = null,
        steps = listOf(
            WorkoutStep(
                type = WorkoutStepType.Warmup,
                durationSec = 180,
                voicePrompt = "Разминка.",
            ),
            WorkoutStep(
                type = WorkoutStepType.Run,
                durationSec = 120,
                voicePrompt = "Бежим.",
            ),
        ),
    )
}
