package com.vladislav.runningapp.activity

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.ui.FeaturePlaceholderScreen

@Composable
fun HistoryScreen() {
    FeaturePlaceholderScreen(
        badge = stringResource(R.string.shell_badge),
        title = stringResource(R.string.screen_history_title),
        body = stringResource(R.string.screen_history_body),
    )
}
