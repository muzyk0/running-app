package com.vladislav.runningapp.ai.data.remote

import com.google.gson.annotations.SerializedName

data class GenerateTrainingRequestDto(
    @SerializedName("profile")
    val profile: GenerateTrainingProfileDto,
    @SerializedName("request")
    val request: GenerateTrainingRequestContextDto,
)

data class GenerateTrainingProfileDto(
    @SerializedName("height_cm")
    val heightCm: Int,
    @SerializedName("weight_kg")
    val weightKg: Int,
    @SerializedName("sex")
    val sex: String,
    @SerializedName("age")
    val age: Int,
    @SerializedName("training_days_per_week")
    val trainingDaysPerWeek: Int,
    @SerializedName("fitness_level")
    val fitnessLevel: String,
    @SerializedName("injuries_and_limitations")
    val injuriesAndLimitations: String,
    @SerializedName("training_goal")
    val trainingGoal: String,
    @SerializedName("additional_prompt_fields")
    val additionalPromptFields: List<GenerateTrainingPromptFieldDto>,
)

data class GenerateTrainingPromptFieldDto(
    @SerializedName("label")
    val label: String,
    @SerializedName("value")
    val value: String,
)

data class GenerateTrainingRequestContextDto(
    @SerializedName("locale")
    val locale: String,
    @SerializedName("user_note")
    val userNote: String? = null,
)

data class TrainingGenerationLogEventDto(
    @SerializedName("message")
    val message: String,
)

data class GenerateTrainingResponseDto(
    @SerializedName("schema_version")
    val schemaVersion: String,
    @SerializedName("training")
    val training: RemoteWorkoutDto,
)

data class RemoteWorkoutDto(
    @SerializedName("title")
    val title: String,
    @SerializedName("summary")
    val summary: String? = null,
    @SerializedName("goal")
    val goal: String? = null,
    @SerializedName("estimated_duration_sec")
    val estimatedDurationSec: Int? = null,
    @SerializedName("disclaimer")
    val disclaimer: String? = null,
    @SerializedName("steps")
    val steps: List<RemoteWorkoutStepDto>,
)

data class RemoteWorkoutStepDto(
    @SerializedName("id")
    val id: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("duration_sec")
    val durationSec: Int,
    @SerializedName("voice_prompt")
    val voicePrompt: String,
)

data class ApiErrorEnvelopeDto(
    @SerializedName("error")
    val error: ApiErrorDto? = null,
)

data class ApiErrorDto(
    @SerializedName("code")
    val code: String? = null,
    @SerializedName("message")
    val message: String? = null,
)

sealed interface RemoteTrainingGenerationStreamEventDto {
    data class Log(
        val payload: TrainingGenerationLogEventDto,
    ) : RemoteTrainingGenerationStreamEventDto

    data class Completed(
        val payload: GenerateTrainingResponseDto,
    ) : RemoteTrainingGenerationStreamEventDto

    data class Error(
        val payload: ApiErrorEnvelopeDto,
    ) : RemoteTrainingGenerationStreamEventDto
}
