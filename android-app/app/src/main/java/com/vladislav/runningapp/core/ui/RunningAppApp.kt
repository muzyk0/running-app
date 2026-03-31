package com.vladislav.runningapp.core.ui

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vladislav.runningapp.R
import com.vladislav.runningapp.core.navigation.RunningAppNavHost
import com.vladislav.runningapp.core.navigation.RunningAppNavigationState
import com.vladislav.runningapp.core.navigation.TopLevelDestination
import com.vladislav.runningapp.core.permissions.PermissionRequirementsState
import com.vladislav.runningapp.core.permissions.PermissionSnapshotFactory
import com.vladislav.runningapp.core.permissions.RequirementState
import com.vladislav.runningapp.core.permissions.permissionRequirementsFor
import kotlinx.coroutines.launch

@Composable
fun RunningAppApp(
    modifier: Modifier = Modifier,
) {
    ReadyAppShell(
        startDestination = TopLevelDestination.Profile,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReadyAppShell(
    startDestination: TopLevelDestination,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val navigationState = remember(navBackStackEntry?.destination?.route) {
        RunningAppNavigationState(currentRoute = navBackStackEntry?.destination?.route)
    }
    val scope = rememberCoroutineScope()
    var permissionsState by remember { mutableStateOf(PermissionRequirementsState()) }

    fun refreshPermissions() {
        permissionsState = permissionRequirementsFor(
            PermissionSnapshotFactory.fromContext(context),
        )
    }

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        refreshPermissions()
    }

    DisposableEffect(context, lifecycleOwner) {
        refreshPermissions()
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    TopLevelDestination.entries.forEach { destination ->
                        NavigationDrawerItem(
                            label = { Text(stringResource(destination.titleRes)) },
                            selected = destination == navigationState.currentTopLevelDestination,
                            icon = {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = null,
                                )
                            },
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.startDestinationId) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                                scope.launch {
                                    drawerState.close()
                                }
                            },
                        )
                    }
                }
            }
        },
    ) {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Text(text = stringResource(navigationState.currentTopLevelDestination.titleRes))
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                scope.launch {
                                    drawerState.open()
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Menu,
                                contentDescription = stringResource(R.string.navigation_open_menu),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TrackingReadinessCard(
                    state = permissionsState,
                    onRequestPermissions = {
                        permissionsLauncher.launch(
                            PermissionSnapshotFactory.runtimePermissions(Build.VERSION.SDK_INT).toTypedArray(),
                        )
                    },
                )
                RunningAppNavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun TrackingReadinessCard(
    state: PermissionRequirementsState,
    onRequestPermissions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.permissions_title),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = if (state.canStartTrackedSessions) {
                    stringResource(R.string.permissions_summary_ready)
                } else {
                    stringResource(R.string.permissions_summary_missing)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusChip(
                    label = stringResource(R.string.permission_location),
                    state = state.location,
                )
                StatusChip(
                    label = stringResource(R.string.permission_notifications),
                    state = state.notifications,
                )
                StatusChip(
                    label = stringResource(R.string.permission_foreground_tracking),
                    state = state.foregroundTracking,
                )
            }
            if (!state.canStartTrackedSessions) {
                Button(onClick = onRequestPermissions) {
                    Text(text = stringResource(R.string.permissions_request))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    label: String,
    state: RequirementState,
    modifier: Modifier = Modifier,
) {
    AssistChip(
        modifier = modifier,
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = when (state) {
                    RequirementState.Available -> "$label: ${stringResource(R.string.permission_status_available)}"
                    RequirementState.Missing, RequirementState.Pending -> "$label: ${stringResource(R.string.permission_status_missing)}"
                    RequirementState.NotRequired -> "$label: ${stringResource(R.string.permission_status_not_required)}"
                },
            )
        },
        shape = CircleShape,
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = when (state) {
                RequirementState.Available -> MaterialTheme.colorScheme.secondaryContainer
                RequirementState.NotRequired -> MaterialTheme.colorScheme.surfaceVariant
                RequirementState.Pending, RequirementState.Missing -> MaterialTheme.colorScheme.errorContainer
            },
            disabledLabelColor = when (state) {
                RequirementState.Available -> MaterialTheme.colorScheme.onSecondaryContainer
                RequirementState.NotRequired -> MaterialTheme.colorScheme.onSurfaceVariant
                RequirementState.Pending, RequirementState.Missing -> MaterialTheme.colorScheme.onErrorContainer
            },
        ),
    )
}
