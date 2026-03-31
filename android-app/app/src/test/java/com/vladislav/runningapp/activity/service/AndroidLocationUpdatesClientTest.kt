package com.vladislav.runningapp.activity.service

import android.app.PendingIntent
import android.location.Location
import android.os.Looper
import com.google.android.gms.common.api.Api
import com.google.android.gms.common.api.internal.ApiKey
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.DeviceOrientationListener
import com.google.android.gms.location.DeviceOrientationRequest
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LastLocationRequest
import com.google.android.gms.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.tasks.CancellationToken
import com.google.android.gms.tasks.OnCanceledListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidLocationUpdatesClientTest {
    @Test
    fun openSessionRemovesLocationUpdatesWhenCallerIsCancelledBeforeStartupCompletes() = runTest {
        val fusedLocationProviderClient = FakeFusedLocationProviderClient()
        val client = AndroidLocationUpdatesClient(fusedLocationProviderClient)

        val opening = async {
            client.openSession()
        }
        runCurrent()

        assertEquals(1, fusedLocationProviderClient.requestCalls)
        assertNotNull(fusedLocationProviderClient.requestedCallback)

        opening.cancel()
        runCurrent()

        assertEquals(1, fusedLocationProviderClient.removeCalls)
        assertEquals(
            fusedLocationProviderClient.requestedCallback,
            fusedLocationProviderClient.removedCallback,
        )
    }
}

@Suppress("OVERRIDE_DEPRECATION")
private class FakeFusedLocationProviderClient : FusedLocationProviderClient {
    private val requestLocationUpdatesTask = ManualVoidTask()

    var requestCalls: Int = 0
    var removeCalls: Int = 0
    var requestedCallback: LocationCallback? = null
    var removedCallback: LocationCallback? = null

    override fun getApiKey(): ApiKey<Api.ApiOptions.NoOptions> = unusedValue()

    override fun getLastLocation(): Task<Location> = unusedTask()

    override fun getLastLocation(request: LastLocationRequest): Task<Location> = unusedTask()

    override fun getCurrentLocation(
        priority: Int,
        cancellationToken: CancellationToken?,
    ): Task<Location> = unusedTask()

    override fun getCurrentLocation(
        request: CurrentLocationRequest,
        cancellationToken: CancellationToken?,
    ): Task<Location> = unusedTask()

    override fun getLocationAvailability(): Task<LocationAvailability> = unusedTask()

    override fun requestLocationUpdates(
        request: LocationRequest,
        executor: Executor,
        listener: LocationListener,
    ): Task<Void> = unusedTask()

    override fun requestLocationUpdates(
        request: LocationRequest,
        listener: LocationListener,
        looper: Looper?,
    ): Task<Void> = unusedTask()

    override fun requestLocationUpdates(
        request: LocationRequest,
        callback: LocationCallback,
        looper: Looper?,
    ): Task<Void> {
        requestCalls += 1
        requestedCallback = callback
        return requestLocationUpdatesTask
    }

    override fun requestLocationUpdates(
        request: LocationRequest,
        executor: Executor,
        callback: LocationCallback,
    ): Task<Void> = unusedTask()

    override fun requestLocationUpdates(
        request: LocationRequest,
        pendingIntent: PendingIntent,
    ): Task<Void> = unusedTask()

    override fun removeLocationUpdates(listener: LocationListener): Task<Void> = unusedTask()

    override fun removeLocationUpdates(callback: LocationCallback): Task<Void> {
        removeCalls += 1
        removedCallback = callback
        return completedVoidTask()
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent): Task<Void> = unusedTask()

    override fun flushLocations(): Task<Void> = unusedTask()

    override fun setMockMode(mockMode: Boolean): Task<Void> = unusedTask()

    override fun setMockLocation(mockLocation: Location): Task<Void> = unusedTask()

    override fun requestDeviceOrientationUpdates(
        request: DeviceOrientationRequest,
        executor: Executor,
        listener: DeviceOrientationListener,
    ): Task<Void> = unusedTask()

    override fun requestDeviceOrientationUpdates(
        request: DeviceOrientationRequest,
        listener: DeviceOrientationListener,
        looper: Looper?,
    ): Task<Void> = unusedTask()

    override fun removeDeviceOrientationUpdates(listener: DeviceOrientationListener): Task<Void> = unusedTask()

    private fun completedVoidTask(): Task<Void> = ManualVoidTask().apply {
        setResult()
    }

    private fun <T> unusedTask(): Task<T> = throw UnsupportedOperationException("unused in test")

    private fun <T> unusedValue(): T = throw UnsupportedOperationException("unused in test")
}

private class ManualVoidTask : Task<Void>() {
    private var isFinished = false
    private var isCancelledValue = false
    private var failure: Exception? = null
    private val successListeners = mutableListOf<OnSuccessListener<in Void>>()
    private val failureListeners = mutableListOf<OnFailureListener>()
    private val canceledListeners = mutableListOf<OnCanceledListener>()

    fun setResult() {
        if (isFinished) {
            return
        }
        isFinished = true
        successListeners.forEach { listener ->
            listener.onSuccess(null)
        }
    }

    override fun addOnCanceledListener(listener: OnCanceledListener): Task<Void> {
        if (isCancelledValue) {
            listener.onCanceled()
        } else {
            canceledListeners += listener
        }
        return this
    }

    override fun addOnFailureListener(listener: OnFailureListener): Task<Void> {
        failure?.let(listener::onFailure) ?: failureListeners.add(listener)
        return this
    }

    override fun addOnFailureListener(
        activity: android.app.Activity,
        listener: OnFailureListener,
    ): Task<Void> = addOnFailureListener(listener)

    override fun addOnFailureListener(
        executor: Executor,
        listener: OnFailureListener,
    ): Task<Void> {
        failure?.let { error ->
            executor.execute { listener.onFailure(error) }
        } ?: failureListeners.add(OnFailureListener { error ->
            executor.execute { listener.onFailure(error) }
        })
        return this
    }

    override fun addOnSuccessListener(listener: OnSuccessListener<in Void>): Task<Void> {
        if (isSuccessful) {
            listener.onSuccess(null)
        } else {
            successListeners += listener
        }
        return this
    }

    override fun addOnSuccessListener(
        activity: android.app.Activity,
        listener: OnSuccessListener<in Void>,
    ): Task<Void> = addOnSuccessListener(listener)

    override fun addOnSuccessListener(
        executor: Executor,
        listener: OnSuccessListener<in Void>,
    ): Task<Void> {
        if (isSuccessful) {
            executor.execute { listener.onSuccess(null) }
        } else {
            successListeners += OnSuccessListener {
                executor.execute { listener.onSuccess(null) }
            }
        }
        return this
    }

    override fun getException(): Exception? = failure

    override fun getResult(): Void? = null

    override fun <X : Throwable?> getResult(exceptionType: Class<X>): Void? {
        val currentFailure = failure
        if (currentFailure != null && exceptionType.isInstance(currentFailure)) {
            throw currentFailure
        }
        return null
    }

    override fun isCanceled(): Boolean = isCancelledValue

    override fun isComplete(): Boolean = isFinished

    override fun isSuccessful(): Boolean = isFinished && !isCancelledValue && failure == null
}
