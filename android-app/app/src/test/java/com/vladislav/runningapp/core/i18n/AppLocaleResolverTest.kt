package com.vladislav.runningapp.core.i18n

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLocaleResolverTest {
    @Test
    fun mapsRussianLocalesToRussianSupport() {
        val resolver = AppLocaleResolver(Locale.forLanguageTag("ru-KZ"))

        assertEquals(
            SupportedAppLocale.Russian,
            resolver.currentSupportedLocale(),
        )
    }

    @Test
    fun fallsBackUnsupportedLocalesToEnglishUs() {
        val resolver = AppLocaleResolver(Locale.FRANCE)

        assertEquals(
            SupportedAppLocale.EnglishUs,
            resolver.currentSupportedLocale(),
        )
    }
}
