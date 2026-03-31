package com.vladislav.runningapp.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.core.datastore.DisclaimerPreferenceStore
import com.vladislav.runningapp.core.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileTextField {
    HeightCm,
    WeightKg,
    Age,
    TrainingDaysPerWeek,
    InjuriesAndLimitations,
    TrainingGoal,
}

data class ProfileUiState(
    val isLoading: Boolean = true,
    val savedProfile: UserProfile? = null,
    val draft: ProfileDraft = ProfileDraft(),
    val validationErrors: ProfileValidationErrors = ProfileValidationErrors(),
    val isEditing: Boolean = false,
    val isDisclaimerAccepted: Boolean = false,
    val shouldShowDisclaimerDialog: Boolean = false,
) {
    val isOnboarding: Boolean
        get() = savedProfile == null
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val disclaimerPreferenceStore: DisclaimerPreferenceStore,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            val profile = profileRepository.getProfile()
            val disclaimerAccepted = disclaimerPreferenceStore.readDisclaimerAccepted()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    savedProfile = profile,
                    draft = profile?.toDraft() ?: ProfileDraft(),
                    isEditing = profile == null,
                    isDisclaimerAccepted = disclaimerAccepted,
                    shouldShowDisclaimerDialog = !disclaimerAccepted,
                )
            }
        }
    }

    fun onTextFieldChanged(
        field: ProfileTextField,
        value: String,
    ) {
        updateDraft { draft ->
            when (field) {
                ProfileTextField.HeightCm -> draft.copy(heightCm = value)
                ProfileTextField.WeightKg -> draft.copy(weightKg = value)
                ProfileTextField.Age -> draft.copy(age = value)
                ProfileTextField.TrainingDaysPerWeek -> draft.copy(trainingDaysPerWeek = value)
                ProfileTextField.InjuriesAndLimitations -> draft.copy(injuriesAndLimitations = value)
                ProfileTextField.TrainingGoal -> draft.copy(trainingGoal = value)
            }
        }
    }

    fun onSexSelected(sex: UserSex) {
        updateDraft { draft -> draft.copy(sex = sex) }
    }

    fun onFitnessLevelSelected(level: FitnessLevel) {
        updateDraft { draft -> draft.copy(fitnessLevel = level) }
    }

    fun onPromptFieldLabelChanged(
        index: Int,
        value: String,
    ) {
        updateDraft { draft ->
            draft.copy(
                additionalPromptFields = draft.additionalPromptFields.update(index) { field ->
                    field.copy(label = value)
                },
            )
        }
    }

    fun onPromptFieldValueChanged(
        index: Int,
        value: String,
    ) {
        updateDraft { draft ->
            draft.copy(
                additionalPromptFields = draft.additionalPromptFields.update(index) { field ->
                    field.copy(value = value)
                },
            )
        }
    }

    fun onAddPromptField() {
        updateDraft { draft ->
            draft.copy(
                additionalPromptFields = draft.additionalPromptFields + AdditionalPromptFieldDraft(),
            )
        }
    }

    fun onRemovePromptField(index: Int) {
        updateDraft { draft ->
            draft.copy(
                additionalPromptFields = draft.additionalPromptFields.filterIndexed { currentIndex, _ ->
                    currentIndex != index
                },
            )
        }
    }

    fun onStartEditing() {
        _uiState.update { state ->
            state.copy(
                isEditing = true,
                draft = state.savedProfile?.toDraft() ?: state.draft,
                validationErrors = ProfileValidationErrors(),
            )
        }
    }

    fun onCancelEditing() {
        _uiState.update { state ->
            val savedProfile = state.savedProfile ?: return@update state
            state.copy(
                isEditing = false,
                draft = savedProfile.toDraft(),
                validationErrors = ProfileValidationErrors(),
            )
        }
    }

    fun onShowDisclaimer() {
        _uiState.update { state ->
            state.copy(shouldShowDisclaimerDialog = true)
        }
    }

    fun onDismissDisclaimer() {
        _uiState.update { state ->
            state.copy(shouldShowDisclaimerDialog = false)
        }
    }

    fun onAcceptDisclaimer() {
        viewModelScope.launch(defaultDispatcher) {
            disclaimerPreferenceStore.setDisclaimerAccepted(accepted = true)
            _uiState.update { state ->
                state.copy(
                    isDisclaimerAccepted = true,
                    shouldShowDisclaimerDialog = false,
                )
            }
        }
    }

    fun onSaveProfile() {
        val currentDraft = _uiState.value.draft
        val validationResult = ProfileFormValidator.validate(currentDraft)
        if (validationResult.errors.hasErrors) {
            _uiState.update { state ->
                state.copy(validationErrors = validationResult.errors)
            }
            return
        }

        val profile = requireNotNull(validationResult.profile)
        viewModelScope.launch(defaultDispatcher) {
            profileRepository.saveProfile(profile)
            _uiState.update { state ->
                state.copy(
                    savedProfile = profile,
                    draft = profile.toDraft(),
                    validationErrors = ProfileValidationErrors(),
                    isEditing = false,
                )
            }
        }
    }

    private fun updateDraft(transform: (ProfileDraft) -> ProfileDraft) {
        _uiState.update { state ->
            state.copy(
                draft = transform(state.draft),
                validationErrors = ProfileValidationErrors(),
            )
        }
    }
}

private fun List<AdditionalPromptFieldDraft>.update(
    index: Int,
    transform: (AdditionalPromptFieldDraft) -> AdditionalPromptFieldDraft,
): List<AdditionalPromptFieldDraft> = mapIndexed { currentIndex, field ->
    if (currentIndex == index) {
        transform(field)
    } else {
        field
    }
}
