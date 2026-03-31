package com.vladislav.runningapp.core.permissions

import android.content.Context

const val MissingTrackedSessionPermissionsMessage =
    "Для GPS-трекинга и активных сессий нужны системные разрешения, включая точную геолокацию."

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
