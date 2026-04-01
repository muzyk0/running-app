package com.vladislav.runningapp.activity

import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.uiText
import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.StateFlow

interface ActivityTracker {
    val trackerState: StateFlow<ActivityTrackerState>

    suspend fun startFreeRun(): Boolean

    suspend fun startPlannedWorkout(workout: Workout): Boolean

    fun stopActiveSession()
}

val TrackedSessionStartFailureMessage =
    uiText(R.string.activity_error_tracked_session_start_failed)
