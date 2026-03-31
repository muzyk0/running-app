package com.vladislav.runningapp.activity

import android.util.Log
import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.ActivityDistanceCalculator
import com.vladislav.runningapp.activity.service.ActivityPointFilter
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import com.vladislav.runningapp.activity.service.calculateAveragePaceSecPerKm
import com.vladislav.runningapp.core.di.DefaultDispatcher
import com.vladislav.runningapp.session.WorkoutSessionController
import com.vladislav.runningapp.session.WorkoutSessionStatus
import com.vladislav.runningapp.training.domain.Workout
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val ActivityTrackerLogTag = "DefaultActivityTracker"

@Singleton
class DefaultActivityTracker @Inject constructor(
    private val activityRepository: ActivityRepository,
    private val locationUpdatesClient: LocationUpdatesClient,
    private val workoutSessionController: WorkoutSessionController,
    private val activeSessionServiceController: ActiveSessionServiceController,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : ActivityTracker {
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val mutationMutex = Mutex()
    private val mutableTrackerState = MutableStateFlow(ActivityTrackerState())
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

    override fun startFreeRun() {
        scope.launch {
            mutationMutex.withLock {
                if (mutableTrackerState.value.isTracking) {
                    return@withLock
                }
                mutableTrackerState.value = ActivityTrackerState(
                    sessionId = UUID.randomUUID().toString(),
                    type = ActivitySessionType.FreeRun,
                    startedAtEpochMs = System.currentTimeMillis(),
                    isTracking = true,
                )
                startLocationCollectionLocked()
                startFreeRunTickerLocked()
                activeSessionServiceController.start()
            }
        }
    }

    override fun startPlannedWorkout(workout: Workout) {
        scope.launch {
            mutationMutex.withLock {
                if (mutableTrackerState.value.isTracking) {
                    return@withLock
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
                startLocationCollectionLocked()
                workoutSessionController.startWorkout(workout)
                activeSessionServiceController.start()
            }
        }
    }

    override fun stopActiveSession() {
        scope.launch {
            val snapshot = mutationMutex.withLock {
                mutableTrackerState.value
            }
            if (!snapshot.hasSession) {
                workoutSessionController.stopWorkout()
                return@launch
            }

            finishSession(
                snapshot = snapshot,
                retainCompletedState = false,
                stopWorkoutController = snapshot.isPlannedWorkout,
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
            if (!currentState.isPlannedWorkout || currentState.workoutId != workoutId) {
                return@withLock false
            }
            val updatedState = currentState.copy(
                durationSec = durationSec,
                averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                    durationSec = durationSec,
                    distanceMeters = currentState.distanceMeters,
                ),
            )
            mutableTrackerState.value = updatedState
            currentState.isTracking && status == WorkoutSessionStatus.Completed
        }

        if (shouldFinalize) {
            val snapshot = mutationMutex.withLock { mutableTrackerState.value }
            finishSession(
                snapshot = snapshot,
                retainCompletedState = true,
                stopWorkoutController = false,
            )
        }
    }

    private suspend fun finishSession(
        snapshot: ActivityTrackerState,
        retainCompletedState: Boolean,
        stopWorkoutController: Boolean,
    ) {
        mutationMutex.withLock {
            cancelLocationCollectionLocked()
            cancelFreeRunTickerLocked()
            activeSessionServiceController.stop()
        }

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

        if (stopWorkoutController) {
            workoutSessionController.stopWorkout()
        }

        mutationMutex.withLock {
            mutableTrackerState.value = if (retainCompletedState && snapshot.isPlannedWorkout && sessionPersisted) {
                snapshot.copy(
                    isTracking = false,
                    isCompleted = true,
                    isPersisted = sessionPersisted,
                )
            } else {
                ActivityTrackerState()
            }
        }
    }

    private fun startLocationCollectionLocked() {
        cancelLocationCollectionLocked()
        locationJob = scope.launch {
            locationUpdatesClient.locationUpdates().collectLatest { candidatePoint ->
                mutationMutex.withLock {
                    val currentState = mutableTrackerState.value
                    if (!currentState.isTracking) {
                        return@withLock
                    }
                    val previousPoint = currentState.routePoints.lastOrNull()
                    if (!ActivityPointFilter.shouldAccept(previousPoint, candidatePoint)) {
                        return@withLock
                    }

                    val distanceIncrement = previousPoint?.let { point ->
                        ActivityDistanceCalculator.distanceMeters(point, candidatePoint)
                    } ?: 0.0
                    val nextDistance = currentState.distanceMeters + distanceIncrement
                    mutableTrackerState.value = currentState.copy(
                        routePoints = currentState.routePoints + candidatePoint,
                        distanceMeters = nextDistance,
                        averagePaceSecPerKm = calculateAveragePaceSecPerKm(
                            durationSec = currentState.durationSec,
                            distanceMeters = nextDistance,
                        ),
                    )
                }
            }
        }
    }

    private fun cancelLocationCollectionLocked() {
        locationJob?.cancel()
        locationJob = null
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
