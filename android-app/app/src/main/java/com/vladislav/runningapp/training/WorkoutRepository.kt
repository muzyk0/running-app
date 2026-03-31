package com.vladislav.runningapp.training

import com.vladislav.runningapp.training.domain.Workout
import kotlinx.coroutines.flow.Flow

interface WorkoutRepository {
    fun observeWorkouts(): Flow<List<Workout>>

    fun observeWorkout(workoutId: String): Flow<Workout?>

    suspend fun getWorkout(workoutId: String): Workout?

    suspend fun saveWorkout(workout: Workout)

    suspend fun deleteWorkout(workoutId: String)
}
