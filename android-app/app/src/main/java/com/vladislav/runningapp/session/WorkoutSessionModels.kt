package com.vladislav.runningapp.session

import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep

enum class WorkoutSessionStatus {
    Idle,
    Running,
    Paused,
    Completed,
}

data class WorkoutSessionState(
    val status: WorkoutSessionStatus = WorkoutSessionStatus.Idle,
    val workout: Workout? = null,
    val currentStepIndex: Int = 0,
    val currentStepElapsedSec: Int = 0,
    val totalElapsedSec: Int = 0,
    val lastCuePrompt: String? = null,
) {
    val currentStep: WorkoutStep?
        get() = workout?.steps?.getOrNull(currentStepIndex)

    val currentStepNumber: Int
        get() = if (workout == null) 0 else currentStepIndex + 1

    val totalSteps: Int
        get() = workout?.steps?.size ?: 0

    val currentStepRemainingSec: Int
        get() = currentStep?.durationSec?.minus(currentStepElapsedSec)?.coerceAtLeast(0) ?: 0

    val totalDurationSec: Int
        get() = workout?.estimatedDurationSec ?: 0

    val canPause: Boolean
        get() = status == WorkoutSessionStatus.Running

    val canResume: Boolean
        get() = status == WorkoutSessionStatus.Paused

    val canStop: Boolean
        get() = workout != null
}
