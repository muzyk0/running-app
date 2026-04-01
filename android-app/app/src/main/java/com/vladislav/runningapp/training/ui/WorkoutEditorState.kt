package com.vladislav.runningapp.training.ui

import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.UiText
import com.vladislav.runningapp.core.i18n.uiText
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import java.util.Locale

data class WorkoutStepDraft(
    val type: WorkoutStepType = WorkoutStepType.Warmup,
    val durationSec: String = "",
    val voicePrompt: String = "",
)

data class WorkoutStepValidationErrors(
    val durationSec: UiText? = null,
    val voicePrompt: UiText? = null,
) {
    val hasErrors: Boolean
        get() = durationSec != null || voicePrompt != null
}

data class WorkoutEditorValidationErrors(
    val title: UiText? = null,
    val stepsMessage: UiText? = null,
    val stepErrors: List<WorkoutStepValidationErrors> = emptyList(),
) {
    val hasErrors: Boolean
        get() = title != null || stepsMessage != null || stepErrors.any { error -> error.hasErrors }
}

data class WorkoutEditorState(
    val workoutId: String? = null,
    val schemaVersion: String = DefaultWorkoutSchemaVersion,
    val title: String = "",
    val summary: String = "",
    val goal: String = "",
    val disclaimer: String = "",
    val steps: List<WorkoutStepDraft> = listOf(WorkoutStepDraft()),
    val validationErrors: WorkoutEditorValidationErrors = WorkoutEditorValidationErrors(),
    val isSaving: Boolean = false,
) {
    val totalDurationSec: Int
        get() = steps.sumOf { step -> step.durationSec.trim().toIntOrNull()?.coerceAtLeast(0) ?: 0 }
}

sealed interface WorkoutEditorAction {
    data class LoadWorkout(val workout: Workout?) : WorkoutEditorAction

    data class AssignWorkoutId(val workoutId: String) : WorkoutEditorAction

    data class SetTitle(val value: String) : WorkoutEditorAction

    data class SetSummary(val value: String) : WorkoutEditorAction

    data class SetGoal(val value: String) : WorkoutEditorAction

    data class SetDisclaimer(val value: String) : WorkoutEditorAction

    data object AddStep : WorkoutEditorAction

    data class RemoveStep(val index: Int) : WorkoutEditorAction

    data class SetStepType(
        val index: Int,
        val value: WorkoutStepType,
    ) : WorkoutEditorAction

    data class SetStepDuration(
        val index: Int,
        val value: String,
    ) : WorkoutEditorAction

    data class SetStepVoicePrompt(
        val index: Int,
        val value: String,
    ) : WorkoutEditorAction

    data class SetValidationErrors(
        val value: WorkoutEditorValidationErrors,
    ) : WorkoutEditorAction

    data class SetSaving(val value: Boolean) : WorkoutEditorAction
}

object WorkoutEditorReducer {
    fun reduce(
        state: WorkoutEditorState,
        action: WorkoutEditorAction,
    ): WorkoutEditorState = when (action) {
        is WorkoutEditorAction.LoadWorkout -> action.workout?.toEditorState() ?: WorkoutEditorState()
        is WorkoutEditorAction.AssignWorkoutId -> state.copy(workoutId = action.workoutId)
        is WorkoutEditorAction.SetTitle -> state.updated { copy(title = action.value) }
        is WorkoutEditorAction.SetSummary -> state.updated { copy(summary = action.value) }
        is WorkoutEditorAction.SetGoal -> state.updated { copy(goal = action.value) }
        is WorkoutEditorAction.SetDisclaimer -> state.updated { copy(disclaimer = action.value) }
        WorkoutEditorAction.AddStep -> state.updated {
            copy(steps = steps + WorkoutStepDraft(type = WorkoutStepType.Run))
        }

        is WorkoutEditorAction.RemoveStep -> state.updated {
            copy(
                steps = steps.filterIndexed { index, _ -> index != action.index },
            )
        }

        is WorkoutEditorAction.SetStepType -> state.updated {
            copy(
                steps = steps.update(action.index) { step ->
                    step.copy(type = action.value)
                },
            )
        }

        is WorkoutEditorAction.SetStepDuration -> state.updated {
            copy(
                steps = steps.update(action.index) { step ->
                    step.copy(durationSec = action.value)
                },
            )
        }

        is WorkoutEditorAction.SetStepVoicePrompt -> state.updated {
            copy(
                steps = steps.update(action.index) { step ->
                    step.copy(voicePrompt = action.value)
                },
            )
        }

        is WorkoutEditorAction.SetValidationErrors -> state.copy(validationErrors = action.value)
        is WorkoutEditorAction.SetSaving -> state.copy(isSaving = action.value)
    }
}

