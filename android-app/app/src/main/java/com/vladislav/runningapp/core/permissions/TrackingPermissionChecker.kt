package com.vladislav.runningapp.core.permissions

import android.content.Context
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.i18n.uiText

val MissingTrackedSessionPermissionsMessage =
    uiText(R.string.permission_error_missing_tracked_session_permissions)

interface TrackingPermissionChecker {
    fun currentState(): PermissionRequirementsState
}

class AndroidTrackingPermissionChecker(
    private val context: Context,
) : TrackingPermissionChecker {
    override fun currentState(): PermissionRequirementsState = permissionRequirementsFor(
        PermissionSnapshotFactory.fromContext(context),
    )
}
