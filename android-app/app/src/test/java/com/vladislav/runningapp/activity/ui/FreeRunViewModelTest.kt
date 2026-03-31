package com.vladislav.runningapp.activity.ui

import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.activity.TrackedSessionStartFailureMessage
import com.vladislav.runningapp.core.permissions.MissingTrackedSessionPermissionsMessage
import com.vladislav.runningapp.core.permissions.PermissionRequirementsState
import com.vladislav.runningapp.core.permissions.RequirementState
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class FreeRunViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun onStartFreeRunSurfacesPermissionErrorWithoutStartingTracker() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker()
        val viewModel = FreeRunViewModel(
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = false),
        )

        viewModel.onStartFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(
            MissingTrackedSessionPermissionsMessage,
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(tracker.trackerState.value.isTracking)
    }

    @Test
    fun onStartFreeRunClearsErrorAndStartsTrackerWhenPermissionsAreGranted() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker()
        val viewModel = FreeRunViewModel(
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
        )

        viewModel.onStartFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(true, tracker.trackerState.value.isTracking)
    }

    @Test
    fun onStartFreeRunSurfacesTrackerStartFailure() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker(startFreeRunResult = false)
        val viewModel = FreeRunViewModel(
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
        )

        viewModel.onStartFreeRun()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(TrackedSessionStartFailureMessage, viewModel.uiState.value.errorMessage)
        assertFalse(tracker.trackerState.value.isTracking)
        assertFalse(viewModel.uiState.value.isStarting)
    }

    private class FakeActivityTracker(
        private val startFreeRunResult: Boolean = true,
    ) : ActivityTracker {
        private val mutableState = MutableStateFlow(ActivityTrackerState())

        override val trackerState: StateFlow<ActivityTrackerState> = mutableState

        override suspend fun startFreeRun(): Boolean {
            if (!startFreeRunResult) {
                return false
            }
            mutableState.value = ActivityTrackerState(
                sessionId = "free-run",
                isTracking = true,
            )
            return true
        }

        override suspend fun startPlannedWorkout(workout: com.vladislav.runningapp.training.domain.Workout): Boolean =
            true

        override fun stopActiveSession() {
            mutableState.value = ActivityTrackerState()
        }
    }

    private class FixedTrackingPermissionChecker(
        private val canStartTrackedSessions: Boolean,
    ) : TrackingPermissionChecker {
        override fun currentState(): PermissionRequirementsState = PermissionRequirementsState(
            location = if (canStartTrackedSessions) {
                RequirementState.Available
            } else {
                RequirementState.Missing
            },
            notifications = RequirementState.Available,
            foregroundTracking = RequirementState.Available,
        )
    }
}
