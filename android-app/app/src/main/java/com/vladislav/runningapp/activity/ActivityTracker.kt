package com.vladislav.runningapp.activity

import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.StateFlow

interface ActivityTracker {
    val trackerState: StateFlow<ActivityTrackerState>

    suspend fun startFreeRun(): Boolean

    suspend fun startPlannedWorkout(workout: Workout): Boolean

    fun stopActiveSession()
}

const val TrackedSessionStartFailureMessage =
    "Не удалось запустить активную сессию. Проверьте точную геолокацию и повторите попытку."
