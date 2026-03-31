package com.vladislav.runningapp.training.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladislav.runningapp.R
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType

@Composable
fun TrainingScreen(
    focusedWorkoutId: String? = null,
    onOpenActiveSession: () -> Unit,
    viewModel: TrainingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(focusedWorkoutId) {
        focusedWorkoutId?.takeIf { workoutId -> workoutId.isNotBlank() }?.let(viewModel::onFocusWorkout)
    }

    TrainingScreen(
        state = uiState,
        onSelectWorkout = viewModel::onSelectWorkout,
        onCreateWorkout = viewModel::onCreateWorkout,
        onEditWorkout = viewModel::onEditSelectedWorkout,
        onDismissEditor = viewModel::onDismissEditor,
        onRequestDeleteWorkout = viewModel::onRequestDeleteSelectedWorkout,
        onDismissDeleteWorkout = viewModel::onDismissDeleteWorkout,
        onConfirmDeleteWorkout = viewModel::onConfirmDeleteWorkout,
        onSaveCopy = viewModel::onSaveCopyOfSelectedWorkout,
        onStartWorkout = {
            if (viewModel.onStartSelectedWorkout()) {
                onOpenActiveSession()
            }
        },
        onTitleChanged = viewModel::onTitleChanged,
        onSummaryChanged = viewModel::onSummaryChanged,
        onGoalChanged = viewModel::onGoalChanged,
        onDisclaimerChanged = viewModel::onDisclaimerChanged,
        onAddStep = viewModel::onAddStep,
        onRemoveStep = viewModel::onRemoveStep,
        onStepTypeChanged = viewModel::onStepTypeChanged,
        onStepDurationChanged = viewModel::onStepDurationChanged,
        onStepVoicePromptChanged = viewModel::onStepVoicePromptChanged,
        onSaveWorkout = viewModel::onSaveWorkout,
    )
}

