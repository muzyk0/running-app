package com.vladislav.runningapp.training.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.activity.ActivityTracker
import com.vladislav.runningapp.core.di.DefaultDispatcher
import com.vladislav.runningapp.training.WorkoutRepository
import com.vladislav.runningapp.training.domain.Workout
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TrainingUiState(
    val isLoading: Boolean = true,
    val workouts: List<Workout> = emptyList(),
    val selectedWorkoutId: String? = null,
    val editorState: WorkoutEditorState? = null,
    val pendingDeleteWorkoutId: String? = null,
) {
    val selectedWorkout: Workout?
        get() = workouts.firstOrNull { workout -> workout.id == selectedWorkoutId }
}

@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val workoutRepository: WorkoutRepository,
    private val activityTracker: ActivityTracker,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(TrainingUiState())
    val uiState: StateFlow<TrainingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            workoutRepository.observeWorkouts().collect { workouts ->
                _uiState.update { state ->
                    state.copy(
                        isLoading = false,
                        workouts = workouts,
                        selectedWorkoutId = resolveSelectedWorkoutId(
                            currentId = state.selectedWorkoutId,
                            workouts = workouts,
                        ),
                        pendingDeleteWorkoutId = state.pendingDeleteWorkoutId?.takeIf { workoutId ->
                            workouts.any { workout -> workout.id == workoutId }
                        },
                    )
                }
            }
        }
    }

    fun onSelectWorkout(workoutId: String) {
        _uiState.update { state ->
            if (state.editorState != null) {
                state
            } else {
                state.copy(selectedWorkoutId = workoutId)
            }
        }
    }

    fun onCreateWorkout() {
        _uiState.update { state ->
            state.copy(
                editorState = WorkoutEditorReducer.reduce(
                    state = WorkoutEditorState(),
                    action = WorkoutEditorAction.LoadWorkout(workout = null),
                ),
                pendingDeleteWorkoutId = null,
            )
        }
    }

    fun onEditSelectedWorkout() {
        val selectedWorkout = _uiState.value.selectedWorkout ?: return
        _uiState.update { state ->
            state.copy(
                editorState = WorkoutEditorReducer.reduce(
                    state = WorkoutEditorState(),
                    action = WorkoutEditorAction.LoadWorkout(selectedWorkout),
                ),
                pendingDeleteWorkoutId = null,
            )
        }
    }

    fun onDismissEditor() {
        _uiState.update { state ->
            state.copy(editorState = null)
        }
    }

    fun onRequestDeleteSelectedWorkout() {
        val selectedWorkoutId = _uiState.value.selectedWorkoutId ?: return
        _uiState.update { state ->
            state.copy(pendingDeleteWorkoutId = selectedWorkoutId)
        }
    }

    fun onDismissDeleteWorkout() {
        _uiState.update { state ->
            state.copy(pendingDeleteWorkoutId = null)
        }
    }

    fun onConfirmDeleteWorkout() {
        val workoutId = _uiState.value.pendingDeleteWorkoutId ?: return
        viewModelScope.launch(defaultDispatcher) {
            workoutRepository.deleteWorkout(workoutId)
            _uiState.update { state ->
                state.copy(pendingDeleteWorkoutId = null)
            }
        }
    }

    fun onSaveCopyOfSelectedWorkout() {
        val selectedWorkout = _uiState.value.selectedWorkout ?: return
        val duplicatedWorkout = selectedWorkout.copy(
            id = UUID.randomUUID().toString(),
            title = buildDuplicateWorkoutTitle(
                sourceTitle = selectedWorkout.title,
                existingTitles = _uiState.value.workouts.map { workout -> workout.title }.toSet(),
            ),
        )
        viewModelScope.launch(defaultDispatcher) {
            workoutRepository.saveWorkout(duplicatedWorkout)
            _uiState.update { state ->
                state.copy(selectedWorkoutId = duplicatedWorkout.id)
            }
        }
    }

    fun onStartSelectedWorkout() {
        val selectedWorkout = _uiState.value.selectedWorkout ?: return
        activityTracker.startPlannedWorkout(selectedWorkout)
    }

    fun onSaveWorkout() {
        val currentEditorState = _uiState.value.editorState ?: return
        if (currentEditorState.isSaving) {
            return
        }

        val validationErrors = WorkoutEditorValidator.validate(currentEditorState)
        if (validationErrors.hasErrors) {
            _uiState.update { state ->
                state.copy(
                    editorState = state.editorState?.let { editorState ->
                        WorkoutEditorReducer.reduce(
                            state = editorState,
                            action = WorkoutEditorAction.SetValidationErrors(validationErrors),
                        )
                    },
                )
            }
            return
        }

        val preparedEditorState = prepareEditorStateForSave(currentEditorState)
        _uiState.update { state ->
            state.copy(editorState = preparedEditorState)
        }

        viewModelScope.launch(defaultDispatcher) {
            workoutRepository.saveWorkout(preparedEditorState.toDomainWorkout())
            _uiState.update { state ->
                state.copy(
                    selectedWorkoutId = preparedEditorState.workoutId,
                    editorState = null,
                )
            }
        }
    }

    fun onTitleChanged(value: String) = updateEditor(WorkoutEditorAction.SetTitle(value))

    fun onSummaryChanged(value: String) = updateEditor(WorkoutEditorAction.SetSummary(value))

    fun onGoalChanged(value: String) = updateEditor(WorkoutEditorAction.SetGoal(value))

    fun onDisclaimerChanged(value: String) = updateEditor(WorkoutEditorAction.SetDisclaimer(value))

    fun onAddStep() = updateEditor(WorkoutEditorAction.AddStep)

    fun onRemoveStep(index: Int) = updateEditor(WorkoutEditorAction.RemoveStep(index))

    fun onStepTypeChanged(
        index: Int,
        value: com.vladislav.runningapp.training.domain.WorkoutStepType,
    ) = updateEditor(WorkoutEditorAction.SetStepType(index, value))

    fun onStepDurationChanged(
        index: Int,
        value: String,
    ) = updateEditor(WorkoutEditorAction.SetStepDuration(index, value))

    fun onStepVoicePromptChanged(
        index: Int,
        value: String,
    ) = updateEditor(WorkoutEditorAction.SetStepVoicePrompt(index, value))

    private fun updateEditor(action: WorkoutEditorAction) {
        _uiState.update { state ->
            val editorState = state.editorState ?: return@update state
            state.copy(
                editorState = WorkoutEditorReducer.reduce(
                    state = editorState,
                    action = action,
                ),
            )
        }
    }

    private fun prepareEditorStateForSave(editorState: WorkoutEditorState): WorkoutEditorState {
        val stateWithId = if (editorState.workoutId == null) {
            WorkoutEditorReducer.reduce(
                state = editorState,
                action = WorkoutEditorAction.AssignWorkoutId(UUID.randomUUID().toString()),
            )
        } else {
            editorState
        }

        return WorkoutEditorReducer.reduce(
            state = stateWithId,
            action = WorkoutEditorAction.SetSaving(true),
        )
    }

    private fun resolveSelectedWorkoutId(
        currentId: String?,
        workouts: List<Workout>,
    ): String? = when {
        currentId != null && workouts.any { workout -> workout.id == currentId } -> currentId
        workouts.isNotEmpty() -> workouts.first().id
        else -> null
    }
}