object WorkoutEditorValidator {
    fun validate(state: WorkoutEditorState): WorkoutEditorValidationErrors {
        val titleError = if (state.title.trim().isBlank()) {
            uiText(R.string.training_validation_title_required)
        } else {
            null
        }

        val stepsMessage = if (state.steps.isEmpty()) {
            uiText(R.string.training_validation_steps_required)
        } else {
            null
        }

        val stepErrors = state.steps.map { step ->
            WorkoutStepValidationErrors(
                durationSec = validateDuration(step.durationSec),
                voicePrompt = if (step.voicePrompt.trim().isBlank()) {
                    uiText(R.string.training_validation_step_voice_prompt_required)
                } else {
                    null
                },
            )
        }

        return WorkoutEditorValidationErrors(
            title = titleError,
            stepsMessage = stepsMessage,
            stepErrors = stepErrors,
        )
    }

    private fun validateDuration(value: String): UiText? {
        val parsed = value.trim().toIntOrNull()
        return when {
            value.trim().isBlank() -> uiText(R.string.training_validation_step_duration_required)
            parsed == null || parsed <= 0 -> uiText(R.string.training_validation_step_duration_invalid)
            else -> null
        }
    }
}

fun WorkoutEditorState.toDomainWorkout(): Workout = Workout(
    id = requireNotNull(workoutId),
    schemaVersion = schemaVersion,
    title = title.trim(),
    summary = summary.trim().orNullIfBlank(),
    goal = goal.trim().orNullIfBlank(),
    disclaimer = disclaimer.trim().orNullIfBlank(),
    steps = steps.map { step ->
        WorkoutStep(
            type = step.type,
            durationSec = step.durationSec.trim().toIntOrNull() ?: 0,
            voicePrompt = step.voicePrompt.trim(),
        )
    },
)

fun Workout.toEditorState(): WorkoutEditorState = WorkoutEditorState(
    workoutId = id,
    schemaVersion = schemaVersion,
    title = title,
    summary = summary.orEmpty(),
    goal = goal.orEmpty(),
    disclaimer = disclaimer.orEmpty(),
    steps = steps.map { step ->
        WorkoutStepDraft(
            type = step.type,
            durationSec = step.durationSec.toString(),
            voicePrompt = step.voicePrompt,
        )
    },
)

fun buildDuplicateWorkoutTitle(
    sourceTitle: String,
    existingTitles: Set<String>,
    defaultTitle: String,
    duplicatePattern: String,
    numberedDuplicatePattern: String,
    locale: Locale = Locale.getDefault(),
): String {
    val normalizedSourceTitle = sourceTitle.trim().ifBlank { defaultTitle }
    val firstCandidate = String.format(locale, duplicatePattern, normalizedSourceTitle)
    if (firstCandidate !in existingTitles) {
        return firstCandidate
    }

    var copyNumber = 2
    while (true) {
        val candidate = String.format(
            locale,
            numberedDuplicatePattern,
            normalizedSourceTitle,
            copyNumber,
        )
        if (candidate !in existingTitles) {
            return candidate
        }
        copyNumber += 1
    }
}

private fun WorkoutEditorState.updated(
    transform: WorkoutEditorState.() -> WorkoutEditorState,
): WorkoutEditorState = transform().copy(
    validationErrors = WorkoutEditorValidationErrors(),
    isSaving = false,
)

private fun List<WorkoutStepDraft>.update(
    index: Int,
    transform: (WorkoutStepDraft) -> WorkoutStepDraft,
): List<WorkoutStepDraft> = mapIndexed { currentIndex, step ->
    if (currentIndex == index) {
        transform(step)
    } else {
        step
    }
}

private fun String.orNullIfBlank(): String? = takeIf { value -> value.isNotBlank() }
