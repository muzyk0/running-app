package com.vladislav.runningapp.profile

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import com.vladislav.runningapp.core.i18n.UiText
import com.vladislav.runningapp.core.i18n.asString

@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ProfileScreen(
        state = uiState,
        onTextFieldChanged = viewModel::onTextFieldChanged,
        onSexSelected = viewModel::onSexSelected,
        onFitnessLevelSelected = viewModel::onFitnessLevelSelected,
        onPromptFieldLabelChanged = viewModel::onPromptFieldLabelChanged,
        onPromptFieldValueChanged = viewModel::onPromptFieldValueChanged,
        onAddPromptField = viewModel::onAddPromptField,
        onRemovePromptField = viewModel::onRemovePromptField,
        onStartEditing = viewModel::onStartEditing,
        onCancelEditing = viewModel::onCancelEditing,
        onShowDisclaimer = viewModel::onShowDisclaimer,
        onDismissDisclaimer = viewModel::onDismissDisclaimer,
        onAcceptDisclaimer = viewModel::onAcceptDisclaimer,
        onSaveProfile = viewModel::onSaveProfile,
    )
}

@Composable
private fun ProfileScreen(
    state: ProfileUiState,
    onTextFieldChanged: (ProfileTextField, String) -> Unit,
    onSexSelected: (UserSex) -> Unit,
    onFitnessLevelSelected: (FitnessLevel) -> Unit,
    onPromptFieldLabelChanged: (Int, String) -> Unit,
    onPromptFieldValueChanged: (Int, String) -> Unit,
    onAddPromptField: () -> Unit,
    onRemovePromptField: (Int) -> Unit,
    onStartEditing: () -> Unit,
    onCancelEditing: () -> Unit,
    onShowDisclaimer: () -> Unit,
    onDismissDisclaimer: () -> Unit,
    onAcceptDisclaimer: () -> Unit,
    onSaveProfile: () -> Unit,
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

    if (state.shouldShowDisclaimerDialog) {
        DisclaimerDialog(
            isAccepted = state.isDisclaimerAccepted,
            onDismiss = onDismissDisclaimer,
            onAccept = onAcceptDisclaimer,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ProfileIntroCard(
            isOnboarding = state.isOnboarding,
            isEditing = state.isEditing,
        )
        state.errorMessage?.let { message ->
            InlineErrorCard(message = message.asString())
        }
        DisclaimerStatusCard(
            isAccepted = state.isDisclaimerAccepted,
            onReviewDisclaimer = onShowDisclaimer,
            onAcceptDisclaimer = onAcceptDisclaimer,
        )
        if (state.isOnboarding || state.isEditing) {
            ProfileEditorCard(
                draft = state.draft,
                errors = state.validationErrors,
                savedProfile = state.savedProfile,
                onTextFieldChanged = onTextFieldChanged,
                onSexSelected = onSexSelected,
                onFitnessLevelSelected = onFitnessLevelSelected,
                onPromptFieldLabelChanged = onPromptFieldLabelChanged,
                onPromptFieldValueChanged = onPromptFieldValueChanged,
                onAddPromptField = onAddPromptField,
                onRemovePromptField = onRemovePromptField,
                isSaving = state.isSaving,
                onSaveProfile = onSaveProfile,
                onCancelEditing = onCancelEditing,
            )
        } else {
            val profile = requireNotNull(state.savedProfile)
            ProfileSummaryCard(
                profile = profile,
                onStartEditing = onStartEditing,
            )
        }
    }
}

@Composable
private fun ProfileIntroCard(
    isOnboarding: Boolean,
    isEditing: Boolean,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = when {
                    isOnboarding -> stringResource(R.string.profile_onboarding_title)
                    isEditing -> stringResource(R.string.profile_edit_title)
                    else -> stringResource(R.string.profile_summary_title)
                },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = when {
                    isOnboarding -> stringResource(R.string.profile_onboarding_body)
                    isEditing -> stringResource(R.string.profile_edit_body)
                    else -> stringResource(R.string.profile_summary_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DisclaimerStatusCard(
    isAccepted: Boolean,
    onReviewDisclaimer: () -> Unit,
    onAcceptDisclaimer: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_disclaimer_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (isAccepted) {
                    stringResource(R.string.profile_disclaimer_status_accepted)
                } else {
                    stringResource(R.string.profile_disclaimer_status_pending)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onReviewDisclaimer) {
                    Text(text = stringResource(R.string.profile_disclaimer_review))
                }
                if (!isAccepted) {
                    Button(onClick = onAcceptDisclaimer) {
                        Text(text = stringResource(R.string.profile_disclaimer_accept))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorCard(
    draft: ProfileDraft,
    errors: ProfileValidationErrors,
    savedProfile: UserProfile?,
    onTextFieldChanged: (ProfileTextField, String) -> Unit,
    onSexSelected: (UserSex) -> Unit,
    onFitnessLevelSelected: (FitnessLevel) -> Unit,
    onPromptFieldLabelChanged: (Int, String) -> Unit,
    onPromptFieldValueChanged: (Int, String) -> Unit,
    onAddPromptField: () -> Unit,
    onRemovePromptField: (Int) -> Unit,
    isSaving: Boolean,
    onSaveProfile: () -> Unit,
    onCancelEditing: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileTextInput(
                label = stringResource(R.string.profile_field_height),
                value = draft.heightCm,
                error = errors.heightCm,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { onTextFieldChanged(ProfileTextField.HeightCm, it) },
            )
            ProfileTextInput(
                label = stringResource(R.string.profile_field_weight),
                value = draft.weightKg,
                error = errors.weightKg,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { onTextFieldChanged(ProfileTextField.WeightKg, it) },
            )
            ChoiceSection(
                title = stringResource(R.string.profile_field_sex),
                error = errors.sex,
                options = UserSex.entries,
                selected = draft.sex,
                optionLabelRes = { option -> option.labelRes() },
                onSelect = onSexSelected,
            )
            ProfileTextInput(
                label = stringResource(R.string.profile_field_age),
                value = draft.age,
                error = errors.age,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { onTextFieldChanged(ProfileTextField.Age, it) },
            )
            ProfileTextInput(
                label = stringResource(R.string.profile_field_training_days),
                value = draft.trainingDaysPerWeek,
                error = errors.trainingDaysPerWeek,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChange = { onTextFieldChanged(ProfileTextField.TrainingDaysPerWeek, it) },
            )
            ChoiceSection(
                title = stringResource(R.string.profile_field_fitness_level),
                error = errors.fitnessLevel,
                options = FitnessLevel.entries,
                selected = draft.fitnessLevel,
                optionLabelRes = { option -> option.labelRes() },
                onSelect = onFitnessLevelSelected,
            )
            ProfileTextInput(
                label = stringResource(R.string.profile_field_injuries),
                value = draft.injuriesAndLimitations,
                error = errors.injuriesAndLimitations,
                singleLine = false,
                onValueChange = { onTextFieldChanged(ProfileTextField.InjuriesAndLimitations, it) },
            )
            ProfileTextInput(
                label = stringResource(R.string.profile_field_goal),
                value = draft.trainingGoal,
                error = errors.trainingGoal,
                singleLine = false,
                onValueChange = { onTextFieldChanged(ProfileTextField.TrainingGoal, it) },
            )

            HorizontalDivider()

            AdditionalPromptFieldsSection(
                fields = draft.additionalPromptFields,
                errors = errors.additionalPromptFields,
                onAddPromptField = onAddPromptField,
                onRemovePromptField = onRemovePromptField,
                onLabelChanged = onPromptFieldLabelChanged,
                onValueChanged = onPromptFieldValueChanged,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onSaveProfile,
                    enabled = !isSaving,
                ) {
                    Text(text = stringResource(R.string.profile_save))
                }
                if (savedProfile != null) {
                    OutlinedButton(
                        onClick = onCancelEditing,
                        enabled = !isSaving,
                    ) {
                        Text(text = stringResource(R.string.profile_cancel))
                    }
                }
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
private fun ProfileSummaryCard(
    profile: UserProfile,
    onStartEditing: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_height),
                value = stringResource(R.string.profile_value_height, profile.heightCm),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_weight),
                value = stringResource(R.string.profile_value_weight, profile.weightKg),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_sex),
                value = stringResource(profile.sex.labelRes()),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_age),
                value = stringResource(R.string.profile_value_age, profile.age),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_training_days),
                value = stringResource(
                    R.string.profile_value_training_days,
                    profile.trainingDaysPerWeek,
                ),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_fitness_level),
                value = stringResource(profile.fitnessLevel.labelRes()),
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_injuries),
                value = profile.injuriesAndLimitations,
            )
            ProfileSummaryRow(
                label = stringResource(R.string.profile_field_goal),
                value = profile.trainingGoal,
            )

            HorizontalDivider()

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_additional_fields_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (profile.additionalPromptFields.isEmpty()) {
                    Text(
                        text = stringResource(R.string.profile_additional_fields_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    profile.additionalPromptFields.forEach { field ->
                        ProfileSummaryRow(
                            label = field.label,
                            value = field.value,
                        )
                    }
                }
            }

            Button(
                modifier = Modifier.fillMaxWidth(),
                onClick = onStartEditing,
            ) {
                Text(text = stringResource(R.string.profile_edit_action))
            }
        }
    }
}

