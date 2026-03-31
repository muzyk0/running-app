package com.vladislav.runningapp.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        assertNotNull(result.errors.heightCm)
        assertNotNull(result.errors.weightKg)
        assertNotNull(result.errors.sex)
        assertNotNull(result.errors.age)
        assertNotNull(result.errors.trainingDaysPerWeek)
        assertNotNull(result.errors.fitnessLevel)
        assertNotNull(result.errors.injuriesAndLimitations)
        assertNotNull(result.errors.trainingGoal)
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
        assertNotNull(result.errors.additionalPromptFields.first().value)
    }

    private fun assertFalse(value: Boolean) {
        assertTrue(!value)
    }
}
