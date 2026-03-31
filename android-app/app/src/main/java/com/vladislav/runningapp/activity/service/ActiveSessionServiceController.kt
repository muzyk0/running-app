package com.vladislav.runningapp.activity.service

import android.content.Context
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface ActiveSessionServiceController {
    fun start()

    fun stop()
}

@Singleton
class AndroidActiveSessionServiceController @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ActiveSessionServiceController {
    override fun start() {
        ContextCompat.startForegroundService(
            context,
            ActiveSessionService.createIntent(context, ActiveSessionService.Action.Start),
        )
    }

    override fun stop() {
        context.stopService(
            ActiveSessionService.createIntent(context, ActiveSessionService.Action.Start),
        )
    }
}
