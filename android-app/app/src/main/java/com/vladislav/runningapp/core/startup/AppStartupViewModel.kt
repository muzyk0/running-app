package com.vladislav.runningapp.core.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vladislav.runningapp.core.di.DefaultDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AppStartupUiState {
    data object Loading : AppStartupUiState

    data class Ready(
        val bootstrap: AppBootstrap,
    ) : AppStartupUiState
}

@HiltViewModel
class AppStartupViewModel @Inject constructor(
    private val appBootstrapper: AppBootstrapper,
    @param:DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AppStartupUiState>(AppStartupUiState.Loading)
    val uiState: StateFlow<AppStartupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(defaultDispatcher) {
            val bootstrap = appBootstrapper.bootstrap()
            _uiState.update { AppStartupUiState.Ready(bootstrap) }
        }
    }
}
