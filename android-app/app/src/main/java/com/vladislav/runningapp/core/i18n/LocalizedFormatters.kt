package com.vladislav.runningapp.core.i18n

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.vladislav.runningapp.R
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun formatElapsedDurationLabel(totalDurationSec: Int): String {
    LocalConfiguration.current
    return formatElapsedDurationLabel(
        resources = LocalContext.current.resources,
        totalDurationSec = totalDurationSec,
    )
}

fun formatElapsedDurationLabel(
    resources: Resources,
    totalDurationSec: Int,
): String {
    val locale = resources.configuration.locales[0]
    val hours = totalDurationSec / 3_600
    val minutes = (totalDurationSec % 3_600) / 60
    val seconds = totalDurationSec % 60
    return if (hours > 0) {
        String.format(locale, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(locale, "%d:%02d", minutes, seconds)
    }
}

@Composable
fun formatWorkoutDurationLabel(totalDurationSec: Int): String {
    LocalConfiguration.current
    return formatWorkoutDurationLabel(
        resources = LocalContext.current.resources,
        totalDurationSec = totalDurationSec,
    )
}

fun formatWorkoutDurationLabel(
    resources: Resources,
    totalDurationSec: Int,
): String {
    val minutes = totalDurationSec / 60
    val seconds = totalDurationSec % 60
    return when {
        minutes > 0 && seconds > 0 -> resources.getString(
            R.string.format_duration_minutes_seconds,
            minutes,
            seconds,
        )

        minutes > 0 -> resources.getString(
            R.string.format_duration_minutes_only,
            minutes,
        )

        else -> resources.getString(
            R.string.format_duration_seconds_only,
            seconds,
        )
    }
}

@Composable
fun formatDistanceLabel(distanceMeters: Double): String {
    LocalConfiguration.current
    return formatDistanceLabel(
        resources = LocalContext.current.resources,
        distanceMeters = distanceMeters,
    )
}

fun formatDistanceLabel(
    resources: Resources,
    distanceMeters: Double,
): String = when {
    distanceMeters >= 1_000.0 -> resources.getString(
        R.string.format_distance_kilometers,
        distanceMeters / 1_000.0,
    )

    distanceMeters >= 100.0 -> resources.getString(
        R.string.format_distance_meters_whole,
        distanceMeters,
    )

    else -> resources.getString(
        R.string.format_distance_meters_decimal,
        distanceMeters,
    )
}

@Composable
fun formatPaceLabel(averagePaceSecPerKm: Int?): String {
    LocalConfiguration.current
    return formatPaceLabel(
        resources = LocalContext.current.resources,
        averagePaceSecPerKm = averagePaceSecPerKm,
    )
}

fun formatPaceLabel(
    resources: Resources,
    averagePaceSecPerKm: Int?,
): String = if (averagePaceSecPerKm == null) {
    resources.getString(R.string.format_pace_unavailable)
} else {
    val minutes = averagePaceSecPerKm / 60
    val seconds = averagePaceSecPerKm % 60
    resources.getString(
        R.string.format_pace_per_kilometer,
        minutes,
        seconds,
    )
}

@Composable
fun formatCompletedAtLabel(epochMs: Long): String {
    LocalConfiguration.current
    return formatCompletedAtLabel(
        resources = LocalContext.current.resources,
        epochMs = epochMs,
    )
}

fun formatCompletedAtLabel(
    resources: Resources,
    epochMs: Long,
): String = DateTimeFormatter.ofPattern(
    "d MMM, HH:mm",
    resources.configuration.locales[0],
).format(
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
)
