package com.vladislav.runningapp.core.i18n

import android.content.Context
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

data class UiText(
    @param:StringRes val resId: Int,
    val formatArgs: List<Any> = emptyList(),
)

fun uiText(
    @StringRes resId: Int,
    vararg formatArgs: Any,
): UiText = UiText(
    resId = resId,
    formatArgs = formatArgs.toList(),
)

fun UiText.asString(resources: Resources): String = resources.getString(
    resId,
    *formatArgs.toTypedArray(),
)

fun UiText.asString(context: Context): String = asString(context.resources)

@Composable
fun UiText.asString(): String = stringResource(
    resId,
    *formatArgs.toTypedArray(),
)
