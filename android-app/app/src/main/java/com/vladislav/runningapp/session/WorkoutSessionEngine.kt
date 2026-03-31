package com.vladislav.runningapp.session

import com.vladislav.runningapp.training.domain.Workout

sealed interface WorkoutSessionAction {
    data class Start(
        val workout: Workout,
    ) : WorkoutSessionAction

    data object Tick : WorkoutSessionAction

    data object Pause : WorkoutSessionAction

    data object Resume : WorkoutSessionAction

    data object Stop : WorkoutSessionAction
}

sealed interface WorkoutSessionEffect {
    data class PlayStepCue(
        val prompt: String,
    ) : WorkoutSessionEffect
}

data class WorkoutSessionTransition(
    val state: WorkoutSessionState,
    val effects: List<WorkoutSessionEffect> = emptyList(),
)

object WorkoutSessionEngine {
    fun reduce(
        state: WorkoutSessionState,
        action: WorkoutSessionAction,
    ): WorkoutSessionTransition = when (action) {
        is WorkoutSessionAction.Start -> startSession(action.workout)
        WorkoutSessionAction.Tick -> tick(state)
        WorkoutSessionAction.Pause -> pause(state)
        WorkoutSessionAction.Resume -> resume(state)
        WorkoutSessionAction.Stop -> WorkoutSessionTransition(state = WorkoutSessionState())
    }

    private fun startSession(workout: Workout): WorkoutSessionTransition {
        if (workout.steps.isEmpty()) {
            return WorkoutSessionTransition(state = WorkoutSessionState())
        }

        val initialState = WorkoutSessionState(
            status = WorkoutSessionStatus.Running,
            workout = workout,
            currentStepIndex = 0,
            currentStepElapsedSec = 0,
            totalElapsedSec = 0,
        )
        return WorkoutSessionTransition(
            state = initialState,
            effects = listOf(WorkoutSessionEffect.PlayStepCue(workout.steps.first().voicePrompt)),
        )
    }

    private fun tick(state: WorkoutSessionState): WorkoutSessionTransition {
        val workout = state.workout ?: return WorkoutSessionTransition(state = state)
        if (state.status != WorkoutSessionStatus.Running) {
            return WorkoutSessionTransition(state = state)
        }

        val currentStep = state.currentStep ?: return WorkoutSessionTransition(state = state)
        val nextTotalElapsed = (state.totalElapsedSec + 1).coerceAtMost(workout.estimatedDurationSec)
        val nextStepElapsed = state.currentStepElapsedSec + 1

        if (nextStepElapsed < currentStep.durationSec) {
            return WorkoutSessionTransition(
                state = state.copy(
                    totalElapsedSec = nextTotalElapsed,
                    currentStepElapsedSec = nextStepElapsed,
                ),
            )
        }

        val nextStepIndex = state.currentStepIndex + 1
        return if (nextStepIndex < workout.steps.size) {
            WorkoutSessionTransition(
                state = state.copy(
                    currentStepIndex = nextStepIndex,
                    currentStepElapsedSec = 0,
                    totalElapsedSec = nextTotalElapsed,
                ),
                effects = listOf(
                    WorkoutSessionEffect.PlayStepCue(workout.steps[nextStepIndex].voicePrompt),
                ),
            )
        } else {
            WorkoutSessionTransition(
                state = state.copy(
                    status = WorkoutSessionStatus.Completed,
                    currentStepElapsedSec = currentStep.durationSec,
                    totalElapsedSec = workout.estimatedDurationSec,
                ),
            )
        }
    }

    private fun pause(state: WorkoutSessionState): WorkoutSessionTransition = if (
        state.status == WorkoutSessionStatus.Running
    ) {
        WorkoutSessionTransition(
            state = state.copy(status = WorkoutSessionStatus.Paused),
        )
    } else {
        WorkoutSessionTransition(state = state)
    }

    private fun resume(state: WorkoutSessionState): WorkoutSessionTransition = if (
        state.status == WorkoutSessionStatus.Paused
    ) {
        WorkoutSessionTransition(
            state = state.copy(status = WorkoutSessionStatus.Running),
        )
    } else {
        WorkoutSessionTransition(state = state)
    }
}
