package com.vladislav.runningapp.ai.data.remote

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST

interface TrainingGenerationApiService {
    @POST("v1/trainings/generate")
    suspend fun generateTraining(
        @Body request: GenerateTrainingRequestDto,
    ): Response<GenerateTrainingResponseDto>
}
