package com.vladislav.runningapp.session

import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionEngineTest {
    @Test
    fun startCreatesRunningStateAndAnnouncesFirstStep() {
        val transition = WorkoutSessionEngine.reduce(
            state = WorkoutSessionState(),
            action = WorkoutSessionAction.Start(sampleWorkout()),
        )

        assertEquals(WorkoutSessionStatus.Running, transition.state.status)
        assertEquals(0, transition.state.currentStepIndex)
        assertEquals(0, transition.state.totalElapsedSec)
        assertEquals(1, transition.effects.size)
        assertEquals(
            "Разминка в удобном темпе.",
            (transition.effects.single() as WorkoutSessionEffect.PlayStepCue).prompt,
        )
    }

    @Test
    fun ticksAdvanceAcrossStepBoundariesAndCompleteTheWorkout() {
        var state = WorkoutSessionEngine.reduce(
            state = WorkoutSessionState(),
            action = WorkoutSessionAction.Start(sampleWorkout()),
        ).state

        val firstTick = WorkoutSessionEngine.reduce(
            state = state,
            action = WorkoutSessionAction.Tick,
        )
        state = firstTick.state

        assertEquals(WorkoutSessionStatus.Running, state.status)
        assertEquals(1, state.totalElapsedSec)
        assertEquals(0, state.currentStepIndex)
        assertEquals(1, state.currentStepRemainingSec)
        assertTrue(firstTick.effects.isEmpty())

        val secondTick = WorkoutSessionEngine.reduce(
            state = state,
            action = WorkoutSessionAction.Tick,
        )
        state = secondTick.state

        assertEquals(2, state.totalElapsedSec)
        assertEquals(1, state.currentStepIndex)
        assertEquals(0, state.currentStepElapsedSec)
        assertEquals(
            "Переходим к беговому отрезку.",
            (secondTick.effects.single() as WorkoutSessionEffect.PlayStepCue).prompt,
        )

        state = WorkoutSessionEngine.reduce(
            state = state,
            action = WorkoutSessionAction.Tick,
        ).state
        state = WorkoutSessionEngine.reduce(
            state = state,
            action = WorkoutSessionAction.Tick,
        ).state

        assertEquals(WorkoutSessionStatus.Completed, state.status)
        assertEquals(4, state.totalElapsedSec)
        assertEquals(0, state.currentStepRemainingSec)
    }

    @Test
    fun pauseAndResumeGateTickProgress() {
        val startedState = WorkoutSessionEngine.reduce(
            state = WorkoutSessionState(),
            action = WorkoutSessionAction.Start(sampleWorkout()),
        ).state

        val pausedState = WorkoutSessionEngine.reduce(
            state = startedState,
            action = WorkoutSessionAction.Pause,
        ).state

        val afterTickWhilePaused = WorkoutSessionEngine.reduce(
            state = pausedState,
            action = WorkoutSessionAction.Tick,
        ).state

        assertEquals(pausedState, afterTickWhilePaused)

        val resumedState = WorkoutSessionEngine.reduce(
            state = afterTickWhilePaused,
            action = WorkoutSessionAction.Resume,
        ).state

        val progressedState = WorkoutSessionEngine.reduce(
            state = resumedState,
            action = WorkoutSessionAction.Tick,
        ).state

        assertEquals(WorkoutSessionStatus.Running, progressedState.status)
        assertEquals(1, progressedState.totalElapsedSec)
        assertEquals(1, progressedState.currentStepElapsedSec)
    }

    @Test
    fun stopResetsSessionToIdle() {
        val startedState = WorkoutSessionEngine.reduce(
            state = WorkoutSessionState(),
            action = WorkoutSessionAction.Start(sampleWorkout()),
        ).state

        val stoppedState = WorkoutSessionEngine.reduce(
            state = startedState,
            action = WorkoutSessionAction.Stop,
        ).state

        assertEquals(WorkoutSessionState(), stoppedState)
    }

    @Test
    fun startWithEmptyWorkoutReturnsIdleStateWithoutEffects() {
        val transition = WorkoutSessionEngine.reduce(
            state = WorkoutSessionState(),
            action = WorkoutSessionAction.Start(sampleWorkout().copy(steps = emptyList())),
        )

        assertEquals(WorkoutSessionState(), transition.state)
        assertTrue(transition.effects.isEmpty())
    }

    private fun sampleWorkout(): Workout = Workout(
        id = "workout-session-1",
        schemaVersion = DefaultWorkoutSchemaVersion,
        title = "Интервальный блок",
        summary = null,
        goal = null,
        disclaimer = null,
        steps = listOf(
            WorkoutStep(
                type = WorkoutStepType.Warmup,
                durationSec = 2,
                voicePrompt = "Разминка в удобном темпе.",
            ),
            WorkoutStep(
                type = WorkoutStepType.Run,
                durationSec = 2,
                voicePrompt = "Переходим к беговому отрезку.",
            ),
        ),
    )
}
