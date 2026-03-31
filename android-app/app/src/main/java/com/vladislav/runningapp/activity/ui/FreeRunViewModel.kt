package com.vladislav.runningapp.activity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.core.permissions.MissingTrackedSessionPermissionsMessage
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class FreeRunUiState(
    val trackerState: ActivityTrackerState = ActivityTrackerState(),
    val errorMessage: String? = null,
)

@HiltViewModel
class FreeRunViewModel @Inject constructor(
    private val activityTracker: ActivityTracker,
    private val trackingPermissionChecker: TrackingPermissionChecker,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<FreeRunUiState> = combine(
        activityTracker.trackerState,
        errorMessage,
    ) { trackerState, currentError ->
        FreeRunUiState(
            trackerState = trackerState,
            errorMessage = currentError,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FreeRunUiState(trackerState = activityTracker.trackerState.value),
    )

    fun onStartFreeRun() {
        if (!trackingPermissionChecker.currentState().canStartTrackedSessions) {
            errorMessage.value = MissingTrackedSessionPermissionsMessage
            return
        }
        errorMessage.value = null
        activityTracker.startFreeRun()
    }

    fun onStopFreeRun() {
        errorMessage.value = null
        activityTracker.stopActiveSession()
    }
}
