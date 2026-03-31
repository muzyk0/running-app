package com.vladislav.runningapp.ai.domain

import com.vladislav.runningapp.profile.UserProfile

interface TrainingGenerationRepository {
    suspend fun generateWorkout(
        profile: UserProfile,
        userNote: String?,
    ): TrainingGenerationResult
}
