package com.vladislav.runningapp.session.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ActiveWorkoutUiState(
    val trackingState: ActivityTrackerState = ActivityTrackerState(),
    val workoutSessionState: WorkoutSessionState = WorkoutSessionState(),
) {
    val hasPlannedWorkoutSession: Boolean
        get() = trackingState.isPlannedWorkout && workoutSessionState.workout != null
}

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val activityTracker: ActivityTracker,
    private val workoutSessionController: WorkoutSessionController,
) : ViewModel() {
    val uiState: StateFlow<ActiveWorkoutUiState> = combine(
        activityTracker.trackerState,
        workoutSessionController.sessionState,
    ) { trackingState, workoutSessionState ->
        ActiveWorkoutUiState(
            trackingState = trackingState,
            workoutSessionState = workoutSessionState,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ActiveWorkoutUiState(),
    )

    fun onPause() {
        workoutSessionController.pauseWorkout()
    }

    fun onResume() {
        workoutSessionController.resumeWorkout()
    }

    fun onStop() {
        activityTracker.stopActiveSession()
    }
}
