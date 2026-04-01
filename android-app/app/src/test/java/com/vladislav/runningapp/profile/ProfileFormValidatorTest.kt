package com.vladislav.runningapp.profile

import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.uiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileFormValidatorTest {
    @Test
    fun validDraftProducesSanitizedProfile() {
        val result = ProfileFormValidator.validate(
            ProfileDraft(
                heightCm = " 182 ",
                weightKg = " 78 ",
                sex = UserSex.Male,
                age = " 33 ",
                trainingDaysPerWeek = " 4 ",
                fitnessLevel = FitnessLevel.Intermediate,
                injuriesAndLimitations = " колено после старой травмы ",
                trainingGoal = " подготовка к 10 км ",
                additionalPromptFields = listOf(
                    AdditionalPromptFieldDraft(label = "Предпочтение", value = "утро"),
                    AdditionalPromptFieldDraft(label = "", value = ""),
                ),
            ),
        )

        assertFalse(result.errors.hasErrors)
        val profile = requireNotNull(result.profile)
        assertEquals(182, profile.heightCm)
        assertEquals(78, profile.weightKg)
        assertEquals(UserSex.Male, profile.sex)
        assertEquals(33, profile.age)
        assertEquals(4, profile.trainingDaysPerWeek)
        assertEquals(FitnessLevel.Intermediate, profile.fitnessLevel)
        assertEquals("колено после старой травмы", profile.injuriesAndLimitations)
        assertEquals("подготовка к 10 км", profile.trainingGoal)
        assertEquals(
            listOf(AdditionalPromptField(label = "Предпочтение", value = "утро")),
            profile.additionalPromptFields,
        )
    }

    @Test
    fun missingRequiredFieldsProduceErrors() {
        val result = ProfileFormValidator.validate(ProfileDraft())

        assertTrue(result.errors.hasErrors)
        assertNull(result.profile)
        assertEquals(uiText(R.string.profile_validation_height_required), result.errors.heightCm)
        assertEquals(uiText(R.string.profile_validation_weight_required), result.errors.weightKg)
        assertEquals(uiText(R.string.profile_validation_sex_required), result.errors.sex)
        assertEquals(uiText(R.string.profile_validation_age_required), result.errors.age)
        assertEquals(uiText(R.string.profile_validation_training_days_required), result.errors.trainingDaysPerWeek)
        assertEquals(uiText(R.string.profile_validation_fitness_level_required), result.errors.fitnessLevel)
        assertEquals(uiText(R.string.profile_validation_injuries_required), result.errors.injuriesAndLimitations)
        assertEquals(uiText(R.string.profile_validation_goal_required), result.errors.trainingGoal)
    }

    @Test
    fun partialAdditionalPromptFieldProducesFieldLevelError() {
        val result = ProfileFormValidator.validate(
            ProfileDraft(
                heightCm = "170",
                weightKg = "65",
                sex = UserSex.Female,
                age = "29",
                trainingDaysPerWeek = "3",
                fitnessLevel = FitnessLevel.Beginner,
                injuriesAndLimitations = "нет",
                trainingGoal = "вернуться к регулярным пробежкам",
                additionalPromptFields = listOf(
                    AdditionalPromptFieldDraft(label = "Покрытие", value = ""),
                ),
            ),
        )

        assertTrue(result.errors.hasErrors)
        assertNull(result.profile)
        assertEquals(1, result.errors.additionalPromptFields.size)
        assertEquals(
            uiText(R.string.profile_validation_additional_field_value_required),
            result.errors.additionalPromptFields.first().value,
        )
    }

    private fun assertFalse(value: Boolean) {
        assertTrue(!value)
    }
}
