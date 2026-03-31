package com.vladislav.runningapp.training

import com.vladislav.runningapp.training.data.local.DefaultWorkoutRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TrainingModule {
    @Binds
    @Singleton
    abstract fun bindWorkoutRepository(
        defaultWorkoutRepository: DefaultWorkoutRepository,
    ): WorkoutRepository
}
