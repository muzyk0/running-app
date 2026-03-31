package com.vladislav.runningapp.activity.service

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.vladislav.runningapp.activity.ActivityRoutePoint
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidLocationUpdatesClient @Inject constructor(
    private val fusedLocationProviderClient: FusedLocationProviderClient,
) : LocationUpdatesClient {
    @SuppressLint("MissingPermission")
    override fun locationUpdates(): Flow<ActivityRoutePoint> = callbackFlow {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(false)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    trySend(
                        ActivityRoutePoint(
                            latitude = location.latitude,
                            longitude = location.longitude,
                            recordedAtEpochMs = location.time,
                            accuracyMeters = location.accuracy,
                        ),
                    )
                }
            }
        }

        fusedLocationProviderClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        awaitClose {
            fusedLocationProviderClient.removeLocationUpdates(callback)
        }
    }
}
