package com.vladislav.runningapp.activity

import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import com.vladislav.runningapp.activity.service.LocationUpdatesSession
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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultActivityTrackerTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun stopActiveSessionClearsTrackerStateWhenPersistenceFails() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController()
        val repository = RecordingActivityRepository(failOnSave = true)
        val locationUpdatesClient = FakeLocationUpdatesClient()
        val tracker = DefaultActivityTracker(
            activityRepository = repository,
            locationUpdatesClient = locationUpdatesClient,
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
        assertEquals(1, locationUpdatesClient.openedSessions.single().closeCalls)
        assertTrue(repository.savedSessions.isEmpty())
    }

    @Test
    fun startFreeRunDoesNothingWhenTrackingPermissionsAreMissing() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController()
        val locationUpdatesClient = FakeLocationUpdatesClient()
        val tracker = DefaultActivityTracker(
            activityRepository = RecordingActivityRepository(),
            locationUpdatesClient = locationUpdatesClient,
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = false),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(tracker.trackerState.value.isTracking)
        assertEquals(0, serviceController.startCalls)
        assertEquals(0, locationUpdatesClient.openCalls)
    }

    @Test
    fun startFreeRunDoesNotActivateSessionWhenLocationStartupFails() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController()
        val locationUpdatesClient = FakeLocationUpdatesClient(openError = IllegalStateException("gps unavailable"))
        val tracker = DefaultActivityTracker(
            activityRepository = RecordingActivityRepository(),
            locationUpdatesClient = locationUpdatesClient,
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertFalse(tracker.trackerState.value.hasSession)
        assertFalse(tracker.trackerState.value.isTracking)
        assertEquals(1, locationUpdatesClient.openCalls)
        assertEquals(0, serviceController.startCalls)
        assertTrue(locationUpdatesClient.openedSessions.isEmpty())
    }

    @Test
    fun startFreeRunRollsBackTrackingWhenForegroundServiceStartFails() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController(
            startError = IllegalStateException("service rejected"),
        )
        val locationUpdatesClient = FakeLocationUpdatesClient()
        val tracker = DefaultActivityTracker(
            activityRepository = RecordingActivityRepository(),
            locationUpdatesClient = locationUpdatesClient,
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        val started = tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(started)
        assertFalse(tracker.trackerState.value.hasSession)
        assertFalse(tracker.trackerState.value.isTracking)
        assertEquals(1, serviceController.startCalls)
        assertEquals(1, serviceController.stopCalls)
        assertEquals(1, locationUpdatesClient.openedSessions.single().closeCalls)
    }

    @Test
    fun startPlannedWorkoutRollsBackTrackingWhenForegroundServiceStartFails() = runTest(mainDispatcherRule.dispatcher) {
        val serviceController = FakeActiveSessionServiceController(
            startError = IllegalStateException("service rejected"),
        )
        val locationUpdatesClient = FakeLocationUpdatesClient()
        val workoutSessionController = FakeWorkoutSessionController()
        val tracker = DefaultActivityTracker(
            activityRepository = RecordingActivityRepository(),
            locationUpdatesClient = locationUpdatesClient,
            workoutSessionController = workoutSessionController,
            activeSessionServiceController = serviceController,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        val started = tracker.startPlannedWorkout(sampleWorkout())
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(started)
        assertFalse(tracker.trackerState.value.hasSession)
        assertFalse(tracker.trackerState.value.isTracking)
        assertEquals(1, serviceController.startCalls)
        assertEquals(1, serviceController.stopCalls)
        assertEquals(1, workoutSessionController.stopCalls)
        assertEquals(1, locationUpdatesClient.openedSessions.single().closeCalls)
    }

    @Test
    fun pausedPlannedWorkoutIgnoresMovementUntilResumeBaselineIsReset() = runTest(mainDispatcherRule.dispatcher) {
        val locationUpdates = MutableSharedFlow<ActivityRoutePoint>(extraBufferCapacity = 8)
        val workoutSessionController = FakeWorkoutSessionController()
        val tracker = DefaultActivityTracker(
            activityRepository = RecordingActivityRepository(),
            locationUpdatesClient = FakeLocationUpdatesClient(locationUpdates = locationUpdates),
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

    @Test
    fun stopActiveSessionFreezesFreeRunSnapshotBeforeLocationShutdownCompletes() = runTest(mainDispatcherRule.dispatcher) {
        val locationUpdates = MutableSharedFlow<ActivityRoutePoint>(extraBufferCapacity = 8)
        val closeGate = CompletableDeferred<Unit>()
        val repository = RecordingActivityRepository()
        val locationUpdatesClient = FakeLocationUpdatesClient(
            locationUpdates = locationUpdates,
            closeGate = closeGate,
        )
        val tracker = DefaultActivityTracker(
            activityRepository = repository,
            locationUpdatesClient = locationUpdatesClient,
            workoutSessionController = FakeWorkoutSessionController(),
            activeSessionServiceController = FakeActiveSessionServiceController(),
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startFreeRun()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        locationUpdates.tryEmit(point(latitude = 55.751244, longitude = 37.618423, recordedAtEpochMs = 1_000L))
        mainDispatcherRule.dispatcher.scheduler.runCurrent()
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_000L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        tracker.stopActiveSession()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        locationUpdates.tryEmit(point(latitude = 55.752244, longitude = 37.628423, recordedAtEpochMs = 8_000L))
        mainDispatcherRule.dispatcher.scheduler.advanceTimeBy(1_000L)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        closeGate.complete(Unit)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.saveCalls)
        assertEquals(1, repository.savedSessions.single().durationSec)
        assertEquals(1, repository.savedSessions.single().routePoints.size)
    }

    @Test
    fun stopActiveSessionUsesFrozenWorkoutStateWhileCleanupIsSuspended() = runTest(mainDispatcherRule.dispatcher) {
        val workoutSessionController = FakeWorkoutSessionController()
        val workout = sampleWorkout()
        val closeGate = CompletableDeferred<Unit>()
        val repository = RecordingActivityRepository()
        val tracker = DefaultActivityTracker(
            activityRepository = repository,
            locationUpdatesClient = FakeLocationUpdatesClient(closeGate = closeGate),
            workoutSessionController = workoutSessionController,
            activeSessionServiceController = FakeActiveSessionServiceController(),
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        tracker.startPlannedWorkout(workout)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        workoutSessionController.emitState(
            WorkoutSessionState(
                status = WorkoutSessionStatus.Running,
                workout = workout,
                totalElapsedSec = 7,
            ),
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        tracker.stopActiveSession()
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        workoutSessionController.emitState(
            WorkoutSessionState(
                status = WorkoutSessionStatus.Running,
                workout = workout,
                totalElapsedSec = 8,
            ),
        )
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        closeGate.complete(Unit)
        mainDispatcherRule.dispatcher.scheduler.runCurrent()

        assertEquals(1, repository.saveCalls)
        assertEquals(7, repository.savedSessions.single().durationSec)
    }

    @Test
    fun stopActiveSessionUsesWorkoutControllerFinalElapsedWhenManualStopCapturesLaterTick() =
        runTest(mainDispatcherRule.dispatcher) {
            val workout = sampleWorkout()
            val repository = RecordingActivityRepository()
            val workoutSessionController = FakeWorkoutSessionController().apply {
                elapsedIncrementBeforeStop = 1
            }
            val tracker = DefaultActivityTracker(
                activityRepository = repository,
                locationUpdatesClient = FakeLocationUpdatesClient(),
                workoutSessionController = workoutSessionController,
                activeSessionServiceController = FakeActiveSessionServiceController(),
                trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
                defaultDispatcher = mainDispatcherRule.dispatcher,
            )

            tracker.startPlannedWorkout(workout)
            mainDispatcherRule.dispatcher.scheduler.runCurrent()

            workoutSessionController.emitState(
                WorkoutSessionState(
                    status = WorkoutSessionStatus.Running,
                    workout = workout,
                    totalElapsedSec = 7,
                ),
            )
            mainDispatcherRule.dispatcher.scheduler.runCurrent()

            tracker.stopActiveSession()
            mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

            assertEquals(1, repository.saveCalls)
            assertEquals(8, repository.savedSessions.single().durationSec)
        }

    private class RecordingActivityRepository(
        private val failOnSave: Boolean = false,
        private val onSave: suspend (CompletedActivitySession) -> Unit = {},
    ) : ActivityRepository {
        var saveCalls: Int = 0
        val savedSessions = mutableListOf<CompletedActivitySession>()

        override fun observeCompletedSessions(): Flow<List<CompletedActivitySession>> = emptyFlow()

        override suspend fun saveCompletedSession(session: CompletedActivitySession) {
            saveCalls += 1
            if (failOnSave) {
                throw IllegalStateException("disk full")
            }
            onSave(session)
            savedSessions += session
        }
    }

    private class FakeLocationUpdatesClient(
        private val locationUpdates: Flow<ActivityRoutePoint> = emptyFlow(),
        private val openError: Throwable? = null,
        private val closeGate: CompletableDeferred<Unit>? = null,
    ) : LocationUpdatesClient {
        var openCalls: Int = 0
        val openedSessions = mutableListOf<FakeLocationUpdatesSession>()

        override suspend fun openSession(): LocationUpdatesSession {
            openCalls += 1
            openError?.let { throw it }
            return FakeLocationUpdatesSession(
                updates = locationUpdates,
                closeGate = closeGate,
            ).also { session ->
                openedSessions += session
            }
        }
    }

    private class FakeLocationUpdatesSession(
        override val updates: Flow<ActivityRoutePoint>,
        private val closeGate: CompletableDeferred<Unit>? = null,
    ) : LocationUpdatesSession {
        var closeCalls: Int = 0

        override suspend fun close() {
            closeCalls += 1
            closeGate?.await()
        }
    }

    private class FakeWorkoutSessionController : WorkoutSessionController {
        private val mutableState = MutableStateFlow(WorkoutSessionState())
        var elapsedIncrementBeforeStop: Int = 0
        var stopCalls: Int = 0

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

        override fun stopWorkout(): WorkoutSessionState {
            stopCalls += 1
            val finalState = mutableState.value
            val adjustedFinalState = if (elapsedIncrementBeforeStop > 0 && finalState.workout != null) {
                finalState.copy(totalElapsedSec = finalState.totalElapsedSec + elapsedIncrementBeforeStop)
            } else {
                finalState
            }
            mutableState.value = WorkoutSessionState()
            return adjustedFinalState
        }

        fun emitState(state: WorkoutSessionState) {
            mutableState.value = state
        }
    }

    private class FakeActiveSessionServiceController(
        private val startError: Throwable? = null,
    ) : ActiveSessionServiceController {
        var startCalls: Int = 0
        var stopCalls: Int = 0

        override fun start() {
            startCalls += 1
            startError?.let { throw it }
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
