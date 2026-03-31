package com.vladislav.runningapp.session.ui

import androidx.lifecycle.ViewModel
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class ActiveSessionViewModel @Inject constructor(
    private val workoutSessionController: WorkoutSessionController,
) : ViewModel() {
    val uiState: StateFlow<WorkoutSessionState> = workoutSessionController.sessionState

    fun onPause() {
        workoutSessionController.pauseWorkout()
    }

    fun onResume() {
        workoutSessionController.resumeWorkout()
    }

    fun onStop() {
        workoutSessionController.stopWorkout()
    }
}
