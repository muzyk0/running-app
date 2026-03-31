package com.vladislav.runningapp.training.ui

import android.net.Uri

private const val TrainingRouteBase = "training"
const val FocusedWorkoutIdArg = "focusedWorkoutId"
const val TrainingRoutePattern = "$TrainingRouteBase?$FocusedWorkoutIdArg={$FocusedWorkoutIdArg}"

fun trainingRoute(
    focusedWorkoutId: String? = null,
): String = if (focusedWorkoutId.isNullOrBlank()) {
    TrainingRouteBase
} else {
    "$TrainingRouteBase?$FocusedWorkoutIdArg=${Uri.encode(focusedWorkoutId)}"
}
