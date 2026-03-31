package com.vladislav.runningapp.core.startup

import com.vladislav.runningapp.core.navigation.TopLevelDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppStartupViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exposesReadyStateFromBootstrapper() {
        val viewModel = AppStartupViewModel(
            appBootstrapper = AppBootstrapper(
                startupDestinationResolver = object : StartupDestinationResolver {
                    override fun resolve(): TopLevelDestination = TopLevelDestination.Generation
                },
            ),
            defaultDispatcher = mainDispatcherRule.dispatcher,
        )

        assertTrue(viewModel.uiState.value is AppStartupUiState.Loading)

        mainDispatcherRule.dispatcher.scheduler.advanceUntilIdle()

        val readyState = viewModel.uiState.value as AppStartupUiState.Ready
        assertEquals(TopLevelDestination.Generation, readyState.bootstrap.startDestination)
    }
}
