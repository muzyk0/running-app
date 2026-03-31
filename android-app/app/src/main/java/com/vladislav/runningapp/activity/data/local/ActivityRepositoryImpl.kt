package com.vladislav.runningapp.activity.data.local

import com.vladislav.runningapp.activity.ActivityRepository
import com.vladislav.runningapp.activity.CompletedActivitySession
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DefaultActivityRepository @Inject constructor(
    private val activityDao: ActivityDao,
) : ActivityRepository {
    override fun observeCompletedSessions(): Flow<List<CompletedActivitySession>> =
        activityDao.observeActivitySessionRecords().map { records ->
            records.map(ActivitySessionRecord::toDomainModel)
        }

    override suspend fun saveCompletedSession(session: CompletedActivitySession) {
        activityDao.upsertCompletedSession(
            session = session.toActivitySessionEntity(),
            routePoints = session.toRoutePointEntities(),
        )
    }
}
