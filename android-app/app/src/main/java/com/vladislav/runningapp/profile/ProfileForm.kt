package com.vladislav.runningapp.profile

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
    val label: String? = null,
    val value: String? = null,
) {
    val hasErrors: Boolean
        get() = label != null || value != null
}

data class ProfileValidationErrors(
    val heightCm: String? = null,
    val weightKg: String? = null,
    val sex: String? = null,
    val age: String? = null,
    val trainingDaysPerWeek: String? = null,
    val fitnessLevel: String? = null,
    val injuriesAndLimitations: String? = null,
    val trainingGoal: String? = null,
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
                        label = "Добавьте название поля.",
                    )
                }

                value.isBlank() -> {
                    additionalFieldErrors += AdditionalPromptFieldValidationErrors(
                        value = "Заполните значение поля.",
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
                emptyMessage = "Укажите рост в сантиметрах.",
                invalidMessage = "Рост должен быть от 50 до 300 см.",
            ).error,
            weightKg = validateIntField(
                value = draft.weightKg,
                range = 20..400,
                emptyMessage = "Укажите вес в килограммах.",
                invalidMessage = "Вес должен быть от 20 до 400 кг.",
            ).error,
            sex = if (draft.sex == null) {
                "Выберите пол."
            } else {
                null
            },
            age = validateIntField(
                value = draft.age,
                range = 10..120,
                emptyMessage = "Укажите возраст.",
                invalidMessage = "Возраст должен быть от 10 до 120 лет.",
            ).error,
            trainingDaysPerWeek = validateIntField(
                value = draft.trainingDaysPerWeek,
                range = 1..7,
                emptyMessage = "Укажите количество тренировок в неделю.",
                invalidMessage = "Количество тренировок должно быть от 1 до 7.",
            ).error,
            fitnessLevel = if (draft.fitnessLevel == null) {
                "Выберите уровень подготовки."
            } else {
                null
            },
            injuriesAndLimitations = validateTextField(
                value = draft.injuriesAndLimitations,
                emptyMessage = "Опишите ограничения и травмы.",
            ),
            trainingGoal = validateTextField(
                value = draft.trainingGoal,
                emptyMessage = "Опишите цель тренировок.",
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
        val error: String? = null,
    )

    private fun validateIntField(
        value: String,
        range: IntRange,
        emptyMessage: String,
        invalidMessage: String,
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
        emptyMessage: String,
    ): String? = if (value.trim().isBlank()) {
        emptyMessage
    } else {
        null
    }
}
