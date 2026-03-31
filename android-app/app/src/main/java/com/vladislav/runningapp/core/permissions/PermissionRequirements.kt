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

sealed interface PermissionRequirementsAction {
    data class SyncFromSystem(val snapshot: PermissionSnapshot) : PermissionRequirementsAction
}

object PermissionRequirementsReducer {
    fun reduce(
        currentState: PermissionRequirementsState,
        action: PermissionRequirementsAction,
    ): PermissionRequirementsState = when (action) {
        is PermissionRequirementsAction.SyncFromSystem -> currentState.copy(
            location = action.snapshot.locationGranted.toRequirementState(),
            notifications = if (action.snapshot.sdkInt >= 33) {
                action.snapshot.notificationsGranted.toRequirementState()
            } else {
                RequirementState.NotRequired
            },
            foregroundTracking = action.snapshot.foregroundTrackingConfigured.toRequirementState(),
        )
    }
}

private fun Boolean.toRequirementState(): RequirementState = if (this) {
    RequirementState.Available
} else {
    RequirementState.Missing
}

private fun RequirementState.isSatisfied(): Boolean = when (this) {
    RequirementState.Available, RequirementState.NotRequired -> true
    RequirementState.Pending, RequirementState.Missing -> false
}
