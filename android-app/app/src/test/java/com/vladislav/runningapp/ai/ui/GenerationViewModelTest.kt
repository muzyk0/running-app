package com.vladislav.runningapp.ai.ui

import com.vladislav.runningapp.ai.domain.GenerateWorkoutUseCase
import com.vladislav.runningapp.ai.domain.TrainingGenerationRepository
import com.vladislav.runningapp.ai.domain.TrainingGenerationResult
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import com.vladislav.runningapp.profile.AdditionalPromptField
import com.vladislav.runningapp.profile.FitnessLevel
import com.vladislav.runningapp.profile.ProfileRepository
import com.vladislav.runningapp.profile.UserProfile
import com.vladislav.runningapp.profile.UserSex
import com.vladislav.runningapp.training.WorkoutRepository
import com.vladislav.runningapp.training.domain.DefaultWorkoutSchemaVersion
import com.vladislav.runningapp.training.domain.Workout
import com.vladislav.runningapp.training.domain.WorkoutStep
import com.vladislav.runningapp.training.domain.WorkoutStepType
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GenerationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun savesGeneratedWorkoutAndEmitsNavigationEvent() = runTest(mainDispatcherRule.dispatcher) {
        val profileRepository = FakeProfileRepository(profile = sampleUserProfile())
        val workoutRepository = FakeWorkoutRepository()
        val generatedWorkout = sampleGeneratedWorkout()
        val viewModel = GenerationViewModel(
            profileRepository = profileRepository,
            workoutRepository = workoutRepository,
            generateWorkoutUseCase = GenerateWorkoutUseCase(
                repository = object : TrainingGenerationRepository {
                    override suspend fun generateWorkout(
                        profile: UserProfile,
                        userNote: String?,
                    ): TrainingGenerationResult = TrainingGenerationResult.Success(generatedWorkout)
                },
            ),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val navigationEvent = async { viewModel.navigationEvents.first() }
        viewModel.onAcceptGeneratedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val savedWorkout = workoutRepository.savedWorkouts.single()
        val event = navigationEvent.await() as GenerationNavigationEvent.OpenSavedWorkout

        assertEquals(generatedWorkout.title, savedWorkout.title)
        assertNotEquals(generatedWorkout.id, savedWorkout.id)
        assertEquals(event.workoutId, savedWorkout.id)
        assertTrue(viewModel.uiState.value.generatedWorkout != null)
    }

    private class FakeProfileRepository(
        profile: UserProfile?,
    ) : ProfileRepository {
        private val state = MutableStateFlow(profile)

        override fun observeProfile(): Flow<UserProfile?> = state

        override suspend fun getProfile(): UserProfile? = state.value

        override suspend fun saveProfile(profile: UserProfile) {
            state.value = profile
        }
    }

    private class FakeWorkoutRepository : WorkoutRepository {
        val savedWorkouts = mutableListOf<Workout>()

        override fun observeWorkouts(): Flow<List<Workout>> = flowOf(savedWorkouts.toList())

        override fun observeWorkout(workoutId: String): Flow<Workout?> =
            flowOf(savedWorkouts.firstOrNull { workout -> workout.id == workoutId })

        override suspend fun getWorkout(workoutId: String): Workout? =
            savedWorkouts.firstOrNull { workout -> workout.id == workoutId }

        override suspend fun saveWorkout(workout: Workout) {
            savedWorkouts += workout
        }

        override suspend fun deleteWorkout(workoutId: String) {
            savedWorkouts.removeAll { workout -> workout.id == workoutId }
        }
    }
}

private fun sampleGeneratedWorkout(): Workout = Workout(
    id = "generated-preview",
    schemaVersion = DefaultWorkoutSchemaVersion,
    title = "Интервалы для старта",
    summary = "Чередование бега и ходьбы",
    goal = "Build consistency",
    disclaimer = "Приложение не является медицинской рекомендацией.",
    steps = listOf(
        WorkoutStep(
            type = WorkoutStepType.Warmup,
            durationSec = 180,
            voicePrompt = "Разминка.",
        ),
        WorkoutStep(
            type = WorkoutStepType.Run,
            durationSec = 300,
            voicePrompt = "Легкий бег.",
        ),
    ),
)

private fun sampleUserProfile(): UserProfile = UserProfile(
    heightCm = 180,
    weightKg = 77,
    sex = UserSex.Male,
    age = 31,
    trainingDaysPerWeek = 4,
    fitnessLevel = FitnessLevel.Beginner,
    injuriesAndLimitations = "none",
    trainingGoal = "Build consistency",
    additionalPromptFields = listOf(
        AdditionalPromptField(
            label = "Поверхность",
            value = "Стадион",
        ),
    ),
)
