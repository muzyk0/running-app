package com.vladislav.runningapp.activity.ui

import androidx.lifecycle.ViewModel
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class FreeRunViewModel @Inject constructor(
    private val activityTracker: ActivityTracker,
) : ViewModel() {
    val uiState: StateFlow<ActivityTrackerState> = activityTracker.trackerState

    fun onStartFreeRun() {
        activityTracker.startFreeRun()
    }

    fun onStopFreeRun() {
        activityTracker.stopActiveSession()
    }
}
