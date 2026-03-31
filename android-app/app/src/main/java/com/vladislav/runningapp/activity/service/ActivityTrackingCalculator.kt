package com.vladislav.runningapp.activity.service

import com.vladislav.runningapp.activity.ActivityRoutePoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

private const val EarthRadiusMeters = 6_371_000.0
private const val MaximumAcceptedAccuracyMeters = 40f
private const val MinimumAcceptedDistanceMeters = 5.0
private const val MinimumAcceptedGapMs = 3_000L

object ActivityPointFilter {
    fun shouldAccept(
        previousAcceptedPoint: ActivityRoutePoint?,
        candidatePoint: ActivityRoutePoint,
    ): Boolean {
        if (!candidatePoint.hasValidCoordinates()) {
            return false
        }
        if (candidatePoint.recordedAtEpochMs <= 0L || candidatePoint.accuracyMeters > MaximumAcceptedAccuracyMeters) {
            return false
        }
        if (previousAcceptedPoint == null) {
            return true
        }
        if (candidatePoint.recordedAtEpochMs <= previousAcceptedPoint.recordedAtEpochMs) {
            return false
        }

        val distanceMeters = ActivityDistanceCalculator.distanceMeters(previousAcceptedPoint, candidatePoint)
        val timeGapMs = candidatePoint.recordedAtEpochMs - previousAcceptedPoint.recordedAtEpochMs
        return distanceMeters >= MinimumAcceptedDistanceMeters || timeGapMs >= MinimumAcceptedGapMs
    }
}

object ActivityDistanceCalculator {
    fun distanceMeters(
        start: ActivityRoutePoint,
        end: ActivityRoutePoint,
    ): Double {
        val latitudeDelta = Math.toRadians(end.latitude - start.latitude)
        val longitudeDelta = Math.toRadians(end.longitude - start.longitude)
        val startLatitude = Math.toRadians(start.latitude)
        val endLatitude = Math.toRadians(end.latitude)

        val haversine = sin(latitudeDelta / 2.0).pow(2.0) +
            cos(startLatitude) * cos(endLatitude) * sin(longitudeDelta / 2.0).pow(2.0)
        return 2.0 * EarthRadiusMeters * asin(sqrt(haversine.coerceIn(0.0, 1.0)))
    }
}

fun calculateAveragePaceSecPerKm(
    durationSec: Int,
    distanceMeters: Double,
): Int? = if (durationSec <= 0 || distanceMeters < 1.0) {
    null
} else {
    (durationSec / (distanceMeters / 1_000.0)).roundToInt()
}

private fun ActivityRoutePoint.hasValidCoordinates(): Boolean =
    latitude.isFinite() &&
        longitude.isFinite() &&
        latitude in -90.0..90.0 &&
        longitude in -180.0..180.0 &&
        accuracyMeters.isFinite() &&
        accuracyMeters > 0f
