package com.vladislav.runningapp.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.ai.domain.GenerateWorkoutUseCase
import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationFailureSource
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.di.DefaultDispatcher
import com.vladislav.runningapp.core.i18n.UiText
import com.vladislav.runningapp.core.i18n.uiText
import com.vladislav.runningapp.profile.ProfileRepository
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.training.WorkoutRepository
import com.vladislav.runningapp.training.domain.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GenerationPhase {
    Idle,
    Streaming,
    Completed,
}

private const val MaxGenerationOutputChars = 12_000

data class GenerationUiState(
    val isLoadingProfile: Boolean = true,
    val profile: UserProfile? = null,
    val userNote: String = "",
    val generationPhase: GenerationPhase = GenerationPhase.Idle,
    val generationOutput: String = "",
    val streamErrorMessage: UiText? = null,
    val generatedWorkout: Workout? = null,
    val savedWorkoutId: String? = null,
    val errorMessage: UiText? = null,
    val isSaving: Boolean = false,
) {
    val isGenerating: Boolean
        get() = generationPhase == GenerationPhase.Streaming

    val isWorkoutReady: Boolean
        get() = generationPhase == GenerationPhase.Completed && generatedWorkout != null

    val canGenerate: Boolean
        get() = profile != null && !isLoadingProfile && !isGenerating && !isSaving

    val canSaveGeneratedWorkout: Boolean
        get() = isWorkoutReady && savedWorkoutId == null && !isSaving

    val shouldShowGenerationOutput: Boolean
        get() = isGenerating || generationOutput.isNotBlank() || streamErrorMessage != null || isWorkoutReady
}

sealed interface GenerationNavigationEvent {
    data class OpenSavedWorkout(
        val workoutId: String,
    ) : GenerationNavigationEvent
}

