package com.vladislav.runningapp.training.data.local

import com.vladislav.runningapp.training.WorkoutRepository
import com.vladislav.runningapp.training.domain.Workout
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultWorkoutRepository @Inject constructor(
    private val workoutDao: WorkoutDao,
) : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<Workout>> = workoutDao.observeWorkoutRecords().map { records ->
        records.map { record -> record.toDomainModel() }
    }

    override fun observeWorkout(workoutId: String): Flow<Workout?> =
        workoutDao.observeWorkoutRecord(workoutId).map { record ->
            record?.toDomainModel()
        }

    override suspend fun getWorkout(workoutId: String): Workout? =
        workoutDao.getWorkoutRecord(workoutId)?.toDomainModel()

    override suspend fun saveWorkout(workout: Workout) {
        workoutDao.upsertWorkout(
            workout = workout.toWorkoutEntity(updatedAtEpochMs = System.currentTimeMillis()),
            steps = workout.toWorkoutStepEntities(),
        )
    }

    override suspend fun deleteWorkout(workoutId: String) {
        workoutDao.deleteWorkoutById(workoutId)
    }
}
