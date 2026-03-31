package com.vladislav.runningapp.activity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.activity.TrackedSessionStartFailureMessage
import com.vladislav.runningapp.core.permissions.MissingTrackedSessionPermissionsMessage
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FreeRunUiState(
    val trackerState: ActivityTrackerState = ActivityTrackerState(),
    val errorMessage: String? = null,
    val isStarting: Boolean = false,
)

@HiltViewModel
class FreeRunViewModel @Inject constructor(
    private val activityTracker: ActivityTracker,
    private val trackingPermissionChecker: TrackingPermissionChecker,
) : ViewModel() {
    private val errorMessage = MutableStateFlow<String?>(null)
    private val isStarting = MutableStateFlow(false)

    val uiState: StateFlow<FreeRunUiState> = combine(
        activityTracker.trackerState,
        errorMessage,
        isStarting,
    ) { trackerState, currentError, currentIsStarting ->
        FreeRunUiState(
            trackerState = trackerState,
            errorMessage = currentError,
            isStarting = currentIsStarting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = FreeRunUiState(trackerState = activityTracker.trackerState.value),
    )

    fun onStartFreeRun() {
        if (isStarting.value) {
            return
        }
        if (!trackingPermissionChecker.currentState().canStartTrackedSessions) {
            errorMessage.value = MissingTrackedSessionPermissionsMessage
            return
        }
        errorMessage.value = null
        isStarting.value = true
        viewModelScope.launch {
            val started = activityTracker.startFreeRun()
            if (!started) {
                errorMessage.value = TrackedSessionStartFailureMessage
            }
            isStarting.value = false
        }
    }

    fun onStopFreeRun() {
        errorMessage.value = null
        activityTracker.stopActiveSession()
    }
}