@HiltViewModel
class GenerationViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val workoutRepository: WorkoutRepository,
    private val generateWorkoutUseCase: GenerateWorkoutUseCase,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GenerationUiState())
    val uiState: StateFlow<GenerationUiState> = _uiState.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<GenerationNavigationEvent>(extraBufferCapacity = 1)
    val navigationEvents: SharedFlow<GenerationNavigationEvent> = _navigationEvents.asSharedFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            profileRepository.observeProfile().collect { profile ->
                _uiState.update { state ->
                    state.copy(
                        isLoadingProfile = false,
                        profile = profile,
                    )
                }
            }
        }
    }

    fun onUserNoteChanged(value: String) {
        _uiState.update { state ->
            state.copy(
                userNote = value,
                errorMessage = null,
                streamErrorMessage = null,
            )
        }
    }

    fun onDismissError() {
        _uiState.update { state ->
            state.copy(
                errorMessage = null,
                streamErrorMessage = null,
            )
        }
    }

    fun onGenerateWorkout() {
        val currentState = _uiState.value
        val profile = currentState.profile
        if (profile == null) {
            _uiState.update { state ->
                state.copy(errorMessage = uiText(R.string.generation_error_profile_required))
            }
            return
        }
        if (currentState.isGenerating || currentState.isSaving) {
            return
        }

        _uiState.update { state ->
            state.copy(
                generationPhase = GenerationPhase.Streaming,
                generationOutput = "",
                streamErrorMessage = null,
                generatedWorkout = null,
                savedWorkoutId = null,
                errorMessage = null,
            )
        }

        viewModelScope.launch(defaultDispatcher) {
            generateWorkoutUseCase(profile, currentState.userNote).collect { update ->
                when (update) {
                    is TrainingGenerationUpdate.Log -> {
                        _uiState.update { state ->
                            state.copy(
                                generationOutput = state.generationOutput.appendOutputChunk(update.message),
                            )
                        }
                    }

                    is TrainingGenerationUpdate.Completed -> {
                        _uiState.update { state ->
                            state.copy(
                                generationPhase = GenerationPhase.Completed,
                                generatedWorkout = update.workout,
                                savedWorkoutId = null,
                                streamErrorMessage = null,
                                errorMessage = null,
                            )
                        }
                    }

                    is TrainingGenerationUpdate.Failure -> {
                        _uiState.update { state ->
                            when (update.source) {
                                TrainingGenerationFailureSource.Request ->
                                    state.copy(
                                        generationPhase = GenerationPhase.Idle,
                                        generatedWorkout = null,
                                        savedWorkoutId = null,
                                        errorMessage = update.error.toUiText(update.source),
                                        streamErrorMessage = null,
                                    )

                                TrainingGenerationFailureSource.Stream ->
                                    state.copy(
                                        generationPhase = GenerationPhase.Idle,
                                        generatedWorkout = null,
                                        savedWorkoutId = null,
                                        errorMessage = null,
                                        streamErrorMessage = update.error.toUiText(update.source),
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    fun onAcceptGeneratedWorkout() {
        val currentState = _uiState.value
        val generatedWorkout = currentState.generatedWorkout ?: return
        if (currentState.isSaving || currentState.savedWorkoutId != null) {
            return
        }

        val savedWorkout = generatedWorkout.copy(id = UUID.randomUUID().toString())
        _uiState.update { state ->
            state.copy(
                isSaving = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(defaultDispatcher) {
            runCatching {
                workoutRepository.saveWorkout(savedWorkout)
            }.onSuccess {
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        generatedWorkout = savedWorkout,
                        savedWorkoutId = savedWorkout.id,
                    )
                }
                _navigationEvents.emit(GenerationNavigationEvent.OpenSavedWorkout(savedWorkout.id))
            }.onFailure { error ->
                if (error is CancellationException) {
                    throw error
                }
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        errorMessage = uiText(R.string.generation_error_save_failed),
                    )
                }
            }
        }
    }
}

private fun TrainingGenerationError.toUiText(
    source: TrainingGenerationFailureSource,
): UiText = when (source) {
    TrainingGenerationFailureSource.Request -> when (code) {
        TrainingGenerationErrorCode.InvalidRequest -> uiText(R.string.generation_error_request_invalid)
        TrainingGenerationErrorCode.InvalidResponse -> uiText(R.string.generation_error_response_unsupported)
        TrainingGenerationErrorCode.Network -> uiText(R.string.generation_error_connection)
        TrainingGenerationErrorCode.ServiceUnavailable -> uiText(R.string.generation_error_service_unavailable)
        TrainingGenerationErrorCode.ProviderError -> uiText(R.string.generation_error_provider_failed)
        TrainingGenerationErrorCode.Timeout -> uiText(R.string.generation_error_timeout)
        TrainingGenerationErrorCode.Unknown -> uiText(R.string.generation_error_unknown)
    }

    TrainingGenerationFailureSource.Stream -> when (code) {
        TrainingGenerationErrorCode.Network -> uiText(R.string.generation_error_connection)
        TrainingGenerationErrorCode.ServiceUnavailable -> uiText(R.string.generation_error_service_unavailable)
        TrainingGenerationErrorCode.ProviderError -> uiText(R.string.generation_error_provider_failed)
        TrainingGenerationErrorCode.Timeout -> uiText(R.string.generation_error_timeout)
        TrainingGenerationErrorCode.InvalidRequest -> uiText(R.string.generation_error_request_invalid)
        TrainingGenerationErrorCode.InvalidResponse,
        TrainingGenerationErrorCode.Unknown -> uiText(R.string.generation_error_response_processing)
    }
}

private fun String.appendOutputChunk(chunk: String): String {
    val normalizedChunk = chunk.trimEnd('\r', '\n')
    if (normalizedChunk.isBlank()) {
        return this
    }
    val combined = if (isBlank()) {
        normalizedChunk
    } else {
        "$this\n$normalizedChunk"
    }
    return combined.takeLast(MaxGenerationOutputChars).trimStart('\n')
}
