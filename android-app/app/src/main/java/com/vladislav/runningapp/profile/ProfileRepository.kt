package com.vladislav.runningapp.profile

import kotlinx.coroutines.flow.Flow

interface ProfileRepository {
    fun observeProfile(): Flow<UserProfile?>

    suspend fun getProfile(): UserProfile?

    suspend fun saveProfile(profile: UserProfile)
}
