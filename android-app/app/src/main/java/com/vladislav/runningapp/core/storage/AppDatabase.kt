package com.vladislav.runningapp.core.storage

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workouts` (
                        `id` TEXT NOT NULL,
                        `schema_version` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `summary` TEXT,
                        `goal` TEXT,
                        `disclaimer` TEXT,
                        `updated_at_epoch_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `workout_steps` (
                        `workout_id` TEXT NOT NULL,
                        `step_index` INTEGER NOT NULL,
                        `type` TEXT NOT NULL,
                        `duration_sec` INTEGER NOT NULL,
                        `voice_prompt` TEXT NOT NULL,
                        PRIMARY KEY(`workout_id`, `step_index`),
                        FOREIGN KEY(`workout_id`) REFERENCES `workouts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_workout_steps_workout_id`
                    ON `workout_steps` (`workout_id`)
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_sessions` (
                        `id` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `workoutId` TEXT,
                        `workoutTitle` TEXT,
                        `startedAtEpochMs` INTEGER NOT NULL,
                        `completedAtEpochMs` INTEGER NOT NULL,
                        `durationSec` INTEGER NOT NULL,
                        `distanceMeters` REAL NOT NULL,
                        `averagePaceSecPerKm` INTEGER,
                        PRIMARY KEY(`id`),
                        FOREIGN KEY(`workoutId`) REFERENCES `workouts`(`id`) ON UPDATE CASCADE ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_activity_sessions_completedAtEpochMs`
                    ON `activity_sessions` (`completedAtEpochMs`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_activity_sessions_workoutId`
                    ON `activity_sessions` (`workoutId`)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `activity_route_points` (
                        `sessionId` TEXT NOT NULL,
                        `pointIndex` INTEGER NOT NULL,
                        `latitude` REAL NOT NULL,
                        `longitude` REAL NOT NULL,
                        `recordedAtEpochMs` INTEGER NOT NULL,
                        `accuracyMeters` REAL NOT NULL,
                        PRIMARY KEY(`sessionId`, `pointIndex`),
                        FOREIGN KEY(`sessionId`) REFERENCES `activity_sessions`(`id`) ON UPDATE CASCADE ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS `index_activity_route_points_sessionId`
                    ON `activity_route_points` (`sessionId`)
                    """.trimIndent(),
                )
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
        )
    }
}
