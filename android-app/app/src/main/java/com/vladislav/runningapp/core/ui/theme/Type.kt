package com.vladislav.runningapp.core.ui.theme

import androidx.compose.material3.Typography

val RunningAppTypography = Typography()

internal object ColorTokens {
    val surfaceVariant = Mist
    val onSurfaceVariant = Charcoal.copy(alpha = 0.74f)
    val outlineVariant = Pine.copy(alpha = 0.16f)
    val errorContainer = Clay.copy(alpha = 0.18f)
    val onErrorContainer = Charcoal
    val darkBackground = Charcoal
    val darkSurface = ColorTokens.darkBackground
    val darkSurfaceVariant = Pine.copy(alpha = 0.58f)
    val darkOnSurfaceVariant = Mist.copy(alpha = 0.82f)
    val darkOutlineVariant = Sand.copy(alpha = 0.16f)
    val darkSecondary = ColorTokens.surfaceVariant
    val darkSecondaryContainer = Pine.copy(alpha = 0.72f)
    val darkErrorContainer = Clay.copy(alpha = 0.28f)
    val darkOnErrorContainer = Sand
}
