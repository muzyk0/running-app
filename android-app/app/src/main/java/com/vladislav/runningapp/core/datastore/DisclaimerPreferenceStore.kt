package com.vladislav.runningapp.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

interface DisclaimerPreferenceStore {
    fun observeDisclaimerAccepted(): Flow<Boolean>

    suspend fun readDisclaimerAccepted(): Boolean

    suspend fun setDisclaimerAccepted(accepted: Boolean)
}

@Singleton
class DefaultDisclaimerPreferenceStore @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : DisclaimerPreferenceStore {
    override fun observeDisclaimerAccepted(): Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[MedicalDisclaimerAcceptedKey] ?: false
    }

    override suspend fun readDisclaimerAccepted(): Boolean =
        dataStore.data.first()[MedicalDisclaimerAcceptedKey] ?: false

    override suspend fun setDisclaimerAccepted(accepted: Boolean) {
        dataStore.edit { preferences ->
            preferences[MedicalDisclaimerAcceptedKey] = accepted
        }
    }

    private companion object {
        val MedicalDisclaimerAcceptedKey = booleanPreferencesKey("medical_disclaimer_accepted")
    }
}
