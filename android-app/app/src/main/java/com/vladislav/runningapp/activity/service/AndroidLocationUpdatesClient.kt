package com.vladislav.runningapp.activity.service

import android.annotation.SuppressLint
import android.os.Looper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.vladislav.runningapp.activity.ActivityRoutePoint
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class AndroidLocationUpdatesClient @Inject constructor(
    private val fusedLocationProviderClient: FusedLocationProviderClient,
) : LocationUpdatesClient {
    @SuppressLint("MissingPermission")
    override suspend fun openSession(): LocationUpdatesSession {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5_000L)
            .setMinUpdateDistanceMeters(5f)
            .setWaitForAccurateLocation(false)
            .build()
        val updatesChannel = Channel<ActivityRoutePoint>(capacity = Channel.BUFFERED)
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.locations.forEach { location ->
                    updatesChannel.trySend(
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

        try {
            fusedLocationProviderClient
                .requestLocationUpdates(request, callback, Looper.getMainLooper())
                .awaitResult()
        } catch (error: Throwable) {
            try {
                withContext(NonCancellable) {
                    runCatching {
                        fusedLocationProviderClient.removeLocationUpdates(callback).awaitResult()
                    }
                }
            } finally {
                updatesChannel.close(error)
            }
            throw error
        }

        return object : LocationUpdatesSession {
            override val updates: Flow<ActivityRoutePoint> = updatesChannel.receiveAsFlow()

            override suspend fun close() {
                try {
                    fusedLocationProviderClient.removeLocationUpdates(callback).awaitResult()
                } finally {
                    updatesChannel.close()
                }
            }
        }
    }
}

private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { result ->
        if (continuation.isActive) {
            continuation.resume(result)
        }
    }
    addOnFailureListener { error ->
        if (continuation.isActive) {
            continuation.resumeWithException(error)
        }
    }
    addOnCanceledListener {
        if (continuation.isActive) {
            continuation.cancel()
        }
    }
}
