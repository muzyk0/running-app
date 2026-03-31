package com.vladislav.runningapp.core.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object PermissionSnapshotFactory {
    fun fromContext(context: Context): PermissionSnapshot {
        val sdkInt = Build.VERSION.SDK_INT
        return PermissionSnapshot(
            sdkInt = sdkInt,
            locationGranted = isLocationGranted(context),
            notificationsGranted = if (sdkInt >= 33) {
                isPermissionGranted(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            },
            foregroundTrackingConfigured = hasForegroundTrackingSupport(context, sdkInt),
        )
    }

    fun runtimePermissions(sdkInt: Int): List<String> = buildList {
        add(Manifest.permission.ACCESS_COARSE_LOCATION)
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (sdkInt >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun isLocationGranted(context: Context): Boolean = listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ).any { permission ->
        isPermissionGranted(context, permission)
    }

    private fun isPermissionGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun hasForegroundTrackingSupport(context: Context, sdkInt: Int): Boolean {
        val requestedPermissions = requestedPermissions(context)
        val hasForegroundService = requestedPermissions.contains(Manifest.permission.FOREGROUND_SERVICE)
        val hasForegroundServiceLocation = sdkInt < 34 ||
            requestedPermissions.contains(Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        return hasForegroundService && hasForegroundServiceLocation
    }

    private fun requestedPermissions(context: Context): Set<String> {
        val packageInfo = if (Build.VERSION.SDK_INT >= 33) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
        }
        return packageInfo.permissionsOrEmpty()
    }

    private fun PackageInfo.permissionsOrEmpty(): Set<String> = requestedPermissions?.toSet().orEmpty()
}
