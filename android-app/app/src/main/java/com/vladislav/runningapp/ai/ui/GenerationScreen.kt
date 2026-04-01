package com.vladislav.runningapp.ai.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.asString
import com.vladislav.runningapp.core.i18n.formatWorkoutDurationLabel
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType

@Composable
fun GenerationScreen(
    onOpenProfile: () -> Unit,
    onOpenTraining: (String) -> Unit,
    viewModel: GenerationViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.navigationEvents.collect { event ->
            when (event) {
                is GenerationNavigationEvent.OpenSavedWorkout -> onOpenTraining(event.workoutId)
            }
        }
    }

    GenerationScreen(
        state = uiState,
        onOpenProfile = onOpenProfile,
        onUserNoteChanged = viewModel::onUserNoteChanged,
        onDismissError = viewModel::onDismissError,
        onGenerateWorkout = viewModel::onGenerateWorkout,
        onAcceptGeneratedWorkout = viewModel::onAcceptGeneratedWorkout,
    )
}

@Composable
private fun GenerationScreen(
    state: GenerationUiState,
    onOpenProfile: () -> Unit,
    onUserNoteChanged: (String) -> Unit,
    onDismissError: () -> Unit,
    onGenerateWorkout: () -> Unit,
    onAcceptGeneratedWorkout: () -> Unit,
) {
    if (state.isLoadingProfile) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GenerationIntroCard()

        if (state.profile == null) {
            MissingProfileCard(onOpenProfile = onOpenProfile)
        } else {
            ProfileSnapshotCard(profile = state.profile)
            RequestCard(
                userNote = state.userNote,
                isGenerating = state.isGenerating,
                canGenerate = state.canGenerate,
                onUserNoteChanged = onUserNoteChanged,
                onGenerateWorkout = onGenerateWorkout,
            )
        }

        if (state.shouldShowGenerationOutput) {
            GenerationOutputCard(
                output = state.generationOutput,
                isGenerating = state.isGenerating,
                isWorkoutReady = state.isWorkoutReady,
                hasStreamError = state.streamErrorMessage != null,
            )
        }

        state.streamErrorMessage?.let { message ->
            ErrorCard(
                title = stringResource(R.string.generation_stream_error_title),
                message = message.asString(),
                onDismiss = onDismissError,
            )
        }

        state.errorMessage?.let { message ->
            ErrorCard(
                title = stringResource(R.string.generation_error_title),
                message = message.asString(),
                onDismiss = onDismissError,
            )
        }

        if (state.isWorkoutReady) {
            state.generatedWorkout?.let { workout ->
                GeneratedWorkoutPreviewCard(
                    workout = workout,
                    isSaving = state.isSaving,
                    canSave = state.canSaveGeneratedWorkout,
                    onAcceptGeneratedWorkout = onAcceptGeneratedWorkout,
                )
            }
        }
    }
}

@Composable
private fun GenerationIntroCard() {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_intro_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.generation_intro_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MissingProfileCard(
    onOpenProfile: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_profile_missing_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.generation_profile_missing_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenProfile) {
                Text(text = stringResource(R.string.generation_open_profile_action))
            }
        }
    }
}

