package com.vladislav.runningapp.core.navigation

data class RunningAppNavigationState(
    val currentRoute: String? = null,
    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries,
) {
    val currentTopLevelDestination: TopLevelDestination
        get() = topLevelDestinations.firstOrNull { destination ->
            destination.matches(currentRoute)
        } ?: TopLevelDestination.Profile
}
