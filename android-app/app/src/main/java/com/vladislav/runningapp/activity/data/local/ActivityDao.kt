package com.vladislav.runningapp.activity.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ActivityDao {
    @Transaction
    @Query("SELECT * FROM activity_sessions ORDER BY completedAtEpochMs DESC")
    fun observeActivitySessionRecords(): Flow<List<ActivitySessionRecord>>

    @Transaction
    @Query("SELECT * FROM activity_sessions WHERE id = :sessionId")
    suspend fun getActivitySessionRecord(sessionId: String): ActivitySessionRecord?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertActivitySession(entity: ActivitySessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutePoints(points: List<ActivityRoutePointEntity>)

    @Query("DELETE FROM activity_route_points WHERE sessionId = :sessionId")
    suspend fun deleteRoutePoints(sessionId: String)

    @Transaction
    suspend fun upsertCompletedSession(
        session: ActivitySessionEntity,
        routePoints: List<ActivityRoutePointEntity>,
    ) {
        upsertActivitySession(session)
        deleteRoutePoints(session.id)
        if (routePoints.isNotEmpty()) {
            insertRoutePoints(routePoints)
        }
    }
}
