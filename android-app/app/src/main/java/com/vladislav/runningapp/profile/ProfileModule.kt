package com.vladislav.runningapp.profile

import com.vladislav.runningapp.core.datastore.DefaultDisclaimerPreferenceStore
import com.vladislav.runningapp.core.datastore.DisclaimerPreferenceStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileModule {
    @Binds
    @Singleton
    abstract fun bindProfileRepository(
        defaultProfileRepository: DefaultProfileRepository,
    ): ProfileRepository

    @Binds
    @Singleton
    abstract fun bindDisclaimerPreferenceStore(
        defaultDisclaimerPreferenceStore: DefaultDisclaimerPreferenceStore,
    ): DisclaimerPreferenceStore
}
