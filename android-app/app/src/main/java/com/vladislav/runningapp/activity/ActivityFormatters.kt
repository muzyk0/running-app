package com.vladislav.runningapp.activity

import android.content.res.Resources
import androidx.compose.runtime.Composable
import com.vladislav.runningapp.core.i18n.formatCompletedAtLabel as formatLocalizedCompletedAtLabel
import com.vladislav.runningapp.core.i18n.formatDistanceLabel as formatLocalizedDistanceLabel
import com.vladislav.runningapp.core.i18n.formatElapsedDurationLabel
import com.vladislav.runningapp.core.i18n.formatPaceLabel as formatLocalizedPaceLabel

@Composable
fun formatDurationLabel(totalDurationSec: Int): String = formatElapsedDurationLabel(totalDurationSec)

fun formatDurationLabel(
    resources: Resources,
    totalDurationSec: Int,
): String = formatElapsedDurationLabel(resources, totalDurationSec)

@Composable
fun formatDistanceLabel(distanceMeters: Double): String = formatLocalizedDistanceLabel(distanceMeters)

fun formatDistanceLabel(
    resources: Resources,
    distanceMeters: Double,
): String = formatLocalizedDistanceLabel(resources, distanceMeters)

@Composable
fun formatPaceLabel(averagePaceSecPerKm: Int?): String = formatLocalizedPaceLabel(averagePaceSecPerKm)

fun formatPaceLabel(
    resources: Resources,
    averagePaceSecPerKm: Int?,
): String = formatLocalizedPaceLabel(resources, averagePaceSecPerKm)

@Composable
fun formatCompletedAtLabel(epochMs: Long): String = formatLocalizedCompletedAtLabel(epochMs)

fun formatCompletedAtLabel(
    resources: Resources,
    epochMs: Long,
): String = formatLocalizedCompletedAtLabel(resources, epochMs)
