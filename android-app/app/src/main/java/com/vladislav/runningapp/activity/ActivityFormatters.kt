package com.vladislav.runningapp.activity

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val RussianLocale: Locale = Locale.forLanguageTag("ru-RU")
private val HistoryTimestampFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm", RussianLocale)

fun formatDurationLabel(totalDurationSec: Int): String {
    val hours = totalDurationSec / 3_600
    val minutes = (totalDurationSec % 3_600) / 60
    val seconds = totalDurationSec % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(RussianLocale, hours, minutes, seconds)
        else -> "%d:%02d".format(RussianLocale, minutes, seconds)
    }
}

fun formatDistanceLabel(distanceMeters: Double): String = when {
    distanceMeters >= 1_000.0 -> "%.2f км".format(RussianLocale, distanceMeters / 1_000.0)
    distanceMeters >= 100.0 -> "%.0f м".format(RussianLocale, distanceMeters)
    else -> "%.1f м".format(RussianLocale, distanceMeters)
}

fun formatPaceLabel(averagePaceSecPerKm: Int?): String = if (averagePaceSecPerKm == null) {
    "Пока нет"
} else {
    val minutes = averagePaceSecPerKm / 60
    val seconds = averagePaceSecPerKm % 60
    "%d:%02d /км".format(RussianLocale, minutes, seconds)
}

fun formatCompletedAtLabel(epochMs: Long): String = HistoryTimestampFormatter.format(
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()),
)
