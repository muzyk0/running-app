package com.vladislav.runningapp.ai.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.ai.domain.GenerateWorkoutUseCase
import com.vladislav.runningapp.ai.domain.TrainingGenerationResult
import com.vladislav.runningapp.core.di.DefaultDispatcher
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

data class GenerationUiState(
    val isLoadingProfile: Boolean = true,
    val profile: UserProfile? = null,
    val userNote: String = "",
    val isGenerating: Boolean = false,
    val generatedWorkout: Workout? = null,
    val errorMessage: String? = null,
    val isSaving: Boolean = false,
) {
    val canGenerate: Boolean
        get() = profile != null && !isLoadingProfile && !isGenerating && !isSaving

    val canSaveGeneratedWorkout: Boolean
        get() = generatedWorkout != null && !isGenerating && !isSaving
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
            )
        }
    }

    fun onDismissError() {
        _uiState.update { state ->
            state.copy(errorMessage = null)
        }
    }

    fun onGenerateWorkout() {
        val currentState = _uiState.value
        val profile = currentState.profile
        if (profile == null) {
            _uiState.update { state ->
                state.copy(errorMessage = "Сначала заполните профиль, иначе backend не сможет собрать тренировку.")
            }
            return
        }
        if (currentState.isGenerating || currentState.isSaving) {
            return
        }

        _uiState.update { state ->
            state.copy(
                isGenerating = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch(defaultDispatcher) {
            when (val result = generateWorkoutUseCase(profile, currentState.userNote)) {
                is TrainingGenerationResult.Success -> {
                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            generatedWorkout = result.workout,
                            errorMessage = null,
                        )
                    }
                }

                is TrainingGenerationResult.Failure -> {
                    _uiState.update { state ->
                        state.copy(
                            isGenerating = false,
                            errorMessage = result.error.message,
                        )
                    }
                }
            }
        }
    }

    fun onAcceptGeneratedWorkout() {
        val generatedWorkout = _uiState.value.generatedWorkout ?: return
        if (_uiState.value.isSaving) {
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
                        errorMessage = "Не удалось сохранить тренировку локально. Повторите попытку.",
                    )
                }
            }
        }
    }
}
