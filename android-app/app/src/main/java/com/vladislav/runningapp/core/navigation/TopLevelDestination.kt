package com.vladislav.runningapp.core.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.graphics.vector.ImageVector
import com.vladislav.runningapp.R

enum class TopLevelDestination(
    val route: String,
    @param:StringRes val titleRes: Int,
    val icon: ImageVector,
) {
    Profile(
        route = "profile",
        titleRes = R.string.navigation_profile,
        icon = Icons.Filled.Person,
    ),
    Training(
        route = "training",
        titleRes = R.string.navigation_training,
        icon = Icons.AutoMirrored.Filled.FormatListBulleted,
    ),
    Generation(
        route = "generation",
        titleRes = R.string.navigation_generation,
        icon = Icons.Filled.AutoAwesome,
    ),
    ActiveSession(
        route = "active-session",
        titleRes = R.string.navigation_active_session,
        icon = Icons.Filled.PlayArrow,
    ),
    FreeRun(
        route = "free-run",
        titleRes = R.string.navigation_free_run,
        icon = Icons.AutoMirrored.Filled.DirectionsRun,
    ),
    History(
        route = "history",
        titleRes = R.string.navigation_history,
        icon = Icons.Filled.History,
    ),
    ;

    fun matches(route: String?): Boolean = route == this.route ||
        route?.startsWith("${this.route}/") == true ||
        route?.startsWith("${this.route}?") == true
}
