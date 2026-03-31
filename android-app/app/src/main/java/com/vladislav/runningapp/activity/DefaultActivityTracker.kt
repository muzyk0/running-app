package com.vladislav.runningapp.activity

import android.util.Log
import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.ActivityDistanceCalculator
import com.vladislav.runningapp.activity.service.ActivityPointFilter
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import com.vladislav.runningapp.activity.service.LocationUpdatesSession
import com.vladislav.runningapp.activity.service.calculateAveragePaceSecPerKm
import com.vladislav.runningapp.core.di.DefaultDispatcher
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionStatus
import com.vladislav.runningapp.session.WorkoutSessionState
import com.vladislav.runningapp.training.domain.Workout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val ActivityTrackerLogTag = "DefaultActivityTracker"

@Singleton
class DefaultActivityTracker @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val locationUpdatesClient: LocationUpdatesClient,
    private val workoutSessionController: WorkoutSessionController,
    private val activeSessionServiceController: ActiveSessionServiceController,
    private val trackingPermissionChecker: TrackingPermissionChecker,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ActivityTracker {
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val mutationMutex = Mutex()
    private val mutableTrackerState = MutableStateFlow(ActivityTrackerState())
    private var isFinishingSession = false
    private var locationSession: LocationUpdatesSession? = null
    private var locationJob: Job? = null
    private var freeRunTickerJob: Job? = null

    override val trackerState = mutableTrackerState.asStateFlow()

    init {
        scope.launch {
            workoutSessionController.sessionState.collectLatest { workoutState ->
                handleWorkoutStateUpdate(
                    status = workoutState.status,
                    workoutId = workoutState.workout?.id,
                    durationSec = workoutState.totalElapsedSec,
                )
            }
        }
    }

    override suspend fun startFreeRun(): Boolean {
        if (!trackingPermissionChecker.currentState().canStartTrackedSessions) {
            return false
        }
        val failedStart = mutationMutex.withLock {
            if (mutableTrackerState.value.isTracking || isFinishingSession) {
                return@withLock null
            }
            val openedLocationSession = runCatching {
                locationUpdatesClient.openSession()
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                Log.e(ActivityTrackerLogTag, "Failed to start free-run location updates.", error)
                return@withLock FailedStartResult.NotStarted
            }
            mutableTrackerState.value = ActivityTrackerState(
                sessionId = UUID.randomUUID().toString(),
                type = ActivitySessionType.FreeRun,
                startedAtEpochMs = System.currentTimeMillis(),
                isTracking = true,
            )
            startLocationCollectionLocked(openedLocationSession)
            startFreeRunTickerLocked()
            runCatching {
                activeSessionServiceController.start()
            }.fold(
                onSuccess = { FailedStartResult.Started },
                onFailure = { error ->
                    Log.e(ActivityTrackerLogTag, "Failed to finish free-run startup.", error)
                    FailedStartResult.Rollback(
                        detachFailedStartCleanupLocked(stopWorkoutController = false),
                    )
                },
            )
        }
        return completeStartResult(failedStart)
    }

    override suspend fun startPlannedWorkout(workout: Workout): Boolean {
        if (!trackingPermissionChecker.currentState().canStartTrackedSessions) {
            return false
        }
        val failedStart = mutationMutex.withLock {
            if (mutableTrackerState.value.isTracking || isFinishingSession) {
                return@withLock null
            }
            val openedLocationSession = runCatching {
                locationUpdatesClient.openSession()
            }.getOrElse { error ->
                if (error is CancellationException) {
                    throw error
                }
                Log.e(ActivityTrackerLogTag, "Failed to start planned-workout location updates.", error)
                return@withLock FailedStartResult.NotStarted
            }
            mutableTrackerState.value = ActivityTrackerState(
                sessionId = UUID.randomUUID().toString(),
                type = ActivitySessionType.PlannedWorkout,
                startedAtEpochMs = System.currentTimeMillis(),
                workoutId = workout.id,
                workoutTitle = workout.title,
                isTracking = true,
            )
            cancelFreeRunTickerLocked()
            startLocationCollectionLocked(openedLocationSession)
            runCatching {
                workoutSessionController.startWorkout(workout)
                activeSessionServiceController.start()
            }.fold(
                onSuccess = { FailedStartResult.Started },
                onFailure = { error ->
                    Log.e(ActivityTrackerLogTag, "Failed to finish planned-workout startup.", error)
                    FailedStartResult.Rollback(
                        detachFailedStartCleanupLocked(stopWorkoutController = true),
                    )
                },
            )
        }
        return completeStartResult(failedStart)
    }

    override fun stopActiveSession() {
        scope.launch {
            val stopWorkoutController = mutationMutex.withLock {
                val currentState = mutableTrackerState.value
                if (!currentState.hasSession) {
                    null
                } else {
                    currentState.isPlannedWorkout
                }
            }
            if (stopWorkoutController == null) {
                workoutSessionController.stopWorkout()
                return@launch
            }

            finishSession(
                retainCompletedState = false,
                stopWorkoutController = stopWorkoutController,
            )
        }
    }

    private suspend fun handleWorkoutStateUpdate(
        status: WorkoutSessionStatus,
        workoutId: String?,
        durationSec: Int,
    ) {
        val shouldFinalize = mutationMutex.withLock {
            val currentState = mutableTrackerState.value
            if (
                !currentState.isPlannedWorkout ||
                currentState.workoutId != workoutId ||
                !currentState.isTracking ||
                isFinishingSession
            ) {
                return@withLock false
            }
            val updatedState = currentState.copy(
                durationSec = durationSec,
                averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                    durationSec = durationSec,
                    distanceMeters = currentState.distanceMeters,
                ),
                isPaused = status == WorkoutSessionStatus.Paused,
                needsLocationBaselineReset = currentState.needsLocationBaselineReset ||
                    (currentState.isPaused && status == WorkoutSessionStatus.Running),
            )
            mutableTrackerState.value = updatedState
            status == WorkoutSessionStatus.Completed
        }

        if (shouldFinalize) {
            finishSession(
                retainCompletedState = true,
                stopWorkoutController = false,
            )
        }
    }

    private suspend fun finishSession(
        retainCompletedState: Boolean,
        stopWorkoutController: Boolean,
    ) {
        val detachedTrackingResources = mutationMutex.withLock {
            val currentState = mutableTrackerState.value
            if (!currentState.hasSession || isFinishingSession) {
                return@withLock null
            }
            isFinishingSession = true
            val snapshot = currentSnapshotForPersistenceLocked(
                workoutState = if (currentState.isPlannedWorkout && stopWorkoutController) {
                    workoutSessionController.stopWorkout()
                } else {
                    null
                },
            )
            mutableTrackerState.value = snapshot.copy(isTracking = false)
            val activeLocationSession = locationSession
            locationSession = null
            val activeLocationJob = locationJob
            locationJob = null
            val activeFreeRunTickerJob = freeRunTickerJob
            freeRunTickerJob = null
            activeSessionServiceController.stop()
            DetachedTrackingResources(
                snapshot = snapshot,
                locationSession = activeLocationSession,
                locationJob = activeLocationJob,
                freeRunTickerJob = activeFreeRunTickerJob,
            )
        }
        if (detachedTrackingResources == null) {
            return
        }

        var nextTrackerState = ActivityTrackerState()
        try {
            runCatching {
                detachedTrackingResources.locationSession?.close()
            }.onFailure { error ->
                Log.e(ActivityTrackerLogTag, "Failed to stop location updates cleanly.", error)
            }
            detachedTrackingResources.locationJob?.cancelAndJoin()
            detachedTrackingResources.freeRunTickerJob?.cancelAndJoin()
            val snapshot = detachedTrackingResources.snapshot
            var sessionPersisted = snapshot.isPersisted
            if (!snapshot.isPersisted) {
                sessionPersisted = runCatching {
                    activityRepository.saveCompletedSession(
                        CompletedActivitySession(
                            id = requireNotNull(snapshot.sessionId),
                            type = requireNotNull(snapshot.type),
                            workoutId = snapshot.workoutId,
                            workoutTitle = snapshot.workoutTitle,
                            startedAtEpochMs = requireNotNull(snapshot.startedAtEpochMs),
                            completedAtEpochMs = System.currentTimeMillis(),
                            durationSec = snapshot.durationSec,
                            distanceMeters = snapshot.distanceMeters,
                            averagePaceSecPerKm = snapshot.averagePaceSecPerKm,
                            routePoints = snapshot.routePoints,
                        ),
                    )
                    true
                }.getOrElse { error ->
                    Log.e(ActivityTrackerLogTag, "Failed to persist completed activity session.", error)
                    false
                }
            }

            nextTrackerState = if (retainCompletedState && snapshot.isPlannedWorkout && sessionPersisted) {
                snapshot.copy(
                    isTracking = false,
                    isPaused = false,
                    needsLocationBaselineReset = false,
                    isCompleted = true,
                    isPersisted = sessionPersisted,
                )
            } else {
                ActivityTrackerState()
            }
        } finally {
            mutationMutex.withLock {
                isFinishingSession = false
                mutableTrackerState.value = nextTrackerState
            }
        }
    }

    private suspend fun completeStartResult(result: FailedStartResult?): Boolean {
        return when (result) {
            null, FailedStartResult.NotStarted -> false
            FailedStartResult.Started -> true
            is FailedStartResult.Rollback -> {
                cleanupFailedStart(result.cleanup)
                false
            }
        }
    }

    private fun detachFailedStartCleanupLocked(
        stopWorkoutController: Boolean,
    ): FailedStartCleanup {
        val activeLocationSession = locationSession
        locationSession = null
        val activeLocationJob = locationJob
        locationJob = null
        val activeFreeRunTickerJob = freeRunTickerJob
        freeRunTickerJob = null
        mutableTrackerState.value = ActivityTrackerState()
        return FailedStartCleanup(
            locationSession = activeLocationSession,
            locationJob = activeLocationJob,
            freeRunTickerJob = activeFreeRunTickerJob,
            stopWorkoutController = stopWorkoutController,
        )
    }

    private suspend fun cleanupFailedStart(cleanup: FailedStartCleanup) {
        withContext(NonCancellable) {
            if (cleanup.stopWorkoutController) {
                runCatching {
                    workoutSessionController.stopWorkout()
                }.onFailure { error ->
                    Log.e(ActivityTrackerLogTag, "Failed to rollback workout session after startup error.", error)
                }
            }
            runCatching {
                activeSessionServiceController.stop()
            }.onFailure { error ->
                Log.e(ActivityTrackerLogTag, "Failed to stop foreground service after startup error.", error)
            }
            runCatching {
                cleanup.locationSession?.close()
            }.onFailure { error ->
                Log.e(ActivityTrackerLogTag, "Failed to close location session after startup error.", error)
            }
            cleanup.locationJob?.cancelAndJoin()
            cleanup.freeRunTickerJob?.cancelAndJoin()
        }
    }

    private fun startLocationCollectionLocked(openedLocationSession: LocationUpdatesSession) {
        locationSession = openedLocationSession
        locationJob = scope.launch {
            openedLocationSession.updates.collectLatest { candidatePoint ->
                mutationMutex.withLock {
                    val currentState = mutableTrackerState.value
                    if (!currentState.isTracking || currentState.isPaused) {
                        return@withLock
                    }
                    val previousPoint = currentState.routePoints.lastOrNull()
                    val filterReferencePoint = if (currentState.needsLocationBaselineReset) {
                        null
                    } else {
                        previousPoint
                    }
                    if (!ActivityPointFilter.shouldAccept(filterReferencePoint, candidatePoint)) {
                        return@withLock
                    }

                    val distanceIncrement = if (
                        currentState.needsLocationBaselineReset ||
                        previousPoint == null
                    ) {
                        0.0
                    } else {
                        ActivityDistanceCalculator.distanceMeters(previousPoint, candidatePoint)
                    }
                    val nextDistance = currentState.distanceMeters + distanceIncrement
                    mutableTrackerState.value = currentState.copy(
                        routePoints = currentState.routePoints + candidatePoint,
                        distanceMeters = nextDistance,
                        averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                            durationSec = currentState.durationSec,
                            distanceMeters = nextDistance,
                        ),
                        needsLocationBaselineReset = false,
                    )
                }
            }
        }
    }

    private fun currentSnapshotForPersistenceLocked(
        workoutState: WorkoutSessionState? = null,
    ): ActivityTrackerState {
        val currentState = mutableTrackerState.value
        if (!currentState.isPlannedWorkout) {
            return currentState
        }

        val resolvedWorkoutState = workoutState ?: workoutSessionController.sessionState.value
        if (currentState.workoutId != resolvedWorkoutState.workout?.id) {
            return currentState
        }

        val refreshedState = currentState.copy(
            durationSec = resolvedWorkoutState.totalElapsedSec,
            averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                durationSec = resolvedWorkoutState.totalElapsedSec,
                distanceMeters = currentState.distanceMeters,
            ),
            isPaused = resolvedWorkoutState.status == WorkoutSessionStatus.Paused,
            needsLocationBaselineReset = currentState.needsLocationBaselineReset ||
                (currentState.isPaused && resolvedWorkoutState.status == WorkoutSessionStatus.Running),
        )
        mutableTrackerState.value = refreshedState
        return refreshedState
    }

    private fun startFreeRunTickerLocked() {
        cancelFreeRunTickerLocked()
        freeRunTickerJob = scope.launch {
            while (isActive) {
                delay(1_000L)
                mutationMutex.withLock {
                    val currentState = mutableTrackerState.value
                    if (!currentState.isTracking || !currentState.isFreeRun) {
                        return@withLock
                    }
                    val nextDurationSec = currentState.durationSec + 1
                    mutableTrackerState.value = currentState.copy(
                        durationSec = nextDurationSec,
                        averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                            durationSec = nextDurationSec,
                            distanceMeters = currentState.distanceMeters,
                        ),
                    )
                }
            }
        }
    }

    private fun cancelFreeRunTickerLocked() {
        freeRunTickerJob?.cancel()
        freeRunTickerJob = null
    }
}

private data class DetachedTrackingResources(
    val snapshot: ActivityTrackerState,
    val locationSession: LocationUpdatesSession?,
    val locationJob: Job?,
    val freeRunTickerJob: Job?,
)

private sealed interface FailedStartResult {
    data object NotStarted : FailedStartResult

    data object Started : FailedStartResult

    data class Rollback(
        val cleanup: FailedStartCleanup,
    ) : FailedStartResult
}

private data class FailedStartCleanup(
    val locationSession: LocationUpdatesSession?,
    val locationJob: Job?,
    val freeRunTickerJob: Job?,
    val stopWorkoutController: Boolean,
)
