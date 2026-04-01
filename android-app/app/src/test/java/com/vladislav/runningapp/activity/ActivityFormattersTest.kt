package com.vladislav.runningapp.activity

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import com.vladislav.runningapp.core.i18n.formatWorkoutDurationLabel
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ActivityFormattersTest {
    @Test
    fun formattersUseEnglishResources() {
        val resources = resourcesFor("en-US")

        assertEquals("2 min 5 sec", formatWorkoutDurationLabel(resources, 125))
        assertEquals("1.60 km", formatDistanceLabel(resources, 1_600.0))
        assertEquals("5:15 /km", formatPaceLabel(resources, 315))
        assertEquals("Not available yet", formatPaceLabel(resources, null))
    }

    @Test
    fun formattersUseRussianResources() {
        val resources = resourcesFor("ru-RU")

        assertEquals("2 мин 5 с", formatWorkoutDurationLabel(resources, 125))
        assertEquals("1,60 км", formatDistanceLabel(resources, 1_600.0))
        assertEquals("5:15 /км", formatPaceLabel(resources, 315))
        assertEquals("Пока нет", formatPaceLabel(resources, null))
    }

    private fun resourcesFor(languageTag: String): Resources {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = Configuration(context.resources.configuration).apply {
            setLocale(Locale.forLanguageTag(languageTag))
        }
        return context.createConfigurationContext(configuration).resources
    }
}
