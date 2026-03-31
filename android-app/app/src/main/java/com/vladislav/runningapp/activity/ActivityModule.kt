package com.vladislav.runningapp.activity

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.vladislav.runningapp.activity.data.local.DefaultActivityRepository
import com.vladislav.runningapp.activity.service.ActiveSessionServiceController
import com.vladislav.runningapp.activity.service.AndroidActiveSessionServiceController
import com.vladislav.runningapp.activity.service.AndroidLocationUpdatesClient
import com.vladislav.runningapp.activity.service.LocationUpdatesClient
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ActivityModule {
    @Binds
    @Singleton
    abstract fun bindActivityRepository(
        defaultActivityRepository: DefaultActivityRepository,
    ): ActivityRepository

    @Binds
    @Singleton
    abstract fun bindActivityTracker(
        defaultActivityTracker: DefaultActivityTracker,
    ): ActivityTracker

    @Binds
    @Singleton
    abstract fun bindLocationUpdatesClient(
        androidLocationUpdatesClient: AndroidLocationUpdatesClient,
    ): LocationUpdatesClient

    @Binds
    @Singleton
    abstract fun bindActiveSessionServiceController(
        androidActiveSessionServiceController: AndroidActiveSessionServiceController,
    ): ActiveSessionServiceController

    companion object {
        @Provides
        @Singleton
        fun provideFusedLocationProviderClient(
            @ApplicationContext context: Context,
        ): FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(context)
    }
}