@Composable
private fun TrainingScreen(
    state: TrainingUiState,
    onSelectWorkout: (String) -> Unit,
    onCreateWorkout: () -> Unit,
    onEditWorkout: () -> Unit,
    onDismissEditor: () -> Unit,
    onRequestDeleteWorkout: () -> Unit,
    onDismissDeleteWorkout: () -> Unit,
    onConfirmDeleteWorkout: () -> Unit,
    onSaveCopy: () -> Unit,
    onStartWorkout: () -> Unit,
    onTitleChanged: (String) -> Unit,
    onSummaryChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onDisclaimerChanged: (String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onStepTypeChanged: (Int, WorkoutStepType) -> Unit,
    onStepDurationChanged: (Int, String) -> Unit,
    onStepVoicePromptChanged: (Int, String) -> Unit,
    onSaveWorkout: () -> Unit,
) {
    if (state.isLoading) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val pendingDeleteWorkout = state.workouts.firstOrNull { workout ->
        workout.id == state.pendingDeleteWorkoutId
    }
    if (pendingDeleteWorkout != null) {
        DeleteWorkoutDialog(
            workoutTitle = pendingDeleteWorkout.title,
            onDismiss = onDismissDeleteWorkout,
            onConfirm = onConfirmDeleteWorkout,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TrainingIntroCard(
            hasWorkouts = state.workouts.isNotEmpty(),
            isEditing = state.editorState != null,
            onCreateWorkout = onCreateWorkout,
        )
        state.errorMessage?.let { message ->
            InlineErrorCard(message = message)
        }

        if (state.workouts.isNotEmpty()) {
            WorkoutListCard(
                workouts = state.workouts,
                selectedWorkoutId = state.selectedWorkoutId,
                isEditing = state.editorState != null,
                onSelectWorkout = onSelectWorkout,
            )
        }

        when {
            state.editorState != null -> {
                WorkoutEditorCard(
                    state = state.editorState,
                    onTitleChanged = onTitleChanged,
                    onSummaryChanged = onSummaryChanged,
                    onGoalChanged = onGoalChanged,
                    onDisclaimerChanged = onDisclaimerChanged,
                    onAddStep = onAddStep,
                    onRemoveStep = onRemoveStep,
                    onStepTypeChanged = onStepTypeChanged,
                    onStepDurationChanged = onStepDurationChanged,
                    onStepVoicePromptChanged = onStepVoicePromptChanged,
                    onDismiss = onDismissEditor,
                    onSave = onSaveWorkout,
                )
            }

            state.selectedWorkout != null -> {
                val selectedWorkout = requireNotNull(state.selectedWorkout)
                WorkoutDetailCard(
                    workout = selectedWorkout,
                    onEditWorkout = onEditWorkout,
                    onSaveCopy = onSaveCopy,
                    onStartWorkout = onStartWorkout,
                    onDeleteWorkout = onRequestDeleteWorkout,
                )
            }

            else -> {
                EmptyWorkoutsCard(onCreateWorkout = onCreateWorkout)
            }
        }
    }
}

@Composable
private fun InlineErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TrainingIntroCard(
    hasWorkouts: Boolean,
    isEditing: Boolean,
    onCreateWorkout: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isEditing) {
                    stringResource(R.string.training_editor_title)
                } else {
                    stringResource(R.string.training_overview_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (hasWorkouts) {
                    stringResource(R.string.training_overview_body)
                } else {
                    stringResource(R.string.training_empty_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!isEditing) {
                Button(onClick = onCreateWorkout) {
                    Text(text = stringResource(R.string.training_create_action))
                }
            }
        }
    }
}

@Composable
private fun WorkoutListCard(
    workouts: List<Workout>,
    selectedWorkoutId: String?,
    isEditing: Boolean,
    onSelectWorkout: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.training_list_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            workouts.forEach { workout ->
                OutlinedButton(
                    onClick = { onSelectWorkout(workout.id) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isEditing,
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = workout.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = if (workout.id == selectedWorkoutId) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                        )
                        Text(
                            text = stringResource(
                                R.string.training_list_item_meta,
                                formatDuration(workout.estimatedDurationSec),
                                workout.steps.size,
                                workout.schemaVersion,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        workout.summary?.let { summary ->
                            Text(
                                text = summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWorkoutsCard(
    onCreateWorkout: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.training_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.training_empty_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onCreateWorkout) {
                Text(text = stringResource(R.string.training_create_action))
            }
        }
    }
}

@Composable
private fun WorkoutDetailCard(
    workout: Workout,
    onEditWorkout: () -> Unit,
    onSaveCopy: () -> Unit,
    onStartWorkout: () -> Unit,
    onDeleteWorkout: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = workout.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.training_detail_meta,
                            workout.schemaVersion,
                            formatDuration(workout.estimatedDurationSec),
                            workout.steps.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(text = formatDuration(workout.estimatedDurationSec))
                    },
                )
            }

            workout.summary?.let { summary ->
                DetailTextBlock(
                    label = stringResource(R.string.training_field_summary),
                    value = summary,
                )
            }
            workout.goal?.let { goal ->
                DetailTextBlock(
                    label = stringResource(R.string.training_field_goal),
                    value = goal,
                )
            }
            workout.disclaimer?.let { disclaimer ->
                DetailTextBlock(
                    label = stringResource(R.string.training_field_disclaimer),
                    value = disclaimer,
                )
            }

            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.training_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                workout.steps.forEachIndexed { index, step ->
                    WorkoutStepDetailCard(
                        index = index,
                        step = step,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = onStartWorkout) {
                        Text(text = stringResource(R.string.training_start_action))
                    }
                    OutlinedButton(onClick = onEditWorkout) {
                        Text(text = stringResource(R.string.training_edit_action))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onSaveCopy) {
                        Text(text = stringResource(R.string.training_save_copy_action))
                    }
                    OutlinedButton(onClick = onDeleteWorkout) {
                        Text(text = stringResource(R.string.training_delete_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutEditorCard(
    state: WorkoutEditorState,
    onTitleChanged: (String) -> Unit,
    onSummaryChanged: (String) -> Unit,
    onGoalChanged: (String) -> Unit,
    onDisclaimerChanged: (String) -> Unit,
    onAddStep: () -> Unit,
    onRemoveStep: (Int) -> Unit,
    onStepTypeChanged: (Int, WorkoutStepType) -> Unit,
    onStepDurationChanged: (Int, String) -> Unit,
    onStepVoicePromptChanged: (Int, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.training_editor_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    R.string.training_editor_meta,
                    state.schemaVersion,
                    formatDuration(state.totalDurationSec),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_field_title),
                value = state.title,
                error = state.validationErrors.title,
                onValueChange = onTitleChanged,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_field_summary),
                value = state.summary,
                onValueChange = onSummaryChanged,
                singleLine = false,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_field_goal),
                value = state.goal,
                onValueChange = onGoalChanged,
                singleLine = false,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_field_disclaimer),
                value = state.disclaimer,
                onValueChange = onDisclaimerChanged,
                singleLine = false,
            )

            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.training_steps_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                state.validationErrors.stepsMessage?.let { error ->
                    ValidationText(error = error)
                }
                state.steps.forEachIndexed { index, step ->
                    WorkoutStepEditorCard(
                        index = index,
                        step = step,
                        errors = state.validationErrors.stepErrors.getOrNull(index) ?: WorkoutStepValidationErrors(),
                        canRemove = state.steps.size > 1,
                        onRemove = { onRemoveStep(index) },
                        onTypeChanged = { value -> onStepTypeChanged(index, value) },
                        onDurationChanged = { value -> onStepDurationChanged(index, value) },
                        onVoicePromptChanged = { value -> onStepVoicePromptChanged(index, value) },
                    )
                }
                OutlinedButton(onClick = onAddStep) {
                    Text(text = stringResource(R.string.training_add_step_action))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSave,
                    enabled = !state.isSaving,
                ) {
                    Text(text = stringResource(R.string.training_save_action))
                }
                OutlinedButton(
                    onClick = onDismiss,
                    enabled = !state.isSaving,
                ) {
                    Text(text = stringResource(R.string.training_cancel_action))
                }
            }
        }
    }
}

@Composable
private fun WorkoutStepEditorCard(
    index: Int,
    step: WorkoutStepDraft,
    errors: WorkoutStepValidationErrors,
    canRemove: Boolean,
    onRemove: () -> Unit,
    onTypeChanged: (WorkoutStepType) -> Unit,
    onDurationChanged: (String) -> Unit,
    onVoicePromptChanged: (String) -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.training_step_card_title, index + 1),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (canRemove) {
                    TextButton(onClick = onRemove) {
                        Text(text = stringResource(R.string.training_remove_step_action))
                    }
                }
            }
            StepTypeSection(
                selected = step.type,
                onTypeChanged = onTypeChanged,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_step_field_duration),
                value = step.durationSec,
                error = errors.durationSec,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = onDurationChanged,
            )
            TrainingTextInput(
                label = stringResource(R.string.training_step_field_voice_prompt),
                value = step.voicePrompt,
                error = errors.voicePrompt,
                onValueChange = onVoicePromptChanged,
                singleLine = false,
            )
        }
    }
}

