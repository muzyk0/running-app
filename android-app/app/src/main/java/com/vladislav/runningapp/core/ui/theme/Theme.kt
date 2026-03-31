package com.vladislav.runningapp.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Clay,
    onPrimary = Sand,
    primaryContainer = Sand,
    onPrimaryContainer = Charcoal,
    secondary = Moss,
    onSecondary = Mist,
    secondaryContainer = Mist,
    onSecondaryContainer = Pine,
    tertiary = Pine,
    onTertiary = Sand,
    background = Mist,
    onBackground = Charcoal,
    surface = Sand,
    onSurface = Charcoal,
    surfaceVariant = ColorTokens.surfaceVariant,
    onSurfaceVariant = ColorTokens.onSurfaceVariant,
    outlineVariant = ColorTokens.outlineVariant,
    errorContainer = ColorTokens.errorContainer,
    onErrorContainer = ColorTokens.onErrorContainer,
)

private val DarkColors = darkColorScheme(
    primary = Sand,
    onPrimary = Pine,
    primaryContainer = Pine,
    onPrimaryContainer = Sand,
    secondary = ColorTokens.darkSecondary,
    onSecondary = Charcoal,
    secondaryContainer = ColorTokens.darkSecondaryContainer,
    onSecondaryContainer = Sand,
    tertiary = Clay,
    onTertiary = Sand,
    background = ColorTokens.darkBackground,
    onBackground = Mist,
    surface = ColorTokens.darkSurface,
    onSurface = Mist,
    surfaceVariant = ColorTokens.darkSurfaceVariant,
    onSurfaceVariant = ColorTokens.darkOnSurfaceVariant,
    outlineVariant = ColorTokens.darkOutlineVariant,
    errorContainer = ColorTokens.darkErrorContainer,
    onErrorContainer = ColorTokens.darkOnErrorContainer,
)

@Composable
fun RunningAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = RunningAppTypography,
        content = content,
    )
}
