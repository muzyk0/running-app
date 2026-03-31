package com.vladislav.runningapp.activity

import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import com.vladislav.runningapp.core.permissions.PermissionRequirementsState
import com.vladislav.runningapp.core.permissions.RequirementState
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionStatus
import com.vladislav.runningapp.session.WorkoutSessionState
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DefaultActivityTrackerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun stopActiveSessionClearsTrackerStateWhenPersistenceFails() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController()
        val tracker = DefaultActivityTracker(
            activityRepository = object : ActivityRepository {
                override fun observeCompletedSessions(): Flow<List<CompletedActivitySession>> = emptyFlow()

                override suspend fun saveCompletedSession(session: CompletedActivitySession) {
                    throw IllegalStateException("disk full")
                }
            },
            locationUpdatesClient = object : LocationUpdatesClient {
                override fun locationUpdates(): Flow<ActivityRoutePoint> = emptyFlow()
            },
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        assertTrue(tracker.trackerState.value.isTracking)

        tracker.stopActiveSession()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(tracker.trackerState.value.hasSession)
        assertFalse(tracker.trackerState.value.isTracking)
        assertNull(tracker.trackerState.value.sessionId)
        assertEquals(1, serviceController.startCalls)
        assertEquals(1, serviceController.stopCalls)
    }

    @Test
    fun startFreeRunDoesNothingWhenTrackingPermissionsAreMissing() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController()
        val tracker = DefaultActivityTracker(
            activityRepository = object : ActivityRepository {
                override fun observeCompletedSessions(): Flow<List<CompletedActivitySession>> = emptyFlow()

                override suspend fun saveCompletedSession(session: CompletedActivitySession) = Unit
            },
            locationUpdatesClient = object : LocationUpdatesClient {
                override fun locationUpdates(): Flow<ActivityRoutePoint> = emptyFlow()
            },
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = false),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(tracker.trackerState.value.isTracking)
        assertEquals(0, serviceController.startCalls)
    }

    @Test
    fun pausedPlannedWorkoutIgnoresMovementUntilResumeBaselineIsReset() = runTest(mainDispatcherRule.dispatcher) {
        val locationUpdates = MutableSharedFlow<ActivityRoutePoint>(extraBufferCapacity = 8)
        val workoutSessionController = FakeWorkoutSessionController()
        val tracker = DefaultActivityTracker(
            activityRepository = object : ActivityRepository {
                override fun observeCompletedSessions(): Flow<List<CompletedActivitySession>> = emptyFlow()

                override suspend fun saveCompletedSession(session: CompletedActivitySession) = Unit
            },
            locationUpdatesClient = object : LocationUpdatesClient {
                override fun locationUpdates(): Flow<ActivityRoutePoint> = locationUpdates
            },
            workoutSessionController = workoutSessionController,
            activeSessionServiceController = FakeActiveSessionServiceController(),
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startPlannedWorkout(sampleWorkout())
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        locationUpdates.tryEmit(point(latitude = 55.751244, longitude = 37.618423, recordedAtEpochMs = 1_000L))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, tracker.trackerState.value.routePoints.size)
        assertEquals(0.0, tracker.trackerState.value.distanceMeters, 0.001)

        workoutSessionController.pauseWorkout()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        locationUpdates.tryEmit(point(latitude = 55.752244, longitude = 37.628423, recordedAtEpochMs = 6_000L))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertTrue(tracker.trackerState.value.isPaused)
        assertEquals(1, tracker.trackerState.value.routePoints.size)
        assertEquals(0.0, tracker.trackerState.value.distanceMeters, 0.001)

        workoutSessionController.resumeWorkout()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        locationUpdates.tryEmit(point(latitude = 55.752244, longitude = 37.628423, recordedAtEpochMs = 9_000L))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(tracker.trackerState.value.isPaused)
        assertEquals(2, tracker.trackerState.value.routePoints.size)
        assertEquals(0.0, tracker.trackerState.value.distanceMeters, 0.001)

        locationUpdates.tryEmit(point(latitude = 55.752544, longitude = 37.629023, recordedAtEpochMs = 13_000L))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(3, tracker.trackerState.value.routePoints.size)
        assertTrue(tracker.trackerState.value.distanceMeters > 0.0)
    }

    private class FakeWorkoutSessionController : WorkoutSessionController {
        private val mutableState = MutableStateFlow(WorkoutSessionState())

        override val sessionState: StateFlow<WorkoutSessionState> = mutableState

        override fun startWorkout(workout: Workout) {
            mutableState.value = WorkoutSessionState(
                status = WorkoutSessionStatus.Running,
                workout = workout,
            )
        }

        override fun pauseWorkout() {
            mutableState.value = mutableState.value.copy(status = WorkoutSessionStatus.Paused)
        }

        override fun resumeWorkout() {
            mutableState.value = mutableState.value.copy(status = WorkoutSessionStatus.Running)
        }

        override fun stopWorkout() {
            mutableState.value = WorkoutSessionState()
        }
    }

    private class FakeActiveSessionServiceController : ActiveSessionServiceController {
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start() {
            startCalls += 1
        }

        override fun stop() {
            stopCalls += 1
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

private fun sampleWorkout(): Workout = Workout(
    id = "workout-1",
    schemaVersion = "mvp.v1",
    title = "Интервалы",
    summary = null,
    goal = null,
    disclaimer = null,
    steps = listOf(
        WorkoutStep(
            type = WorkoutStepType.Run,
            durationSec = 120,
            voicePrompt = "Бегите ровно.",
        ),
    ),
)

private fun point(
    latitude: Double,
    longitude: Double,
    recordedAtEpochMs: Long,
): ActivityRoutePoint = ActivityRoutePoint(
    latitude = latitude,
    longitude = longitude,
    recordedAtEpochMs = recordedAtEpochMs,
    accuracyMeters = 5f,
)
