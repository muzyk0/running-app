package com.vladislav.runningapp.profile

import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.UiText
import com.vladislav.runningapp.core.i18n.uiText

data class AdditionalPromptFieldDraft(
    val label: String = "",
    val value: String = "",
)

data class ProfileDraft(
    val heightCm: String = "",
    val weightKg: String = "",
    val sex: UserSex? = null,
    val age: String = "",
    val trainingDaysPerWeek: String = "",
    val fitnessLevel: FitnessLevel? = null,
    val injuriesAndLimitations: String = "",
    val trainingGoal: String = "",
    val additionalPromptFields: List<AdditionalPromptFieldDraft> = emptyList(),
)

fun UserProfile.toDraft(): ProfileDraft = ProfileDraft(
    heightCm = heightCm.toString(),
    weightKg = weightKg.toString(),
    sex = sex,
    age = age.toString(),
    trainingDaysPerWeek = trainingDaysPerWeek.toString(),
    fitnessLevel = fitnessLevel,
    injuriesAndLimitations = injuriesAndLimitations,
    trainingGoal = trainingGoal,
    additionalPromptFields = additionalPromptFields.map { field ->
        AdditionalPromptFieldDraft(
            label = field.label,
            value = field.value,
        )
    },
)

data class AdditionalPromptFieldValidationErrors(
    val label: UiText? = null,
    val value: UiText? = null,
) {
    val hasErrors: Boolean
        get() = label != null || value != null
}

data class ProfileValidationErrors(
    val heightCm: UiText? = null,
    val weightKg: UiText? = null,
    val sex: UiText? = null,
    val age: UiText? = null,
    val trainingDaysPerWeek: UiText? = null,
    val fitnessLevel: UiText? = null,
    val injuriesAndLimitations: UiText? = null,
    val trainingGoal: UiText? = null,
    val additionalPromptFields: List<AdditionalPromptFieldValidationErrors> = emptyList(),
) {
    val hasErrors: Boolean
        get() = listOf(
            heightCm,
            weightKg,
            sex,
            age,
            trainingDaysPerWeek,
            fitnessLevel,
            injuriesAndLimitations,
            trainingGoal,
        ).any { it != null } || additionalPromptFields.any { it.hasErrors }
}

data class ProfileValidationResult(
    val profile: UserProfile? = null,
    val errors: ProfileValidationErrors = ProfileValidationErrors(),
)

object ProfileFormValidator {
    fun validate(draft: ProfileDraft): ProfileValidationResult {
        val additionalFieldErrors = mutableListOf<AdditionalPromptFieldValidationErrors>()
        val sanitizedAdditionalFields = mutableListOf<AdditionalPromptField>()

        draft.additionalPromptFields.forEach { field ->
            val label = field.label.trim()
            val value = field.value.trim()
            when {
                label.isBlank() && value.isBlank() -> {
                    additionalFieldErrors += AdditionalPromptFieldValidationErrors()
                }

                label.isBlank() -> {
                    additionalFieldErrors += AdditionalPromptFieldValidationErrors(
                        label = uiText(R.string.profile_validation_additional_field_label_required),
                    )
                }

                value.isBlank() -> {
                    additionalFieldErrors += AdditionalPromptFieldValidationErrors(
                        value = uiText(R.string.profile_validation_additional_field_value_required),
                    )
                }

                else -> {
                    additionalFieldErrors += AdditionalPromptFieldValidationErrors()
                    sanitizedAdditionalFields += AdditionalPromptField(
                        label = label,
                        value = value,
                    )
                }
            }
        }

        val errors = ProfileValidationErrors(
            heightCm = validateIntField(
                value = draft.heightCm,
                range = 50..300,
                emptyMessage = uiText(R.string.profile_validation_height_required),
                invalidMessage = uiText(R.string.profile_validation_height_invalid),
            ).error,
            weightKg = validateIntField(
                value = draft.weightKg,
                range = 20..400,
                emptyMessage = uiText(R.string.profile_validation_weight_required),
                invalidMessage = uiText(R.string.profile_validation_weight_invalid),
            ).error,
            sex = if (draft.sex == null) {
                uiText(R.string.profile_validation_sex_required)
            } else {
                null
            },
            age = validateIntField(
                value = draft.age,
                range = 10..120,
                emptyMessage = uiText(R.string.profile_validation_age_required),
                invalidMessage = uiText(R.string.profile_validation_age_invalid),
            ).error,
            trainingDaysPerWeek = validateIntField(
                value = draft.trainingDaysPerWeek,
                range = 1..7,
                emptyMessage = uiText(R.string.profile_validation_training_days_required),
                invalidMessage = uiText(R.string.profile_validation_training_days_invalid),
            ).error,
            fitnessLevel = if (draft.fitnessLevel == null) {
                uiText(R.string.profile_validation_fitness_level_required)
            } else {
                null
            },
            injuriesAndLimitations = validateTextField(
                value = draft.injuriesAndLimitations,
                emptyMessage = uiText(R.string.profile_validation_injuries_required),
            ),
            trainingGoal = validateTextField(
                value = draft.trainingGoal,
                emptyMessage = uiText(R.string.profile_validation_goal_required),
            ),
            additionalPromptFields = additionalFieldErrors,
        )

        if (errors.hasErrors) {
            return ProfileValidationResult(errors = errors)
        }

        return ProfileValidationResult(
            profile = UserProfile(
                heightCm = draft.heightCm.trim().toInt(),
                weightKg = draft.weightKg.trim().toInt(),
                sex = requireNotNull(draft.sex),
                age = draft.age.trim().toInt(),
                trainingDaysPerWeek = draft.trainingDaysPerWeek.trim().toInt(),
                fitnessLevel = requireNotNull(draft.fitnessLevel),
                injuriesAndLimitations = draft.injuriesAndLimitations.trim(),
                trainingGoal = draft.trainingGoal.trim(),
                additionalPromptFields = sanitizedAdditionalFields,
            ),
        )
    }

    private data class IntValidation(
        val error: UiText? = null,
    )

    private fun validateIntField(
        value: String,
        range: IntRange,
        emptyMessage: UiText,
        invalidMessage: UiText,
    ): IntValidation {
        val trimmed = value.trim()
        if (trimmed.isBlank()) {
            return IntValidation(error = emptyMessage)
        }

        val parsed = trimmed.toIntOrNull()
        if (parsed == null || parsed !in range) {
            return IntValidation(error = invalidMessage)
        }

        return IntValidation()
    }

    private fun validateTextField(
        value: String,
        emptyMessage: UiText,
    ): UiText? = if (value.trim().isBlank()) {
        emptyMessage
    } else {
        null
    }
}
