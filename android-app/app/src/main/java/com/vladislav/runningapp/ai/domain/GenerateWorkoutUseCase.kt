package com.vladislav.runningapp.ai.domain

import com.vladislav.runningapp.profile.UserProfile
import javax.inject.Inject

class GenerateWorkoutUseCase @Inject constructor(
    private val repository: TrainingGenerationRepository,
) {
    suspend operator fun invoke(
        profile: UserProfile,
        userNote: String,
    ): TrainingGenerationResult = repository.generateWorkout(
        profile = profile,
        userNote = userNote.trim().takeIf { value -> value.isNotEmpty() },
    )
}
