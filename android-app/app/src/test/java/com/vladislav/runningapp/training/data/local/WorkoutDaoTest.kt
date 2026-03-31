package com.vladislav.runningapp.training.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vladislav.runningapp.core.storage.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WorkoutDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var workoutDao: WorkoutDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutDao = database.workoutDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertWorkoutStoresWorkoutAndReturnsStepsInIndexOrder() = runTest {
        workoutDao.upsertWorkout(
            workout = sampleWorkoutEntity(),
            steps = listOf(
                sampleStepEntity(stepIndex = 2, type = "cooldown"),
                sampleStepEntity(stepIndex = 0, type = "warmup"),
                sampleStepEntity(stepIndex = 1, type = "run"),
            ),
        )

        val storedRecord = workoutDao.getWorkoutRecord("workout-1")
        val orderedSteps = workoutDao.getWorkoutSteps("workout-1")

        requireNotNull(storedRecord)
        assertEquals("Интервалы", storedRecord.workout.title)
        assertEquals(listOf(0, 1, 2), orderedSteps.map { step -> step.stepIndex })
        assertEquals(listOf("warmup", "run", "cooldown"), orderedSteps.map { step -> step.type })
    }

    @Test
    fun deleteWorkoutRemovesChildStepRows() = runTest {
        workoutDao.upsertWorkout(
            workout = sampleWorkoutEntity(),
            steps = listOf(
                sampleStepEntity(stepIndex = 0, type = "warmup"),
            ),
        )

        workoutDao.deleteWorkoutById("workout-1")

        assertNull(workoutDao.getWorkoutRecord("workout-1"))
        assertTrue(workoutDao.getWorkoutSteps("workout-1").isEmpty())
    }

    private fun sampleWorkoutEntity(): WorkoutEntity = WorkoutEntity(
        id = "workout-1",
        schemaVersion = "mvp.v1",
        title = "Интервалы",
        summary = "Легкий блок",
        goal = "Поддерживать форму",
        disclaimer = null,
        updatedAtEpochMs = 123L,
    )

    private fun sampleStepEntity(
        stepIndex: Int,
        type: String,
    ): WorkoutStepEntity = WorkoutStepEntity(
        workoutId = "workout-1",
        stepIndex = stepIndex,
        type = type,
        durationSec = 60 * (stepIndex + 1),
        voicePrompt = "Шаг $stepIndex",
    )
}
