package com.vladislav.runningapp.ai.domain

import com.vladislav.runningapp.profile.UserProfile
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class GenerateWorkoutUseCase @Inject constructor(
    private val repository: TrainingGenerationRepository,
) {
    operator fun invoke(
        profile: UserProfile,
        userNote: String,
    ): Flow<TrainingGenerationUpdate> = repository.generateWorkout(
        profile = profile,
        userNote = userNote.trim().takeIf { value -> value.isNotEmpty() },
    )
}
