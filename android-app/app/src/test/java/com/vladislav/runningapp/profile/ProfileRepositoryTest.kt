package com.vladislav.runningapp.profile

import com.vladislav.runningapp.core.storage.ProfileDao
import com.vladislav.runningapp.core.storage.ProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRepositoryTest {
    @Test
    fun savesAndReadsProfileThroughDaoMapping() = runTest {
        val dao = FakeProfileDao()
        val repository = DefaultProfileRepository(dao)
        val profile = sampleProfile()

        assertNull(repository.getProfile())

        repository.saveProfile(profile)

        assertEquals(profile, repository.getProfile())
        assertEquals(profile, repository.observeProfile().first())
    }

    private class FakeProfileDao : ProfileDao {
        private val state = MutableStateFlow<ProfileEntity?>(null)

        override fun observeProfile(): Flow<ProfileEntity?> = state

        override suspend fun getProfile(): ProfileEntity? = state.value

        override suspend fun upsertProfile(profile: ProfileEntity) {
            state.value = profile
        }
    }
}
