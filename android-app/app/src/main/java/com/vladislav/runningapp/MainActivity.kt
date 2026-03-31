package com.vladislav.runningapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.vladislav.runningapp.core.ui.RunningAppApp
import com.vladislav.runningapp.core.ui.theme.RunningAppTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RunningAppTheme {
                RunningAppApp(modifier = Modifier.fillMaxSize())
            }
        }
    }
}
