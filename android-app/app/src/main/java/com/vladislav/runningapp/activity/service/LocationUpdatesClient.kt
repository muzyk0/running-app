package com.vladislav.runningapp.activity.service

import com.vladislav.runningapp.activity.ActivityRoutePoint
import kotlinx.coroutines.flow.Flow

interface LocationUpdatesClient {
    suspend fun openSession(): LocationUpdatesSession
}

interface LocationUpdatesSession {
    val updates: Flow<ActivityRoutePoint>

    suspend fun close()
}
