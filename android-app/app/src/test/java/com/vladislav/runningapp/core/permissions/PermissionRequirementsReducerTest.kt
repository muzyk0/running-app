package com.vladislav.runningapp.core.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionRequirementsReducerTest {
    @Test
    fun marksNotificationsAsNotRequiredBelowAndroid13() {
        val state = permissionRequirementsFor(
            PermissionSnapshot(
                sdkInt = 32,
                locationGranted = true,
                notificationsGranted = false,
                foregroundTrackingConfigured = true,
            ),
        )

        assertEquals(RequirementState.Available, state.location)
        assertEquals(RequirementState.NotRequired, state.notifications)
        assertEquals(RequirementState.Available, state.foregroundTracking)
        assertTrue(state.canStartTrackedSessions)
    }

    @Test
    fun requiresNotificationsOnAndroid13AndAbove() {
        val state = permissionRequirementsFor(
            PermissionSnapshot(
                sdkInt = 33,
                locationGranted = true,
                notificationsGranted = false,
                foregroundTrackingConfigured = true,
            ),
        )

        assertEquals(RequirementState.Missing, state.notifications)
        assertFalse(state.canStartTrackedSessions)
    }

    @Test
    fun requiresForegroundTrackingConfigurationForTrackedSessions() {
        val state = permissionRequirementsFor(
            PermissionSnapshot(
                sdkInt = 36,
                locationGranted = true,
                notificationsGranted = true,
                foregroundTrackingConfigured = false,
            ),
        )

        assertEquals(RequirementState.Missing, state.foregroundTracking)
        assertFalse(state.canStartTrackedSessions)
    }
}
