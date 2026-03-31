package com.vladislav.runningapp.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [ProfileEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(ProfileTypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
}
