package com.vladislav.runningapp.activity

import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionState
import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.Flow
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

    private class FakeWorkoutSessionController : WorkoutSessionController {
        private val mutableState = MutableStateFlow(WorkoutSessionState())

        override val sessionState: StateFlow<WorkoutSessionState> = mutableState

        override fun startWorkout(workout: Workout) = Unit

        override fun pauseWorkout() = Unit

        override fun resumeWorkout() = Unit

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
}