@Composable
private fun ProfileSnapshotCard(
    profile: UserProfile,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_profile_snapshot_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_height),
                value = stringResource(R.string.profile_value_height, profile.heightCm),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_weight),
                value = stringResource(R.string.profile_value_weight, profile.weightKg),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_sex),
                value = stringResource(profile.sex.labelRes()),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_age),
                value = stringResource(R.string.profile_value_age, profile.age),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_training_days),
                value = stringResource(R.string.profile_value_training_days, profile.trainingDaysPerWeek),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_fitness_level),
                value = stringResource(profile.fitnessLevel.labelRes()),
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_injuries),
                value = profile.injuriesAndLimitations,
            )
            GenerationSummaryRow(
                label = stringResource(R.string.profile_field_goal),
                value = profile.trainingGoal,
            )
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = stringResource(
                            R.string.generation_profile_additional_fields_count,
                            profile.additionalPromptFields.size,
                        ),
                    )
                },
            )
            if (profile.additionalPromptFields.isNotEmpty()) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    profile.additionalPromptFields.forEach { field ->
                        GenerationSummaryRow(
                            label = field.label,
                            value = field.value,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestCard(
    userNote: String,
    isGenerating: Boolean,
    canGenerate: Boolean,
    onUserNoteChanged: (String) -> Unit,
    onGenerateWorkout: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_request_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.generation_request_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = userNote,
                onValueChange = onUserNoteChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(text = stringResource(R.string.generation_user_note_label)) },
                placeholder = { Text(text = stringResource(R.string.generation_user_note_hint)) },
                minLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Sentences,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(text = stringResource(R.string.generation_locale_fixed)) },
                )
                Button(
                    onClick = onGenerateWorkout,
                    enabled = canGenerate,
                ) {
                    Text(
                        text = if (isGenerating) {
                            stringResource(R.string.generation_generating_status)
                        } else {
                            stringResource(R.string.generation_generate_action)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    title: String,
    message: String,
    onDismiss: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.generation_error_dismiss))
            }
        }
    }
}

@Composable
private fun GenerationOutputCard(
    output: String,
    isGenerating: Boolean,
    isWorkoutReady: Boolean,
    hasStreamError: Boolean,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_output_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(
                    when {
                        isGenerating -> R.string.generation_output_status_running
                        isWorkoutReady -> R.string.generation_output_status_completed
                        hasStreamError -> R.string.generation_output_status_failed
                        else -> R.string.generation_output_status_idle
                    },
                ),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            if (isGenerating) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator()
                    Text(
                        text = stringResource(R.string.generation_output_waiting),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider()
            SelectionContainer {
                Text(
                    text = output.ifBlank { stringResource(R.string.generation_output_empty) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun GeneratedWorkoutPreviewCard(
    workout: Workout,
    isSaving: Boolean,
    canSave: Boolean,
    onAcceptGeneratedWorkout: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.generation_preview_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = workout.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.generation_preview_meta,
                    formatWorkoutDurationLabel(workout.estimatedDurationSec),
                    workout.steps.size,
                    workout.schemaVersion,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            workout.summary?.let { summary ->
                GenerationSummaryRow(
                    label = stringResource(R.string.training_field_summary),
                    value = summary,
                )
            }
            workout.goal?.let { goal ->
                GenerationSummaryRow(
                    label = stringResource(R.string.training_field_goal),
                    value = goal,
                )
            }
            workout.disclaimer?.let { disclaimer ->
                GenerationSummaryRow(
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
                    GeneratedWorkoutStepCard(
                        index = index,
                        step = step,
                    )
                }
            }

            Button(
                onClick = onAcceptGeneratedWorkout,
                enabled = canSave,
            ) {
                Text(
                    text = if (isSaving) {
                        stringResource(R.string.generation_saving_action)
                    } else {
                        stringResource(R.string.generation_save_action)
                    },
                )
            }
        }
    }
}

@Composable
private fun GeneratedWorkoutStepCard(
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
                    formatWorkoutDurationLabel(step.durationSec),
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
private fun GenerationSummaryRow(
    label: String,
    value: String,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun UserSex.labelRes(): Int = when (this) {
    UserSex.Female -> R.string.profile_sex_female
    UserSex.Male -> R.string.profile_sex_male
    UserSex.Other -> R.string.profile_sex_other
}

private fun FitnessLevel.labelRes(): Int = when (this) {
    FitnessLevel.Beginner -> R.string.profile_level_beginner
    FitnessLevel.Intermediate -> R.string.profile_level_intermediate
    FitnessLevel.Advanced -> R.string.profile_level_advanced
}

private fun WorkoutStepType.labelRes(): Int = when (this) {
    WorkoutStepType.Warmup -> R.string.training_step_type_warmup
    WorkoutStepType.Run -> R.string.training_step_type_run
    WorkoutStepType.Walk -> R.string.training_step_type_walk
    WorkoutStepType.Cooldown -> R.string.training_step_type_cooldown
    WorkoutStepType.Rest -> R.string.training_step_type_rest
}
