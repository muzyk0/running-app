package com.vladislav.runningapp.core.di

import com.vladislav.runningapp.core.navigation.TopLevelDestination
import com.vladislav.runningapp.core.startup.DefaultStartupDestinationResolver
import com.vladislav.runningapp.core.startup.StartupDestinationResolver
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
    fun provideTopLevelDestinations(): List<TopLevelDestination> = TopLevelDestination.entries
}
