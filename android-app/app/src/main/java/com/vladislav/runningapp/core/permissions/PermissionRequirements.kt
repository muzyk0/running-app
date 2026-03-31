package com.vladislav.runningapp.core.permissions

enum class RequirementState {
    Pending,
    Available,
    Missing,
    NotRequired,
}

data class PermissionRequirementsState(
    val location: RequirementState = RequirementState.Pending,
    val notifications: RequirementState = RequirementState.Pending,
    val foregroundTracking: RequirementState = RequirementState.Pending,
) {
    val canStartTrackedSessions: Boolean
        get() = location.isSatisfied() &&
            notifications.isSatisfied() &&
            foregroundTracking.isSatisfied()
}

data class PermissionSnapshot(
    val sdkInt: Int,
    val locationGranted: Boolean,
    val notificationsGranted: Boolean,
    val foregroundTrackingConfigured: Boolean,
)

fun permissionRequirementsFor(snapshot: PermissionSnapshot): PermissionRequirementsState = PermissionRequirementsState(
    location = snapshot.locationGranted.toRequirementState(),
    notifications = if (snapshot.sdkInt >= 33) {
        snapshot.notificationsGranted.toRequirementState()
    } else {
        RequirementState.NotRequired
    },
    foregroundTracking = snapshot.foregroundTrackingConfigured.toRequirementState(),
)

private fun Boolean.toRequirementState(): RequirementState = if (this) {
    RequirementState.Available
} else {
    RequirementState.Missing
}

private fun RequirementState.isSatisfied(): Boolean = when (this) {
    RequirementState.Available, RequirementState.NotRequired -> true
    RequirementState.Pending, RequirementState.Missing -> false
}
