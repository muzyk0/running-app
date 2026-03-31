package com.vladislav.runningapp.core.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionSnapshotFactoryTest {
    @Test
    fun trackedSessionsRequirePreciseLocationPermission() {
        assertFalse(preciseTrackedSessionLocationGranted(fineLocationGranted = false))
        assertTrue(preciseTrackedSessionLocationGranted(fineLocationGranted = true))
    }
}
