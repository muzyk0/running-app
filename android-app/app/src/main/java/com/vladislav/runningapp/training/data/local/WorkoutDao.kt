package com.vladislav.runningapp.training.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkoutDao {
    @Transaction
    @Query("SELECT * FROM workouts ORDER BY updated_at_epoch_ms DESC, id ASC")
    fun observeWorkoutRecords(): Flow<List<WorkoutRecord>>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    fun observeWorkoutRecord(workoutId: String): Flow<WorkoutRecord?>

    @Transaction
    @Query("SELECT * FROM workouts WHERE id = :workoutId")
    suspend fun getWorkoutRecord(workoutId: String): WorkoutRecord?

    @Query("SELECT * FROM workout_steps WHERE workout_id = :workoutId ORDER BY step_index ASC")
    suspend fun getWorkoutSteps(workoutId: String): List<WorkoutStepEntity>

    @Upsert
    suspend fun upsertWorkoutEntity(workout: WorkoutEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWorkoutSteps(steps: List<WorkoutStepEntity>)

    @Query("DELETE FROM workout_steps WHERE workout_id = :workoutId")
    suspend fun deleteWorkoutSteps(workoutId: String)

    @Query("DELETE FROM workouts WHERE id = :workoutId")
    suspend fun deleteWorkoutById(workoutId: String)

    @Transaction
    suspend fun upsertWorkout(
        workout: WorkoutEntity,
        steps: List<WorkoutStepEntity>,
    ) {
        upsertWorkoutEntity(workout)
        deleteWorkoutSteps(workout.id)
        if (steps.isNotEmpty()) {
            insertWorkoutSteps(steps)
        }
    }
}