@Composable
private fun WorkoutStepDetailCard(
    index: Int,
    step: WorkoutStep,
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.training_step_card_title, index + 1),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    R.string.training_step_detail_meta,
                    stringResource(step.type.labelRes()),
                    formatDuration(step.durationSec),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = step.voicePrompt,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun DetailTextBlock(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun StepTypeSection(
    selected: WorkoutStepType,
    onTypeChanged: (WorkoutStepType) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WorkoutStepType.entries.forEach { type ->
            FilterChip(
                selected = type == selected,
                onClick = { onTypeChanged(type) },
                label = {
                    Text(text = stringResource(type.labelRes()))
                },
            )
        }
    }
}

@Composable
private fun TrainingTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = label) },
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            isError = error != null,
        )
        if (error != null) {
            ValidationText(error = error)
        }
    }
}

@Composable
private fun ValidationText(
    error: String,
) {
    Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}

@Composable
private fun DeleteWorkoutDialog(
    workoutTitle: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.training_delete_dialog_title))
        },
        text = {
            Text(
                text = stringResource(
                    R.string.training_delete_dialog_body,
                    workoutTitle,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(R.string.training_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.training_cancel_action))
            }
        },
    )
}

private fun WorkoutStepType.labelRes(): Int = when (this) {
    WorkoutStepType.Warmup -> R.string.training_step_type_warmup
    WorkoutStepType.Run -> R.string.training_step_type_run
    WorkoutStepType.Walk -> R.string.training_step_type_walk
    WorkoutStepType.Cooldown -> R.string.training_step_type_cooldown
    WorkoutStepType.Rest -> R.string.training_step_type_rest
}

private fun formatDuration(totalDurationSec: Int): String {
    val minutes = totalDurationSec / 60
    val seconds = totalDurationSec % 60
    return when {
        minutes > 0 && seconds > 0 -> "$minutes мин $seconds с"
        minutes > 0 -> "$minutes мин"
        else -> "$seconds с"
    }
}
