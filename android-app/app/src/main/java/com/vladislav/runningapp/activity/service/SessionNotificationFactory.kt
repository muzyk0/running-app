package com.vladislav.runningapp.activity.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.vladislav.runningapp.MainActivity
import com.vladislav.runningapp.R
import com.vladislav.runningapp.activity.ActivitySessionType
import com.vladislav.runningapp.activity.ActivityTrackerState
import com.vladislav.runningapp.activity.formatDistanceLabel
import com.vladislav.runningapp.activity.formatDurationLabel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionNotificationFactory @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val channel = NotificationChannel(
            ActiveSessionNotificationChannelId,
            context.getString(R.string.activity_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.activity_notification_channel_description)
        }
        notificationManager.createNotificationChannel(channel)
    }

    fun build(state: ActivityTrackerState): Notification {
        val launchIntent = PendingIntent.getActivity(
            context,
            1001,
            MainActivity::class.java.let { activityClass ->
                android.content.Intent(context, activityClass)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (state.type) {
            ActivitySessionType.FreeRun -> context.getString(R.string.free_run_title)
            ActivitySessionType.PlannedWorkout -> state.workoutTitle ?: context.getString(R.string.active_session_notification_workout_fallback)
            null -> context.getString(R.string.app_name)
        }
        val content = context.getString(
            R.string.active_session_notification_content,
            formatDurationLabel(context.resources, state.durationSec),
            formatDistanceLabel(context.resources, state.distanceMeters),
        )

        return NotificationCompat.Builder(context, ActiveSessionNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(launchIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }
}

const val ActiveSessionNotificationChannelId = "running-app.active-session"
const val ActiveSessionNotificationId = 4102
