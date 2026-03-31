package com.vladislav.runningapp.ai

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.ui.FeaturePlaceholderScreen

@Composable
fun GenerationScreen() {
    FeaturePlaceholderScreen(
        badge = stringResource(R.string.shell_badge),
        title = stringResource(R.string.screen_generation_title),
        body = stringResource(R.string.screen_generation_body),
    )
}
