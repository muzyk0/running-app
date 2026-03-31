package com.vladislav.runningapp.activity.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.activity.ActivityRepository
import com.vladislav.runningapp.activity.CompletedActivitySession
import com.vladislav.runningapp.core.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HistoryUiState(
    val isLoading: Boolean = true,
    val sessions: List<CompletedActivitySession> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val activityRepository: ActivityRepository,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val mutableUiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = mutableUiState.asStateFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            activityRepository.observeCompletedSessions().collect { sessions ->
                mutableUiState.update { state ->
                    state.copy(
                        isLoading = false,
                        sessions = sessions,
                    )
                }
            }
        }
    }
}
