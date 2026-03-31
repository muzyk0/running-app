package com.vladislav.runningapp.activity.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladislav.runningapp.R
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.activity.formatDistanceLabel
import com.vladislav.runningapp.activity.formatDurationLabel
import com.vladislav.runningapp.activity.formatPaceLabel

@Composable
fun FreeRunScreen(
    onOpenActiveSession: () -> Unit,
    canStartTrackedSessions: Boolean,
    viewModel: FreeRunViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    FreeRunScreen(
        state = uiState.trackerState,
        errorMessage = uiState.errorMessage,
        isStarting = uiState.isStarting,
        canStartTrackedSessions = canStartTrackedSessions,
        onStartFreeRun = viewModel::onStartFreeRun,
        onStopFreeRun = viewModel::onStopFreeRun,
        onOpenActiveSession = onOpenActiveSession,
    )
}

@Composable
private fun FreeRunScreen(
    state: ActivityTrackerState,
    errorMessage: String?,
    isStarting: Boolean,
    canStartTrackedSessions: Boolean,
    onStartFreeRun: () -> Unit,
    onStopFreeRun: () -> Unit,
    onOpenActiveSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        errorMessage?.let { message ->
            InlineErrorCard(message = message)
        }

        Card {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.free_run_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(R.string.free_run_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        when {
            state.isTracking && state.isPlannedWorkout -> PlannedWorkoutRedirectCard(
                workoutTitle = state.workoutTitle,
                onOpenActiveSession = onOpenActiveSession,
            )

            state.isFreeRun -> LiveFreeRunCard(
                state = state,
                onStopFreeRun = onStopFreeRun,
            )

            else -> FreeRunStartCard(
                canStartTrackedSessions = canStartTrackedSessions,
                isStarting = isStarting,
                onStartFreeRun = onStartFreeRun,
            )
        }
    }
}

@Composable
private fun FreeRunStartCard(
    canStartTrackedSessions: Boolean,
    isStarting: Boolean,
    onStartFreeRun: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.free_run_start_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.free_run_start_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = onStartFreeRun,
                enabled = canStartTrackedSessions && !isStarting,
            ) {
                Text(text = stringResource(R.string.free_run_start_action))
            }
            if (!canStartTrackedSessions) {
                Text(
                    text = stringResource(R.string.permissions_summary_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun InlineErrorCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(20.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LiveFreeRunCard(
    state: ActivityTrackerState,
    onStopFreeRun: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.free_run_live_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FreeRunMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.free_run_metric_duration),
                    value = formatDurationLabel(state.durationSec),
                )
                FreeRunMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.free_run_metric_distance),
                    value = formatDistanceLabel(state.distanceMeters),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FreeRunMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.free_run_metric_pace),
                    value = formatPaceLabel(state.averagePaceSecPerKm),
                )
                FreeRunMetric(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.free_run_metric_points),
                    value = state.routePoints.size.toString(),
                )
            }
            Text(
                text = stringResource(
                    R.string.free_run_live_body,
                    state.routePoints.size,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onStopFreeRun) {
                Text(text = stringResource(R.string.free_run_stop_action))
            }
        }
    }
}

@Composable
private fun PlannedWorkoutRedirectCard(
    workoutTitle: String?,
    onOpenActiveSession: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.free_run_busy_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = if (workoutTitle != null) {
                    stringResource(R.string.free_run_busy_body_with_workout, workoutTitle)
                } else {
                    stringResource(R.string.free_run_busy_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onOpenActiveSession) {
                Text(text = stringResource(R.string.free_run_open_active_session_action))
            }
        }
    }
}

@Composable
private fun FreeRunMetric(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
