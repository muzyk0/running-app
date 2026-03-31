package com.vladislav.runningapp.profile

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vladislav.runningapp.core.datastore.DefaultDisclaimerPreferenceStore
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisclaimerPreferenceStoreTest {
    @Test
    fun defaultsToFalseAndPersistsAcceptance() = runTest {
        val tempFile = File.createTempFile("profile-disclaimer", ".preferences_pb")
        tempFile.deleteOnExit()
        val store = DefaultDisclaimerPreferenceStore(
            dataStore = PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { tempFile },
            ),
        )

        assertFalse(store.readDisclaimerAccepted())

        store.setDisclaimerAccepted(accepted = true)

        assertTrue(store.readDisclaimerAccepted())
        assertTrue(store.observeDisclaimerAccepted().first())
    }
}

fun sampleProfile(): UserProfile = UserProfile(
    heightCm = 175,
    weightKg = 70,
    sex = UserSex.Other,
    age = 31,
    trainingDaysPerWeek = 5,
    fitnessLevel = FitnessLevel.Advanced,
    injuriesAndLimitations = "осторожно с голеностопом",
    trainingGoal = "поддерживать форму",
    additionalPromptFields = listOf(
        AdditionalPromptField(label = "Любимое время", value = "вечер"),
    ),
)
