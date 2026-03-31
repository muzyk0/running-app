package com.vladislav.runningapp.activity.service

import com.vladislav.runningapp.activity.ActivityRoutePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityTrackingCalculatorTest {
    @Test
    fun pointFilterRejectsInaccurateOrOutOfOrderPoints() {
        val firstPoint = point(
            latitude = 55.751244,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 8f,
        )
        val inaccuratePoint = point(
            latitude = 55.751400,
            longitude = 37.618600,
            recordedAtEpochMs = 2_000L,
            accuracyMeters = 60f,
        )
        val outOfOrderPoint = point(
            latitude = 55.751500,
            longitude = 37.618700,
            recordedAtEpochMs = 900L,
            accuracyMeters = 8f,
        )

        assertTrue(ActivityPointFilter.shouldAccept(previousAcceptedPoint = null, candidatePoint = firstPoint))
        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = firstPoint, candidatePoint = inaccuratePoint))
        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = firstPoint, candidatePoint = outOfOrderPoint))
    }

    @Test
    fun pointFilterRejectsGpsJitterThatIsTooCloseTooSoon() {
        val firstPoint = point(
            latitude = 55.751244,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 6f,
        )
        val jitterPoint = point(
            latitude = 55.751250,
            longitude = 37.618430,
            recordedAtEpochMs = 2_000L,
            accuracyMeters = 6f,
        )
        val spacedPoint = point(
            latitude = 55.751900,
            longitude = 37.619400,
            recordedAtEpochMs = 5_500L,
            accuracyMeters = 6f,
        )

        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = firstPoint, candidatePoint = jitterPoint))
        assertTrue(ActivityPointFilter.shouldAccept(previousAcceptedPoint = firstPoint, candidatePoint = spacedPoint))
    }

    @Test
    fun pointFilterRejectsInvalidCoordinatesAndNonPositiveAccuracy() {
        val invalidLatitude = point(
            latitude = Double.NaN,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 8f,
        )
        val invalidLongitude = point(
            latitude = 55.751244,
            longitude = 181.0,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 8f,
        )
        val zeroAccuracy = point(
            latitude = 55.751244,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 0f,
        )
        val negativeAccuracy = point(
            latitude = 55.751244,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = -4f,
        )

        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = null, candidatePoint = invalidLatitude))
        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = null, candidatePoint = invalidLongitude))
        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = null, candidatePoint = zeroAccuracy))
        assertFalse(ActivityPointFilter.shouldAccept(previousAcceptedPoint = null, candidatePoint = negativeAccuracy))
    }

    @Test
    fun distanceCalculatorReturnsStableApproximateMeters() {
        val start = point(
            latitude = 55.751244,
            longitude = 37.618423,
            recordedAtEpochMs = 1_000L,
            accuracyMeters = 6f,
        )
        val end = point(
            latitude = 55.752100,
            longitude = 37.620100,
            recordedAtEpochMs = 6_000L,
            accuracyMeters = 6f,
        )

        val distanceMeters = ActivityDistanceCalculator.distanceMeters(start, end)

        assertTrue(distanceMeters in 140.0..180.0)
    }

    @Test
    fun averagePaceCalculatorRoundsPerKilometerAndReturnsNullWithoutDistance() {
        assertEquals(320, calculateAveragePaceSecPerKm(durationSec = 1_600, distanceMeters = 5_000.0))
        assertNotNull(calculateAveragePaceSecPerKm(durationSec = 90, distanceMeters = 250.0))
        assertEquals(null, calculateAveragePaceSecPerKm(durationSec = 120, distanceMeters = 0.0))
    }

    private fun point(
        latitude: Double,
        longitude: Double,
        recordedAtEpochMs: Long,
        accuracyMeters: Float,
    ): ActivityRoutePoint = ActivityRoutePoint(
        latitude = latitude,
        longitude = longitude,
        recordedAtEpochMs = recordedAtEpochMs,
        accuracyMeters = accuracyMeters,
    )
}
