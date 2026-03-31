package com.vladislav.runningapp.core.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.vladislav.runningapp.ai.ui.GenerationScreen
import com.vladislav.runningapp.activity.ui.FreeRunScreen
import com.vladislav.runningapp.activity.ui.HistoryScreen
import com.vladislav.runningapp.profile.ProfileScreen
import com.vladislav.runningapp.session.ui.ActiveSessionScreen
import com.vladislav.runningapp.training.ui.FocusedWorkoutIdArg
import com.vladislav.runningapp.training.ui.TrainingRoutePattern
import com.vladislav.runningapp.training.ui.TrainingScreen
import com.vladislav.runningapp.training.ui.trainingRoute

@Composable
fun RunningAppNavHost(
    navController: NavHostController,
    startDestination: TopLevelDestination,
    canStartTrackedSessions: Boolean,
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
        composable(
            route = TrainingRoutePattern,
            arguments = listOf(
                navArgument(FocusedWorkoutIdArg) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { backStackEntry ->
            TrainingScreen(
                focusedWorkoutId = backStackEntry.arguments?.getString(FocusedWorkoutIdArg),
                canStartTrackedSessions = canStartTrackedSessions,
                onOpenActiveSession = {
                    navController.navigate(TopLevelDestination.ActiveSession.route) {
                        launchSingleTop = true
                    }
                },
            )
        }
        composable(TopLevelDestination.Generation.route) {
            GenerationScreen(
                onOpenProfile = {
                    navController.navigate(TopLevelDestination.Profile.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                onOpenTraining = { workoutId ->
                    navController.navigate(trainingRoute(focusedWorkoutId = workoutId)) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(TopLevelDestination.ActiveSession.route) {
            ActiveSessionScreen(
                onOpenTraining = {
                    navController.navigate(TopLevelDestination.Training.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(TopLevelDestination.FreeRun.route) {
            FreeRunScreen(
                canStartTrackedSessions = canStartTrackedSessions,
                onOpenActiveSession = {
                    navController.navigate(TopLevelDestination.ActiveSession.route) {
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        }
        composable(TopLevelDestination.History.route) {
            HistoryScreen()
        }
    }
}
