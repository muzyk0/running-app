package com.vladislav.runningapp.session

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.ui.FeaturePlaceholderScreen

@Composable
fun ActiveSessionScreen() {
    FeaturePlaceholderScreen(
        badge = stringResource(R.string.shell_badge),
        title = stringResource(R.string.screen_active_session_title),
        body = stringResource(R.string.screen_active_session_body),
    )
}
