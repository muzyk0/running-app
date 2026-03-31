package com.vladislav.runningapp.activity

import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.StateFlow

interface ActivityTracker {
    val trackerState: StateFlow<ActivityTrackerState>

    fun startFreeRun()

    fun startPlannedWorkout(workout: Workout)

    fun stopActiveSession()
}
