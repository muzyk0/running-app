package com.vladislav.runningapp.profile

import com.vladislav.runningapp.core.storage.ProfileDao
import com.vladislav.runningapp.core.storage.ProfileEntity
import com.vladislav.runningapp.core.storage.ProfilePromptFieldRecord
import com.vladislav.runningapp.core.storage.SingletonProfileId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class DefaultProfileRepository @Inject constructor(
    private val profileDao: ProfileDao,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = profileDao.observeProfile().map { profile ->
        profile?.toDomainModel()
    }

    override suspend fun getProfile(): UserProfile? = profileDao.getProfile()?.toDomainModel()

    override suspend fun saveProfile(profile: UserProfile) {
        profileDao.upsertProfile(profile.toEntity())
    }
}

private fun UserProfile.toEntity(): ProfileEntity = ProfileEntity(
    id = SingletonProfileId,
    heightCm = heightCm,
    weightKg = weightKg,
    sex = sex.storageValue,
    age = age,
    trainingDaysPerWeek = trainingDaysPerWeek,
    fitnessLevel = fitnessLevel.storageValue,
    injuriesAndLimitations = injuriesAndLimitations,
    trainingGoal = trainingGoal,
    additionalPromptFields = additionalPromptFields.map { field ->
        ProfilePromptFieldRecord(
            label = field.label,
            value = field.value,
        )
    },
)

private fun ProfileEntity.toDomainModel(): UserProfile = UserProfile(
    heightCm = heightCm,
    weightKg = weightKg,
    sex = UserSex.fromStorageValue(sex),
    age = age,
    trainingDaysPerWeek = trainingDaysPerWeek,
    fitnessLevel = FitnessLevel.fromStorageValue(fitnessLevel),
    injuriesAndLimitations = injuriesAndLimitations,
    trainingGoal = trainingGoal,
    additionalPromptFields = additionalPromptFields.map { field ->
        AdditionalPromptField(
            label = field.label,
            value = field.value,
        )
    },
)
