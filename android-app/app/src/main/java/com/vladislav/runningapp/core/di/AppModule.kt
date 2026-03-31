package com.vladislav.runningapp.core.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.vladislav.runningapp.core.navigation.TopLevelDestination
import com.vladislav.runningapp.core.startup.DefaultStartupDestinationResolver
import com.vladislav.runningapp.core.startup.StartupDestinationResolver
import com.vladislav.runningapp.core.storage.AppDatabase
import com.vladislav.runningapp.core.storage.ProfileDao
import dagger.Binds
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
abstract class StartupModule {
    @Binds
    @Singleton
    abstract fun bindStartupDestinationResolver(
        defaultStartupDestinationResolver: DefaultStartupDestinationResolver,
    ): StartupDestinationResolver
}

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
    ).build()

    @Provides
    fun provideProfileDao(
        appDatabase: AppDatabase,
    ): ProfileDao = appDatabase.profileDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("running-app.preferences_pb") },
    )

    @Provides
    @Singleton
    fun provideTopLevelDestinations(): List<TopLevelDestination> = TopLevelDestination.entries
}
