package com.vladislav.runningapp.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vladislav.runningapp.activity.data.local.ActivityDao
import com.vladislav.runningapp.activity.data.local.ActivityRoutePointEntity
import com.vladislav.runningapp.activity.data.local.ActivitySessionEntity
import com.vladislav.runningapp.training.data.local.WorkoutDao
import com.vladislav.runningapp.training.data.local.WorkoutEntity
import com.vladislav.runningapp.training.data.local.WorkoutStepEntity

@Database(
    entities = [
        ProfileEntity::class,
        WorkoutEntity::class,
        WorkoutStepEntity::class,
        ActivitySessionEntity::class,
        ActivityRoutePointEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(ProfileTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    abstract fun workoutDao(): WorkoutDao

    abstract fun activityDao(): ActivityDao
}
