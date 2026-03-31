package com.vladislav.runningapp.session.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vladislav.runningapp.R
import com.vladislav.runningapp.session.WorkoutSessionState
import com.vladislav.runningapp.session.WorkoutSessionStatus
import com.vladislav.runningapp.training.domain.WorkoutStepType

@Composable
fun ActiveSessionScreen(
    onOpenTraining: () -> Unit,
    viewModel: ActiveSessionViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    ActiveSessionScreen(
        state = uiState,
        onOpenTraining = onOpenTraining,
        onPause = viewModel::onPause,
        onResume = viewModel::onResume,
        onStop = viewModel::onStop,
    )
}

@Composable
private fun ActiveSessionScreen(
    state: WorkoutSessionState,
    onOpenTraining: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    if (state.workout == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            EmptyActiveSessionCard(onOpenTraining = onOpenTraining)
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SessionOverviewCard(state = state)

        state.currentStep?.let { step ->
            CurrentStepCard(
                state = state,
                stepType = step.type,
                voicePrompt = step.voicePrompt,
            )
        }

        state.lastCuePrompt?.let { prompt ->
            Card {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.active_session_last_cue_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = prompt,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        SessionControlsRow(
            state = state,
            onPause = onPause,
            onResume = onResume,
            onStop = onStop,
        )
    }
}

@Composable
private fun EmptyActiveSessionCard(
    onOpenTraining: () -> Unit,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.active_session_empty_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.active_session_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = onOpenTraining) {
                Text(text = stringResource(R.string.active_session_open_training_action))
            }
        }
    }
}

@Composable
private fun SessionOverviewCard(
    state: WorkoutSessionState,
) {
    val workout = requireNotNull(state.workout)

    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = workout.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.active_session_step_counter,
                            state.currentStepNumber,
                            state.totalSteps,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = {
                        Text(text = stringResource(state.status.labelRes()))
                    },
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.active_session_elapsed_label),
                    value = formatDuration(state.totalElapsedSec),
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.active_session_remaining_label),
                    value = formatDuration(state.currentStepRemainingSec),
                )
                MetricCard(
                    modifier = Modifier.weight(1f),
                    label = stringResource(R.string.active_session_total_label),
                    value = formatDuration(state.totalDurationSec),
                )
            }
        }
    }
}

@Composable
private fun CurrentStepCard(
    state: WorkoutSessionState,
    stepType: WorkoutStepType,
    voicePrompt: String,
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.active_session_current_step_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(stepType.labelRes()),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(
                    R.string.active_session_current_step_meta,
                    formatDuration(state.currentStepRemainingSec),
                    formatDuration(state.totalElapsedSec),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = voicePrompt,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun SessionControlsRow(
    state: WorkoutSessionState,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.canPause) {
                Button(onClick = onPause) {
                    Text(text = stringResource(R.string.active_session_pause_action))
                }
            }
            if (state.canResume) {
                Button(onClick = onResume) {
                    Text(text = stringResource(R.string.active_session_resume_action))
                }
            }
            if (state.canStop) {
                OutlinedButton(onClick = onStop) {
                    Text(text = stringResource(R.string.active_session_stop_action))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
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

private fun WorkoutSessionStatus.labelRes(): Int = when (this) {
    WorkoutSessionStatus.Idle -> R.string.active_session_status_idle
    WorkoutSessionStatus.Running -> R.string.active_session_status_running
    WorkoutSessionStatus.Paused -> R.string.active_session_status_paused
    WorkoutSessionStatus.Completed -> R.string.active_session_status_completed
}

private fun WorkoutStepType.labelRes(): Int = when (this) {
    WorkoutStepType.Warmup -> R.string.training_step_type_warmup
    WorkoutStepType.Run -> R.string.training_step_type_run
    WorkoutStepType.Walk -> R.string.training_step_type_walk
    WorkoutStepType.Cooldown -> R.string.training_step_type_cooldown
    WorkoutStepType.Rest -> R.string.training_step_type_rest
}

private fun formatDuration(totalDurationSec: Int): String {
    val minutes = totalDurationSec / 60
    val seconds = totalDurationSec % 60
    return when {
        minutes > 0 && seconds > 0 -> "$minutes мин $seconds с"
        minutes > 0 -> "$minutes мин"
        else -> "$seconds с"
    }
}
