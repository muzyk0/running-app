package com.vladislav.runningapp.session

import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.StateFlow

interface WorkoutSessionController {
    val sessionState: StateFlow<WorkoutSessionState>

    fun startWorkout(workout: Workout)

    fun pauseWorkout()

    fun resumeWorkout()

    fun stopWorkout()
}
