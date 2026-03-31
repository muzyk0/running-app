package com.vladislav.runningapp.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vladislav.runningapp.training.data.local.WorkoutDao
import com.vladislav.runningapp.training.data.local.WorkoutEntity
import com.vladislav.runningapp.training.data.local.WorkoutStepEntity

@Database(
    entities = [
        ProfileEntity::class,
        WorkoutEntity::class,
        WorkoutStepEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
@TypeConverters(ProfileTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun workoutDao(): WorkoutDao
}
