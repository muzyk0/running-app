package com.vladislav.runningapp.activity

enum class ActivitySessionType(
    val rawValue: String,
) {
    PlannedWorkout("planned_workout"),
    FreeRun("free_run"),
    ;

    companion object {
        fun fromRawValue(value: String): ActivitySessionType = entries.firstOrNull { type ->
            type.rawValue == value
        } ?: FreeRun
    }
}

data class ActivityRoutePoint(
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMs: Long,
    val accuracyMeters: Float,
)

data class CompletedActivitySession(
    val id: String,
    val type: ActivitySessionType,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Int?,
    val workoutId: String? = null,
    val workoutTitle: String? = null,
    val routePoints: List<ActivityRoutePoint> = emptyList(),
)

data class ActivityTrackerState(
    val sessionId: String? = null,
    val type: ActivitySessionType? = null,
    val startedAtEpochMs: Long? = null,
    val durationSec: Int = 0,
    val distanceMeters: Double = 0.0,
    val averagePaceSecPerKm: Int? = null,
    val routePoints: List<ActivityRoutePoint> = emptyList(),
    val workoutId: String? = null,
    val workoutTitle: String? = null,
    val isTracking: Boolean = false,
    val isPaused: Boolean = false,
    val needsLocationBaselineReset: Boolean = false,
    val isCompleted: Boolean = false,
    val isPersisted: Boolean = false,
) {
    val hasSession: Boolean
        get() = sessionId != null && type != null

    val isFreeRun: Boolean
        get() = type == ActivitySessionType.FreeRun

    val isPlannedWorkout: Boolean
        get() = type == ActivitySessionType.PlannedWorkout
}
