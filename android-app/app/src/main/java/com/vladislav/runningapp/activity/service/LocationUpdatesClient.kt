package com.vladislav.runningapp.activity.service

import com.vladislav.runningapp.activity.ActivityRoutePoint
import kotlinx.coroutines.flow.Flow

interface LocationUpdatesClient {
    fun locationUpdates(): Flow<ActivityRoutePoint>
}
