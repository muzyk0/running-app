package com.vladislav.runningapp.ai.data.remote

import com.vladislav.runningapp.core.i18n.SupportedAppLocale
import com.vladislav.runningapp.profile.AdditionalPromptField
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import org.junit.Assert.assertEquals
import org.junit.Test

class TrainingGenerationMappersTest {
    @Test
    fun mapsUserProfileIntoRequestDto() {
        val profile = UserProfile(
            heightCm = 180,
            weightKg = 77,
            sex = UserSex.Male,
            age = 31,
            trainingDaysPerWeek = 4,
            fitnessLevel = FitnessLevel.Beginner,
            injuriesAndLimitations = "  none  ",
            trainingGoal = "  Build consistency  ",
            additionalPromptFields = listOf(
                AdditionalPromptField(
                    label = " Поверхность ",
                    value = " Стадион ",
                ),
            ),
        )

        val dto = profile.toGenerateTrainingRequestDto(
            userNote = "Без ускорений",
            locale = SupportedAppLocale.EnglishUs,
        )

        assertEquals(180, dto.profile.heightCm)
        assertEquals("male", dto.profile.sex)
        assertEquals("none", dto.profile.injuriesAndLimitations)
        assertEquals("Build consistency", dto.profile.trainingGoal)
        assertEquals("Поверхность", dto.profile.additionalPromptFields.single().label)
        assertEquals("Стадион", dto.profile.additionalPromptFields.single().value)
        assertEquals("en-US", dto.request.locale)
        assertEquals("Без ускорений", dto.request.userNote)
    }

    @Test
    fun mapsRemoteResponseIntoTransportEnvelope() {
        val response = GenerateTrainingResponseDto(
            schemaVersion = "mvp.v1",
            training = RemoteWorkoutDto(
                title = " Интервалы ",
                summary = " Легкий старт ",
                goal = " Адаптация ",
                estimatedDurationSec = 480,
                disclaimer = " Дисклеймер ",
                steps = listOf(
                    RemoteWorkoutStepDto(
                        id = "step-1",
                        type = "warmup_walk",
                        durationSec = 180,
                        voicePrompt = " Разминка. ",
                    ),
                ),
            ),
        )

        val envelope = response.toWorkoutEnvelopeDto()

        assertEquals("mvp.v1", envelope.schemaVersion)
        assertEquals("Интервалы", envelope.training.title)
        assertEquals(1, envelope.training.steps.size)
        assertEquals("warmup_walk", envelope.training.steps.single().type)
        assertEquals(180, envelope.training.steps.single().durationSec)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankSchemaVersionInCompletedPayload() {
        GenerateTrainingResponseDto(
            schemaVersion = " ",
            training = RemoteWorkoutDto(
                title = "Интервалы",
                steps = listOf(
                    RemoteWorkoutStepDto(
                        id = "step-1",
                        type = "run",
                        durationSec = 180,
                        voicePrompt = "Бежим.",
                    ),
                ),
            ),
        ).toWorkoutEnvelopeDto()
    }
}
