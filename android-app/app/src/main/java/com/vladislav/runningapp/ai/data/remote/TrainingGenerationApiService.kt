package com.vladislav.runningapp.ai.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Streaming

interface TrainingGenerationApiService {
    @Headers("Accept: text/event-stream")
    @Streaming
    @POST("v1/trainings/generate")
    suspend fun generateTraining(
        @Body request: GenerateTrainingRequestDto,
    ): Response<ResponseBody>
}

internal fun ResponseBody.toTrainingGenerationEvents(
    gson: Gson,
): Flow<RemoteTrainingGenerationStreamEventDto> = flow {
    charStream().buffered().use { reader ->
        var eventName: String? = null
        val dataLines = mutableListOf<String>()
        var terminalEventSeen = false

        while (true) {
            val line = reader.readLine() ?: break
            when {
                line.isEmpty() -> {
                    val event = parseTrainingGenerationEvent(
                        gson = gson,
                        eventName = eventName,
                        dataLines = dataLines,
                    )
                    if (event != null) {
                        emit(event)
                        if (event is RemoteTrainingGenerationStreamEventDto.Completed ||
                            event is RemoteTrainingGenerationStreamEventDto.Error
                        ) {
                            terminalEventSeen = true
                            break
                        }
                    }
                    eventName = null
                    dataLines.clear()
                }

                line.startsWith(":") -> Unit
                line.startsWith("event:") -> eventName = line.substringAfter(':').trimStart()
                line.startsWith("data:") -> dataLines += line.substringAfter(':').trimStart()
            }
        }

        if (!terminalEventSeen) {
            parseTrainingGenerationEvent(
                gson = gson,
                eventName = eventName,
                dataLines = dataLines,
            )?.let { emit(it) }
        }
    }
}

private fun parseTrainingGenerationEvent(
    gson: Gson,
    eventName: String?,
    dataLines: List<String>,
): RemoteTrainingGenerationStreamEventDto? {
    if (eventName == null || dataLines.isEmpty()) {
        return null
    }

    val payload = dataLines.joinToString(separator = "\n")
    return when (eventName) {
        "log" -> RemoteTrainingGenerationStreamEventDto.Log(
            payload = gson.fromJson(payload, TrainingGenerationLogEventDto::class.java),
        )

        "completed" -> RemoteTrainingGenerationStreamEventDto.Completed(
            payload = gson.fromJson(payload, GenerateTrainingResponseDto::class.java),
        )

        "error" -> RemoteTrainingGenerationStreamEventDto.Error(
            payload = gson.fromJson(payload, ApiErrorEnvelopeDto::class.java),
        )

        else -> null
    }
}
