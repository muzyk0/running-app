package com.vladislav.runningapp.ai.domain

import com.vladislav.runningapp.profile.UserProfile
import kotlinx.coroutines.flow.Flow

interface TrainingGenerationRepository {
    fun generateWorkout(
        profile: UserProfile,
        userNote: String?,
    ): Flow<TrainingGenerationUpdate>
}
