package com.vladislav.runningapp.core.startup

import com.vladislav.runningapp.core.navigation.TopLevelDestination
import javax.inject.Inject
import javax.inject.Singleton

data class AppBootstrap(
    val startDestination: TopLevelDestination,
)

interface StartupDestinationResolver {
    fun resolve(): TopLevelDestination
}

@Singleton
class DefaultStartupDestinationResolver @Inject constructor() : StartupDestinationResolver {
    override fun resolve(): TopLevelDestination = TopLevelDestination.Profile
}

@Singleton
class AppBootstrapper @Inject constructor(
    private val startupDestinationResolver: StartupDestinationResolver,
) {
    fun bootstrap(): AppBootstrap = AppBootstrap(
        startDestination = startupDestinationResolver.resolve(),
    )
}
