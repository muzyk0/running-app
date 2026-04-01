package com.vladislav.runningapp.core.i18n

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.vladislav.runningapp.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject

enum class SupportedAppLocale(
    val languageTag: String,
    @param:StringRes val labelResId: Int,
) {
    EnglishUs(
        languageTag = "en-US",
        labelResId = R.string.locale_label_en_us,
    ),
    Russian(
        languageTag = "ru-RU",
        labelResId = R.string.locale_label_ru_ru,
    ),
    ;

    val locale: Locale
        get() = Locale.forLanguageTag(languageTag)

    companion object {
        fun from(locale: Locale): SupportedAppLocale = when (locale.language.lowercase(Locale.US)) {
            "ru" -> Russian
            else -> EnglishUs
        }
    }
}

class AppLocaleResolver private constructor(
    private val localeProvider: () -> Locale,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        localeProvider = { context.resources.currentAppLocale() },
    )

    constructor(locale: Locale) : this(localeProvider = { locale })

    fun currentSupportedLocale(): SupportedAppLocale = SupportedAppLocale.from(localeProvider())
}

fun Resources.currentAppLocale(): Locale {
    val locales = configuration.locales
    return if (locales.isEmpty) Locale.getDefault() else locales[0]
}

fun Resources.currentSupportedLocale(): SupportedAppLocale = SupportedAppLocale.from(currentAppLocale())

fun SupportedAppLocale.displayName(resources: Resources): String = resources.getString(labelResId)

@Composable
fun currentSupportedLocaleDisplayName(): String {
    LocalConfiguration.current
    val resources = LocalContext.current.resources
    return resources.currentSupportedLocale().displayName(resources)
}
