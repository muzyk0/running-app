package com.vladislav.runningapp.session

import com.vladislav.runningapp.core.startup.MainDispatcherRule
import com.vladislav.runningapp.session.audio.SessionCuePlayer
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultWorkoutSessionControllerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun tickerAdvancesOnlyWhileRunningAndTriggersCuesOnStepBoundaries() = runTest(mainDispatcherRule.dispatcher) {
        val cuePlayer = RecordingSessionCuePlayer()
        val controller = DefaultWorkoutSessionController(
            sessionCuePlayer = cuePlayer,
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        controller.startWorkout(sampleWorkout())

        assertEquals(listOf("Разминка в ходьбе."), cuePlayer.prompts)
        assertEquals(WorkoutSessionStatus.Running, controller.sessionState.value.status)

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_000)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, controller.sessionState.value.currentStepIndex)
        assertEquals(1, controller.sessionState.value.totalElapsedSec)
        assertEquals(
            listOf("Разминка в ходьбе.", "Начинаем основной бег."),
            cuePlayer.prompts,
        )

        controller.pauseWorkout()
        val elapsedAtPause = controller.sessionState.value.totalElapsedSec

        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(3_000)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(WorkoutSessionStatus.Paused, controller.sessionState.value.status)
        assertEquals(elapsedAtPause, controller.sessionState.value.totalElapsedSec)
        assertEquals(2, cuePlayer.prompts.size)

        controller.resumeWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(2_000)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(WorkoutSessionStatus.Completed, controller.sessionState.value.status)
        assertEquals(sampleWorkout().estimatedDurationSec, controller.sessionState.value.totalElapsedSec)
        assertTrue(controller.sessionState.value.canStop)
        assertEquals(2, cuePlayer.prompts.size)
    }

    private fun sampleWorkout(): Workout = Workout(
        id = "controller-workout",
        schemaVersion = DefaultWorkoutSchemaVersion,
        title = "Контрольная тренировка",
        summary = null,
        goal = null,
        disclaimer = null,
        steps = listOf(
            WorkoutStep(
                type = WorkoutStepType.Warmup,
                durationSec = 1,
                voicePrompt = "Разминка в ходьбе.",
            ),
            WorkoutStep(
                type = WorkoutStepType.Run,
                durationSec = 2,
                voicePrompt = "Начинаем основной бег.",
            ),
        ),
    )

    private class RecordingSessionCuePlayer : SessionCuePlayer {
        val prompts = mutableListOf<String>()

        override fun play(prompt: String) {
            prompts += prompt
        }
    }
}