@Composable
private fun AdditionalPromptFieldsSection(
    fields: List<AdditionalPromptFieldDraft>,
    errors: List<AdditionalPromptFieldValidationErrors>,
    onAddPromptField: () -> Unit,
    onRemovePromptField: (Int) -> Unit,
    onLabelChanged: (Int, String) -> Unit,
    onValueChanged: (Int, String) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.profile_additional_fields_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
        Text(
            text = stringResource(R.string.profile_additional_fields_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        fields.forEachIndexed { index, field ->
            val fieldErrors = errors.getOrNull(index) ?: AdditionalPromptFieldValidationErrors()
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ProfileTextInput(
                        label = stringResource(R.string.profile_additional_field_label),
                        value = field.label,
                        error = fieldErrors.label,
                        onValueChange = { onLabelChanged(index, it) },
                    )
                    ProfileTextInput(
                        label = stringResource(R.string.profile_additional_field_value),
                        value = field.value,
                        error = fieldErrors.value,
                        singleLine = false,
                        onValueChange = { onValueChanged(index, it) },
                    )
                    TextButton(
                        onClick = { onRemovePromptField(index) },
                    ) {
                        Text(text = stringResource(R.string.profile_additional_field_remove))
                    }
                }
            }
        }
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAddPromptField,
        ) {
            Text(text = stringResource(R.string.profile_additional_field_add))
        }
    }
}

@Composable
private fun <T> ChoiceSection(
    title: String,
    error: UiText?,
    options: List<T>,
    selected: T?,
    optionLabelRes: (T) -> Int,
    onSelect: (T) -> Unit,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    label = { Text(text = stringResource(optionLabelRes(option))) },
                )
            }
        }
        if (error != null) {
            Text(
                text = error.asString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun ProfileTextInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    error: UiText? = null,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(text = label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = keyboardOptions,
        isError = error != null,
        supportingText = {
            if (error != null) {
                Text(text = error.asString())
            }
        },
    )
}

@Composable
private fun ProfileSummaryRow(
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

@Composable
private fun DisclaimerDialog(
    isAccepted: Boolean,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(R.string.profile_disclaimer_dialog_title))
        },
        text = {
            Text(
                text = if (isAccepted) {
                    stringResource(R.string.profile_disclaimer_body_acknowledged)
                } else {
                    stringResource(R.string.profile_disclaimer_body)
                },
            )
        },
        confirmButton = {
            Button(onClick = onAccept) {
                Text(text = stringResource(R.string.profile_disclaimer_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.profile_disclaimer_later))
            }
        },
    )
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
