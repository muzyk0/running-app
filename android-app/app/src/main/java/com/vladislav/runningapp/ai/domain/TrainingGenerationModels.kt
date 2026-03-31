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

sealed interface TrainingGenerationUpdate {
    data class Log(
        val message: String,
    ) : TrainingGenerationUpdate

    data class Completed(
        val workout: Workout,
    ) : TrainingGenerationUpdate

    data class Failure(
        val error: TrainingGenerationError,
    ) : TrainingGenerationUpdate
}
