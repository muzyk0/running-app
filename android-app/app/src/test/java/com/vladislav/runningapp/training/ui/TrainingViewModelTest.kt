package com.vladislav.runningapp.training.ui

import com.vladislav.runningapp.activity.ActivitySessionType
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.activity.TrackedSessionStartFailureMessage
import com.vladislav.runningapp.core.permissions.MissingTrackedSessionPermissionsMessage
import com.vladislav.runningapp.core.permissions.PermissionRequirementsState
import com.vladislav.runningapp.core.permissions.RequirementState
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import com.vladislav.runningapp.training.WorkoutRepository
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TrainingViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun startSelectedWorkoutRejectsStartWhenAnotherSessionIsActive() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker(
            initialState = ActivityTrackerState(
                sessionId = "active-session",
                type = ActivitySessionType.FreeRun,
                startedAtEpochMs = 1L,
                isTracking = true,
            ),
        )
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(workouts = listOf(sampleWorkout())),
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartSelectedWorkout()
        assertTrue(tracker.startedWorkouts.isEmpty())
        assertEquals(
            "Сначала завершите текущую активную сессию, затем запускайте сохраненную тренировку.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun saveWorkoutRestoresEditorStateWhenRepositoryWriteFails() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(saveError = IllegalStateException("disk full")),
            activityTracker = FakeActivityTracker(),
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onCreateWorkout()
        viewModel.onTitleChanged("Интервалы")
        viewModel.onStepDurationChanged(index = 0, value = "180")
        viewModel.onStepVoicePromptChanged(index = 0, value = "Разминка.")
        viewModel.onSaveWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val editorState = viewModel.uiState.value.editorState
        assertNotNull(editorState)
        assertFalse(requireNotNull(editorState).isSaving)
        assertEquals(
            "Не удалось сохранить тренировку локально. Повторите попытку.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun saveCopySurfacesRepositoryFailure() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(
                workouts = listOf(sampleWorkout()),
                saveError = IllegalStateException("disk full"),
            ),
            activityTracker = FakeActivityTracker(),
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onSaveCopyOfSelectedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Не удалось создать копию тренировки локально. Повторите попытку.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun startSelectedWorkoutSurfacesPermissionErrorWhenTrackingPermissionsAreMissing() =
        runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker()
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(workouts = listOf(sampleWorkout())),
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = false),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onStartSelectedWorkout()
        assertTrue(tracker.startedWorkouts.isEmpty())
        assertEquals(
            MissingTrackedSessionPermissionsMessage,
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun startSelectedWorkoutSurfacesTrackerStartFailureWithoutOpenSignal() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker(startPlannedWorkoutResult = false)
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(workouts = listOf(sampleWorkout())),
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onStartSelectedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(tracker.startedWorkouts.isEmpty())
        assertEquals(TrackedSessionStartFailureMessage, viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.shouldOpenActiveSession)
        assertFalse(viewModel.uiState.value.isStartingWorkout)
    }

    @Test
    fun startSelectedWorkoutEmitsOpenSignalOnlyAfterTrackerStarts() = runTest(mainDispatcherRule.dispatcher) {
        val tracker = FakeActivityTracker()
        val viewModel = TrainingViewModel(
            workoutRepository = FakeWorkoutRepository(workouts = listOf(sampleWorkout())),
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onStartSelectedWorkout()

        assertFalse(viewModel.uiState.value.shouldOpenActiveSession)

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, tracker.startedWorkouts.size)
        assertTrue(viewModel.uiState.value.shouldOpenActiveSession)
        assertFalse(viewModel.uiState.value.isStartingWorkout)

        viewModel.onActiveSessionOpened()

        assertFalse(viewModel.uiState.value.shouldOpenActiveSession)
    }

    @Test
    fun confirmDeleteWorkoutRejectsCurrentlyActiveWorkout() = runTest(mainDispatcherRule.dispatcher) {
        val workout = sampleWorkout()
        val tracker = FakeActivityTracker()
        val repository = FakeWorkoutRepository(workouts = listOf(workout))
        val viewModel = TrainingViewModel(
            workoutRepository = repository,
            activityTracker = tracker,
            trackingPermissionChecker = FixedTrackingPermissionChecker(canStartTrackedSessions = true),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onRequestDeleteSelectedWorkout()
        tracker.updateState(
            ActivityTrackerState(
                sessionId = "planned-session",
                type = ActivitySessionType.PlannedWorkout,
                startedAtEpochMs = 1L,
                workoutId = workout.id,
                workoutTitle = workout.title,
                isTracking = true,
            ),
        )

        viewModel.onConfirmDeleteWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(repository.deletedWorkoutIds.isEmpty())
        assertEquals(
            "Нельзя удалить тренировку, пока она запущена. Сначала завершите активную сессию.",
            viewModel.uiState.value.errorMessage,
        )
        assertEquals(null, viewModel.uiState.value.pendingDeleteWorkoutId)
        assertEquals(workout.id, viewModel.uiState.value.selectedWorkoutId)
    }

    private class FakeWorkoutRepository(
        workouts: List<Workout> = emptyList(),
        private val saveError: Throwable? = null,
    ) : WorkoutRepository {
        private val state = MutableStateFlow(workouts)
        val deletedWorkoutIds = mutableListOf<String>()

        override fun observeWorkouts(): Flow<List<Workout>> = state

        override fun observeWorkout(workoutId: String): Flow<Workout?> = MutableStateFlow(
            state.value.firstOrNull { workout -> workout.id == workoutId },
        )

        override suspend fun getWorkout(workoutId: String): Workout? =
            state.value.firstOrNull { workout -> workout.id == workoutId }

        override suspend fun saveWorkout(workout: Workout) {
            saveError?.let { throw it }
            state.value = state.value.filterNot { existing -> existing.id == workout.id } + workout
        }

        override suspend fun deleteWorkout(workoutId: String) {
            deletedWorkoutIds += workoutId
            state.value = state.value.filterNot { workout -> workout.id == workoutId }
        }
    }

    private class FakeActivityTracker(
        initialState: ActivityTrackerState = ActivityTrackerState(),
        private val startPlannedWorkoutResult: Boolean = true,
    ) : ActivityTracker {
        private val mutableState = MutableStateFlow(initialState)
        val startedWorkouts = mutableListOf<Workout>()

        override val trackerState: StateFlow<ActivityTrackerState> = mutableState

        override suspend fun startFreeRun(): Boolean = true

        override suspend fun startPlannedWorkout(workout: Workout): Boolean {
            if (!startPlannedWorkoutResult) {
                return false
            }
            startedWorkouts += workout
            mutableState.value = ActivityTrackerState(
                sessionId = "planned-session",
                type = ActivitySessionType.PlannedWorkout,
                startedAtEpochMs = 1L,
                workoutId = workout.id,
                workoutTitle = workout.title,
                isTracking = true,
            )
            return true
        }

        override fun stopActiveSession() {
            mutableState.value = ActivityTrackerState()
        }

        fun updateState(state: ActivityTrackerState) {
            mutableState.value = state
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
    schemaVersion = DefaultWorkoutSchemaVersion,
    title = "Интервалы для старта",
    summary = null,
    goal = null,
    disclaimer = null,
    steps = listOf(
        WorkoutStep(
            type = WorkoutStepType.Warmup,
            durationSec = 180,
            voicePrompt = "Разминка.",
        ),
    ),
)
