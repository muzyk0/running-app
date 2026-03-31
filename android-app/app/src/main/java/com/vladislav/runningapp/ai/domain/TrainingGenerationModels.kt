package com.vladislav.runningapp.ai.domain

import com.vladislav.runningapp.training.domain.Workout

enum class TrainingGenerationErrorCode {
    InvalidRequest,
    InvalidResponse,
    Network,
    ServiceUnavailable,
    ProviderError,
    Timeout,
    Unknown,
}

data class TrainingGenerationError(
    val code: TrainingGenerationErrorCode,
    val message: String,
)

sealed interface TrainingGenerationResult {
    data class Success(
        val workout: Workout,
    ) : TrainingGenerationResult

    data class Failure(
        val error: TrainingGenerationError,
    ) : TrainingGenerationResult
}
