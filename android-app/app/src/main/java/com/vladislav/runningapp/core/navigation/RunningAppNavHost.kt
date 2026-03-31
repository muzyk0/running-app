package com.vladislav.runningapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vladislav.runningapp.ai.GenerationScreen
import com.vladislav.runningapp.activity.FreeRunScreen
import com.vladislav.runningapp.activity.HistoryScreen
import com.vladislav.runningapp.profile.ProfileScreen
import com.vladislav.runningapp.session.ActiveSessionScreen
import com.vladislav.runningapp.training.ui.TrainingScreen

@Composable
fun RunningAppNavHost(
    navController: NavHostController,
    startDestination: TopLevelDestination,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.Profile.route) {
            ProfileScreen()
        }
        composable(TopLevelDestination.Training.route) {
            TrainingScreen()
        }
        composable(TopLevelDestination.Generation.route) {
            GenerationScreen()
        }
        composable(TopLevelDestination.ActiveSession.route) {
            ActiveSessionScreen()
        }
        composable(TopLevelDestination.FreeRun.route) {
            FreeRunScreen()
        }
        composable(TopLevelDestination.History.route) {
            HistoryScreen()
        }
    }
}
