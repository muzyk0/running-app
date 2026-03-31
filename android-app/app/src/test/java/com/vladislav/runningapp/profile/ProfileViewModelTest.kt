package com.vladislav.runningapp.profile

import com.vladislav.runningapp.core.datastore.DisclaimerPreferenceStore
import com.vladislav.runningapp.core.startup.MainDispatcherRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class ProfileViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun saveProfileKeepsDraftAndSurfacesErrorOnRepositoryFailure() = runTest(mainDispatcherRule.dispatcher) {
        val viewModel = ProfileViewModel(
            profileRepository = FakeProfileRepository(saveError = IllegalStateException("disk full")),
            disclaimerPreferenceStore = FakeDisclaimerPreferenceStore(),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()
        viewModel.onTextFieldChanged(ProfileTextField.HeightCm, "180")
        viewModel.onTextFieldChanged(ProfileTextField.WeightKg, "77")
        viewModel.onSexSelected(UserSex.Male)
        viewModel.onTextFieldChanged(ProfileTextField.Age, "31")
        viewModel.onTextFieldChanged(ProfileTextField.TrainingDaysPerWeek, "4")
        viewModel.onFitnessLevelSelected(FitnessLevel.Beginner)
        viewModel.onTextFieldChanged(ProfileTextField.InjuriesAndLimitations, "none")
        viewModel.onTextFieldChanged(ProfileTextField.TrainingGoal, "Build consistency")
        viewModel.onSaveProfile()
        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        assertNull(viewModel.uiState.value.savedProfile)
        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(
            "Не удалось сохранить профиль локально. Повторите попытку.",
            viewModel.uiState.value.errorMessage,
        )
    }

    private class FakeProfileRepository(
        private val saveError: Throwable? = null,
    ) : ProfileRepository {
        private val state = MutableStateFlow<UserProfile?>(null)

        override fun observeProfile(): Flow<UserProfile?> = state

        override suspend fun getProfile(): UserProfile? = state.value

        override suspend fun saveProfile(profile: UserProfile) {
            saveError?.let { throw it }
            state.value = profile
        }
    }

    private class FakeDisclaimerPreferenceStore : DisclaimerPreferenceStore {
        private val state = MutableStateFlow(false)

        override fun observeDisclaimerAccepted(): Flow<Boolean> = state

        override suspend fun readDisclaimerAccepted(): Boolean = state.value

        override suspend fun setDisclaimerAccepted(accepted: Boolean) {
            state.value = accepted
        }
    }
}
