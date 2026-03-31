package com.vladislav.runningapp.activity

import kotlinx.coroutines.flow.Flow

interface ActivityRepository {
    fun observeCompletedSessions(): Flow<List<CompletedActivitySession>>

    suspend fun saveCompletedSession(session: CompletedActivitySession)
}
