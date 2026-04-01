package com.vladislav.runningapp.ai.ui

import com.vladislav.runningapp.ai.domain.GenerateWorkoutUseCase
import com.vladislav.runningapp.ai.domain.TrainingGenerationError
import com.vladislav.runningapp.ai.domain.TrainingGenerationErrorCode
import com.vladislav.runningapp.ai.domain.TrainingGenerationFailureSource
import com.vladislav.runningapp.ai.domain.TrainingGenerationRepository
import com.vladislav.runningapp.ai.domain.TrainingGenerationUpdate
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class GenerationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun accumulatesLogOutputAndKeepsSaveDisabledUntilCompleted() = runTest(mainDispatcherRule.dispatcher) {
        val generatedWorkout = sampleGeneratedWorkout()
        val generationStream = ControlledGenerationStream()
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(generationStream.asFlow()),
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
        assertEquals("", viewModel.uiState.value.generationOutput)
        assertNull(viewModel.uiState.value.generatedWorkout)

        generationStream.emit(TrainingGenerationUpdate.Log("Building training prompt"))
        generationStream.emit(TrainingGenerationUpdate.Log("Waiting for provider output"))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(
            "Building training prompt\nWaiting for provider output",
            viewModel.uiState.value.generationOutput,
        )
        assertTrue(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
        assertNull(viewModel.uiState.value.generatedWorkout)

        generationStream.emit(TrainingGenerationUpdate.Completed(generatedWorkout))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertTrue(viewModel.uiState.value.isWorkoutReady)
        assertEquals(generatedWorkout, viewModel.uiState.value.generatedWorkout)
        assertNull(viewModel.uiState.value.streamErrorMessage)
        assertTrue(viewModel.uiState.value.canSaveGeneratedWorkout)
    }

    @Test
    fun clearsPreviousOutputAndWorkoutWhenNewGenerationStarts() = runTest(mainDispatcherRule.dispatcher) {
        val firstStream = ControlledGenerationStream()
        val secondStream = ControlledGenerationStream()
        val viewModel = createViewModel(
            repository = QueuedTrainingGenerationRepository(
                firstStream.asFlow(),
                secondStream.asFlow(),
            ),
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        firstStream.emit(TrainingGenerationUpdate.Log("First request log"))
        firstStream.emit(TrainingGenerationUpdate.Completed(sampleGeneratedWorkout()))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("First request log", viewModel.uiState.value.generationOutput)
        assertTrue(viewModel.uiState.value.isWorkoutReady)

        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isGenerating)
        assertEquals("", viewModel.uiState.value.generationOutput)
        assertNull(viewModel.uiState.value.generatedWorkout)
        assertNull(viewModel.uiState.value.streamErrorMessage)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)

        secondStream.emit(
            TrainingGenerationUpdate.Failure(
                error = TrainingGenerationError(
                    code = TrainingGenerationErrorCode.ProviderError,
                    message = "provider failed",
                ),
            ),
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isGenerating)
        assertEquals("", viewModel.uiState.value.generationOutput)
        assertNull(viewModel.uiState.value.generatedWorkout)
        assertEquals("provider failed", viewModel.uiState.value.streamErrorMessage)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
    }

    @Test
    fun surfacesStreamFailureAndKeepsWorkoutUnavailable() = runTest(mainDispatcherRule.dispatcher) {
        val generationStream = ControlledGenerationStream()
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(generationStream.asFlow()),
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        generationStream.emit(TrainingGenerationUpdate.Log("Building training prompt"))
        generationStream.emit(
            TrainingGenerationUpdate.Failure(
                error = TrainingGenerationError(
                    code = TrainingGenerationErrorCode.ProviderError,
                    message = "provider failed",
                ),
            ),
        )
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("Building training prompt", viewModel.uiState.value.generationOutput)
        assertEquals("provider failed", viewModel.uiState.value.streamErrorMessage)
        assertFalse(viewModel.uiState.value.isGenerating)
        assertFalse(viewModel.uiState.value.isWorkoutReady)
        assertNull(viewModel.uiState.value.generatedWorkout)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
    }

    @Test
    fun routesRequestFailuresToGeneralErrorWithoutShowingStreamOutput() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(
                flowOf(
                    TrainingGenerationUpdate.Failure(
                        error = TrainingGenerationError(
                            code = TrainingGenerationErrorCode.InvalidRequest,
                            message = "profile.training_goal is required",
                        ),
                        source = TrainingGenerationFailureSource.Request,
                    ),
                ),
            ),
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals("profile.training_goal is required", viewModel.uiState.value.errorMessage)
        assertNull(viewModel.uiState.value.streamErrorMessage)
        assertFalse(viewModel.uiState.value.shouldShowGenerationOutput)
        assertFalse(viewModel.uiState.value.isGenerating)
    }

    @Test
    fun ignoresGenerateRequestsWhileStreamIsAlreadyRunning() = runTest(mainDispatcherRule.dispatcher) {
        val generationStream = ControlledGenerationStream()
        val repository = CountingTrainingGenerationRepository(generationStream.asFlow())
        val viewModel = createViewModel(repository = repository)

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onGenerateWorkout()
        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.callCount)
        assertTrue(viewModel.uiState.value.isGenerating)

        generationStream.emit(TrainingGenerationUpdate.Completed(sampleGeneratedWorkout()))
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, repository.callCount)
        assertTrue(viewModel.uiState.value.isWorkoutReady)
    }

    @Test
    fun savesGeneratedWorkoutAndEmitsNavigationEvent() = runTest(mainDispatcherRule.dispatcher) {
        val generatedWorkout = sampleGeneratedWorkout()
        val workoutRepository = FakeWorkoutRepository()
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(
                flowOf(TrainingGenerationUpdate.Completed(generatedWorkout)),
            ),
            workoutRepository = workoutRepository,
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
        assertTrue(viewModel.uiState.value.isWorkoutReady)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
        assertEquals(savedWorkout.id, viewModel.uiState.value.savedWorkoutId)
    }

    @Test
    fun ignoresRepeatedSaveRequestsAfterGeneratedWorkoutWasSaved() = runTest(mainDispatcherRule.dispatcher) {
        val workoutRepository = FakeWorkoutRepository()
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(
                flowOf(TrainingGenerationUpdate.Completed(sampleGeneratedWorkout())),
            ),
            workoutRepository = workoutRepository,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        viewModel.onAcceptGeneratedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAcceptGeneratedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, workoutRepository.savedWorkouts.size)
        assertFalse(viewModel.uiState.value.canSaveGeneratedWorkout)
    }

    @Test
    fun surfacesErrorWhenProfileIsMissing() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = createViewModel(
            repository = object : TrainingGenerationRepository {
                override fun generateWorkout(
                    profile: UserProfile,
                    userNote: String?,
                ): Flow<TrainingGenerationUpdate> = error("generateWorkout should not be called without a profile")
            },
            profileRepository = FakeProfileRepository(profile = null),
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onGenerateWorkout()

        assertEquals(
            "Сначала заполните профиль, иначе backend не сможет собрать тренировку.",
            viewModel.uiState.value.errorMessage,
        )
    }

    @Test
    fun surfacesSaveFailureWhenAcceptingGeneratedWorkout() = runTest(mainDispatcherRule.dispatcher) {
        val workoutRepository = FakeWorkoutRepository(saveError = IllegalStateException("disk full"))
        val viewModel = createViewModel(
            repository = SingleShotTrainingGenerationRepository(
                flowOf(TrainingGenerationUpdate.Completed(sampleGeneratedWorkout())),
            ),
            workoutRepository = workoutRepository,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onGenerateWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onAcceptGeneratedWorkout()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertTrue(workoutRepository.savedWorkouts.isEmpty())
        assertEquals(
            "Не удалось сохранить тренировку локально. Повторите попытку.",
            viewModel.uiState.value.errorMessage,
        )
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.canSaveGeneratedWorkout)
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
        var saveError: Throwable? = null

        constructor(saveError: Throwable? = null) {
            this.saveError = saveError
        }

        override fun observeWorkouts(): Flow<List<Workout>> = flowOf(savedWorkouts.toList())

        override fun observeWorkout(workoutId: String): Flow<Workout?> =
            flowOf(savedWorkouts.firstOrNull { workout -> workout.id == workoutId })

        override suspend fun getWorkout(workoutId: String): Workout? =
            savedWorkouts.firstOrNull { workout -> workout.id == workoutId }

        override suspend fun saveWorkout(workout: Workout) {
            saveError?.let { throw it }
            savedWorkouts += workout
        }

        override suspend fun deleteWorkout(workoutId: String) {
            savedWorkouts.removeAll { workout -> workout.id == workoutId }
        }
    }

    private fun createViewModel(
        repository: TrainingGenerationRepository,
        profileRepository: ProfileRepository = FakeProfileRepository(profile = sampleUserProfile()),
        workoutRepository: WorkoutRepository = FakeWorkoutRepository(),
    ): GenerationViewModel = GenerationViewModel(
        profileRepository = profileRepository,
        workoutRepository = workoutRepository,
        generateWorkoutUseCase = GenerateWorkoutUseCase(repository = repository),
        defaultDispatcher = mainDispatcherRule.dispatcher,
    )

    private class SingleShotTrainingGenerationRepository(
        private val updates: Flow<TrainingGenerationUpdate>,
    ) : TrainingGenerationRepository {
        override fun generateWorkout(
            profile: UserProfile,
            userNote: String?,
        ): Flow<TrainingGenerationUpdate> = updates
    }

    private class QueuedTrainingGenerationRepository(
        vararg flows: Flow<TrainingGenerationUpdate>,
    ) : TrainingGenerationRepository {
        private val pendingFlows = ArrayDeque(flows.toList())

        override fun generateWorkout(
            profile: UserProfile,
            userNote: String?,
        ): Flow<TrainingGenerationUpdate> = pendingFlows.removeFirstOrNull()
            ?: error("No queued flow configured for generateWorkout")
    }

    private class CountingTrainingGenerationRepository(
        private val updates: Flow<TrainingGenerationUpdate>,
    ) : TrainingGenerationRepository {
        var callCount: Int = 0
            private set

        override fun generateWorkout(
            profile: UserProfile,
            userNote: String?,
        ): Flow<TrainingGenerationUpdate> {
            callCount += 1
            return updates
        }
    }

    private class ControlledGenerationStream {
        private val channel = Channel<TrainingGenerationUpdate>(Channel.UNLIMITED)

        fun emit(update: TrainingGenerationUpdate) {
            val result = channel.trySend(update)
            check(result.isSuccess) {
                "Failed to enqueue update: ${result.exceptionOrNull()?.message ?: "unknown error"}"
            }
        }

        fun asFlow(): Flow<TrainingGenerationUpdate> = flow {
            while (true) {
                val update = channel.receive()
                emit(update)
                if (update is TrainingGenerationUpdate.Completed || update is TrainingGenerationUpdate.Failure) {
                    break
                }
            }
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
