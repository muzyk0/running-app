package com.vladislav.runningapp.activity.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vladislav.runningapp.activity.ActivityRoutePoint
import com.vladislav.runningapp.activity.ActivitySessionType
import com.vladislav.runningapp.activity.CompletedActivitySession
import com.vladislav.runningapp.core.storage.AppDatabase
import com.vladislav.runningapp.training.data.local.WorkoutEntity
import com.vladislav.runningapp.training.data.local.WorkoutStepEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var activityDao: ActivityDao

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        activityDao = database.activityDao()
        database.workoutDao().upsertWorkout(
            workout = WorkoutEntity(
                id = "workout-1",
                schemaVersion = "mvp.v1",
                title = "Интервалы",
                summary = "Базовый интервальный блок",
                goal = "Выносливость",
                disclaimer = null,
                updatedAtEpochMs = 1_000L,
            ),
            steps = listOf(
                WorkoutStepEntity(
                    workoutId = "workout-1",
                    stepIndex = 0,
                    type = "warmup",
                    durationSec = 300,
                    voicePrompt = "Разминка",
                ),
            ),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertCompletedSessionStoresPlannedWorkoutRouteWithStableOrder() = runTest {
        val plannedSession = CompletedActivitySession(
            id = "session-1",
            type = ActivitySessionType.PlannedWorkout,
            workoutId = "workout-1",
            workoutTitle = "Интервалы",
            startedAtEpochMs = 10_000L,
            completedAtEpochMs = 20_000L,
            durationSec = 900,
            distanceMeters = 2_100.0,
            averagePaceSecPerKm = 429,
            routePoints = listOf(
                ActivityRoutePoint(
                    latitude = 55.7512,
                    longitude = 37.6184,
                    recordedAtEpochMs = 11_000L,
                    accuracyMeters = 6f,
                ),
                ActivityRoutePoint(
                    latitude = 55.7519,
                    longitude = 37.6190,
                    recordedAtEpochMs = 13_000L,
                    accuracyMeters = 7f,
                ),
            ),
        )

        activityDao.upsertCompletedSession(
            session = plannedSession.toActivitySessionEntity(),
            routePoints = plannedSession.toRoutePointEntities().reversed(),
        )

        val storedRecord = activityDao.getActivitySessionRecord("session-1")
        val restoredSession = storedRecord?.toDomainModel()

        assertNotNull(restoredSession)
        assertEquals(ActivitySessionType.PlannedWorkout, restoredSession?.type)
        assertEquals("workout-1", restoredSession?.workoutId)
        assertEquals(listOf(11_000L, 13_000L), restoredSession?.routePoints?.map { point -> point.recordedAtEpochMs })
    }

    @Test
    fun observeActivitySessionRecordsOrdersNewestFirstAcrossFreeAndPlannedSessions() = runTest {
        activityDao.upsertCompletedSession(
            session = sampleSession(
                id = "free-1",
                type = ActivitySessionType.FreeRun,
                completedAtEpochMs = 30_000L,
            ).toActivitySessionEntity(),
            routePoints = sampleRoutePoints("free-1"),
        )
        activityDao.upsertCompletedSession(
            session = sampleSession(
                id = "planned-1",
                type = ActivitySessionType.PlannedWorkout,
                completedAtEpochMs = 45_000L,
                workoutId = "workout-1",
                workoutTitle = "Интервалы",
            ).toActivitySessionEntity(),
            routePoints = sampleRoutePoints("planned-1"),
        )

        val orderedRecords = activityDao.observeActivitySessionRecords().first()

        assertEquals(listOf("planned-1", "free-1"), orderedRecords.map { record -> record.session.id })
    }

    private fun sampleSession(
        id: String,
        type: ActivitySessionType,
        completedAtEpochMs: Long,
        workoutId: String? = null,
        workoutTitle: String? = null,
    ): CompletedActivitySession = CompletedActivitySession(
        id = id,
        type = type,
        workoutId = workoutId,
        workoutTitle = workoutTitle,
        startedAtEpochMs = completedAtEpochMs - 1_200L,
        completedAtEpochMs = completedAtEpochMs,
        durationSec = 600,
        distanceMeters = 1_500.0,
        averagePaceSecPerKm = 400,
        routePoints = listOf(
            ActivityRoutePoint(
                latitude = 55.7512,
                longitude = 37.6184,
                recordedAtEpochMs = completedAtEpochMs - 1_000L,
                accuracyMeters = 6f,
            ),
        ),
    )

    private fun sampleRoutePoints(sessionId: String): List<ActivityRoutePointEntity> = listOf(
        ActivityRoutePointEntity(
            sessionId = sessionId,
            pointIndex = 0,
            latitude = 55.7512,
            longitude = 37.6184,
            recordedAtEpochMs = 12_000L,
            accuracyMeters = 6f,
        ),
    )
}
