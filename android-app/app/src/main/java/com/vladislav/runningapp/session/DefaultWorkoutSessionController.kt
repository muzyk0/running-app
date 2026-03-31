package com.vladislav.runningapp.session

import com.vladislav.runningapp.core.di.DefaultDispatcher
import com.vladislav.runningapp.session.audio.SessionCuePlayer
import com.vladislav.runningapp.training.domain.Workout
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Singleton
class DefaultWorkoutSessionController @Inject constructor(
    private val sessionCuePlayer: SessionCuePlayer,
    @DefaultDispatcher defaultDispatcher: CoroutineDispatcher,
) : WorkoutSessionController {
    private val scope = CoroutineScope(SupervisorJob() + defaultDispatcher)
    private val mutableSessionState = MutableStateFlow(WorkoutSessionState())
    private val mutationLock = Any()
    private var tickerJob: Job? = null

    override val sessionState: StateFlow<WorkoutSessionState> = mutableSessionState.asStateFlow()

    override fun startWorkout(workout: Workout) {
        applyAction(WorkoutSessionAction.Start(workout))
    }

    override fun pauseWorkout() {
        applyAction(WorkoutSessionAction.Pause)
    }

    override fun resumeWorkout() {
        applyAction(WorkoutSessionAction.Resume)
    }

    override fun stopWorkout() {
        applyAction(WorkoutSessionAction.Stop)
    }

    private fun applyAction(action: WorkoutSessionAction) {
        val transition = synchronized(mutationLock) {
            val nextTransition = WorkoutSessionEngine.reduce(mutableSessionState.value, action)
            mutableSessionState.value = nextTransition.state
            synchronizeTickerLocked(nextTransition.state.status)
            nextTransition
        }

        transition.effects.forEach { effect ->
            when (effect) {
                is WorkoutSessionEffect.PlayStepCue -> {
                    sessionCuePlayer.play(effect.prompt)
                    mutableSessionState.update { state ->
                        state.copy(lastCuePrompt = effect.prompt)
                    }
                }
            }
        }
    }

    private fun synchronizeTickerLocked(status: WorkoutSessionStatus) {
        if (status == WorkoutSessionStatus.Running) {
            if (tickerJob?.isActive == true) {
                return
            }
            tickerJob = scope.launch {
                while (isActive) {
                    delay(1_000)
                    applyAction(WorkoutSessionAction.Tick)
                }
            }
        } else {
            tickerJob?.cancel()
            tickerJob = null
        }
    }
}
