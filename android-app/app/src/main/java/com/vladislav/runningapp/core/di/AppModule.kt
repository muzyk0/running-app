package com.vladislav.runningapp.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.vladislav.runningapp.activity.data.local.ActivityDao
import com.vladislav.runningapp.core.navigation.TopLevelDestination
import com.vladislav.runningapp.core.permissions.AndroidTrackingPermissionChecker
import com.vladislav.runningapp.core.permissions.TrackingPermissionChecker
import com.vladislav.runningapp.core.storage.AppDatabase
import com.vladislav.runningapp.core.storage.ProfileDao
import com.vladislav.runningapp.training.data.local.WorkoutDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "running-app.db",
    ).addMigrations(*AppDatabase.ALL_MIGRATIONS).build()

    @Provides
    fun provideProfileDao(
        appDatabase: AppDatabase,
    ): ProfileDao = appDatabase.profileDao()

    @Provides
    fun provideWorkoutDao(
        appDatabase: AppDatabase,
    ): WorkoutDao = appDatabase.workoutDao()

    @Provides
    fun provideActivityDao(
        appDatabase: AppDatabase,
    ): ActivityDao = appDatabase.activityDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("running-app.preferences_pb") },
    )

    @Provides
    @Singleton
    fun provideTrackingPermissionChecker(
        @ApplicationContext context: Context,
    ): TrackingPermissionChecker = AndroidTrackingPermissionChecker(context)

    @Provides
    @Singleton
    fun provideTopLevelDestinations(): List<TopLevelDestination> = TopLevelDestination.entries
}
