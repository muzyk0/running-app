package com.vladislav.runningapp.activity.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.vladislav.runningapp.activity.ActivityRoutePoint
import com.vladislav.runningapp.activity.ActivitySessionType
import com.vladislav.runningapp.activity.CompletedActivitySession
import com.vladislav.runningapp.training.data.local.WorkoutEntity

@Entity(
    tableName = "activity_sessions",
    foreignKeys = [
        ForeignKey(
            entity = WorkoutEntity::class,
            parentColumns = ["id"],
            childColumns = ["workoutId"],
            onDelete = ForeignKey.SET_NULL,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("completedAtEpochMs"),
        Index("workoutId"),
    ],
)
data class ActivitySessionEntity(
    @PrimaryKey val id: String,
    val type: String,
    val workoutId: String?,
    val workoutTitle: String?,
    val startedAtEpochMs: Long,
    val completedAtEpochMs: Long,
    val durationSec: Int,
    val distanceMeters: Double,
    val averagePaceSecPerKm: Int?,
)

@Entity(
    tableName = "activity_route_points",
    primaryKeys = ["sessionId", "pointIndex"],
    foreignKeys = [
        ForeignKey(
            entity = ActivitySessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sessionId")],
)
data class ActivityRoutePointEntity(
    val sessionId: String,
    val pointIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val recordedAtEpochMs: Long,
    val accuracyMeters: Float,
)

data class ActivitySessionRecord(
    @Embedded
    val session: ActivitySessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId",
    )
    val routePoints: List<ActivityRoutePointEntity>,
)

internal fun CompletedActivitySession.toActivitySessionEntity(): ActivitySessionEntity = ActivitySessionEntity(
    id = id,
    type = type.rawValue,
    workoutId = workoutId,
    workoutTitle = workoutTitle,
    startedAtEpochMs = startedAtEpochMs,
    completedAtEpochMs = completedAtEpochMs,
    durationSec = durationSec,
    distanceMeters = distanceMeters,
    averagePaceSecPerKm = averagePaceSecPerKm,
)

internal fun CompletedActivitySession.toRoutePointEntities(): List<ActivityRoutePointEntity> =
    routePoints.mapIndexed { index, point ->
        ActivityRoutePointEntity(
            sessionId = id,
            pointIndex = index,
            latitude = point.latitude,
            longitude = point.longitude,
            recordedAtEpochMs = point.recordedAtEpochMs,
            accuracyMeters = point.accuracyMeters,
        )
    }

internal fun ActivitySessionRecord.toDomainModel(): CompletedActivitySession = CompletedActivitySession(
    id = session.id,
    type = ActivitySessionType.fromRawValue(session.type),
    workoutId = session.workoutId,
    workoutTitle = session.workoutTitle,
    startedAtEpochMs = session.startedAtEpochMs,
    completedAtEpochMs = session.completedAtEpochMs,
    durationSec = session.durationSec,
    distanceMeters = session.distanceMeters,
    averagePaceSecPerKm = session.averagePaceSecPerKm,
    routePoints = routePoints
        .sortedBy { point -> point.pointIndex }
        .map { point ->
            ActivityRoutePoint(
                latitude = point.latitude,
                longitude = point.longitude,
                recordedAtEpochMs = point.recordedAtEpochMs,
                accuracyMeters = point.accuracyMeters,
            )
        },
)
