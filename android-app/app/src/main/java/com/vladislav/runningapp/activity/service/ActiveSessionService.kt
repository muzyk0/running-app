package com.vladislav.runningapp.activity.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.vladislav.runningapp.activity.ActivityTracker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ActiveSessionService : Service() {
    @Inject
    lateinit var activityTracker: ActivityTracker

    @Inject
    lateinit var sessionNotificationFactory: SessionNotificationFactory

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var collectionJob: Job? = null
    private var isForegroundStarted = false

    override fun onCreate() {
        super.onCreate()
        sessionNotificationFactory.ensureChannel()
        collectionJob = scope.launch {
            activityTracker.trackerState.collectLatest { state ->
                if (state.isTracking) {
                    val notification = sessionNotificationFactory.build(state)
                    if (!isForegroundStarted) {
                        startForeground(ActiveSessionNotificationId, notification)
                        isForegroundStarted = true
                    } else {
                        val manager = getSystemService(android.app.NotificationManager::class.java)
                        manager.notify(ActiveSessionNotificationId, notification)
                    }
                } else if (isForegroundStarted) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForegroundStarted = false
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == Action.Stop.value) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            isForegroundStarted = false
            stopSelf()
            return START_NOT_STICKY
        }
        val state = activityTracker.trackerState.value
        if (state.isTracking && !isForegroundStarted) {
            startForeground(
                ActiveSessionNotificationId,
                sessionNotificationFactory.build(state),
            )
            isForegroundStarted = true
        }
        return START_STICKY
    }

    override fun onDestroy() {
        collectionJob?.cancel()
        scope.coroutineContext[Job]?.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    enum class Action(
        val value: String,
    ) {
        Start("com.vladislav.runningapp.action.START_ACTIVE_SESSION_SERVICE"),
        Stop("com.vladislav.runningapp.action.STOP_ACTIVE_SESSION_SERVICE"),
    }

    companion object {
        fun createIntent(
            context: Context,
            action: Action,
        ): Intent = Intent(context, ActiveSessionService::class.java).setAction(action.value)
    }
}
