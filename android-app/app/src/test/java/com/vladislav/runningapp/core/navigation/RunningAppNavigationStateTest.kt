package com.vladislav.runningapp.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RunningAppNavigationStateTest {
    @Test
    fun usesProfileAsFallbackForUnknownRoute() {
        val state = RunningAppNavigationState(currentRoute = "unknown")

        assertEquals(TopLevelDestination.Profile, state.currentTopLevelDestination)
    }

    @Test
    fun matchesTopLevelDestinationsByRoutePrefix() {
        val state = RunningAppNavigationState(currentRoute = "free-run/live")

        assertEquals(TopLevelDestination.FreeRun, state.currentTopLevelDestination)
        assertTrue(TopLevelDestination.FreeRun.matches("free-run/live"))
    }

    @Test
    fun matchesTopLevelDestinationsByRouteQuery() {
        val state = RunningAppNavigationState(currentRoute = "training?focusedWorkoutId=local-1")

        assertEquals(TopLevelDestination.Training, state.currentTopLevelDestination)
        assertTrue(TopLevelDestination.Training.matches("training?focusedWorkoutId=local-1"))
    }
}
