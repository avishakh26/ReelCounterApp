package com.reelcounter.app.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteDefaults
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.app.R
import com.reelcounter.app.navigation.ReelCounterNavHost

/**
 * The app shell. Navigation adapts to the window: a bottom bar on phones held upright, and a side
 * rail once the window is at least [RailMinWidthDp] wide (phones in landscape, foldables, tablets). Losing connectivity shows a persistent snackbar until the device is back online.
 */
@Composable
fun ReelCounterApp(
    appState: ReelCounterAppState,
    modifier: Modifier = Modifier,
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val isOffline by appState.isOffline.collectAsStateWithLifecycle()
    val offlineMessage = stringResource(R.string.offline_snackbar)

    // Restarting on reconnect cancels showSnackbar, which dismisses the notice.
    LaunchedEffect(isOffline) {
        if (isOffline) {
            snackbarHostState.showSnackbar(message = offlineMessage, duration = SnackbarDuration.Indefinite)
        }
    }

    val currentDestination = appState.currentDestination
    val colors = MaterialTheme.colorScheme
    // Calm chrome: the bar matches the cards, and only the active tab uses the accent.
    val itemColors = NavigationSuiteDefaults.itemColors(
        navigationBarItemColors = NavigationBarItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.onSurface,
            indicatorColor = colors.primaryContainer,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
        ),
        navigationRailItemColors = NavigationRailItemDefaults.colors(
            selectedIconColor = colors.primary,
            selectedTextColor = colors.onSurface,
            indicatorColor = colors.primaryContainer,
            unselectedIconColor = colors.onSurfaceVariant,
            unselectedTextColor = colors.onSurfaceVariant,
        ),
    )
    // The library default keeps the bottom bar whenever the window is short, which in landscape
    // leaves a quarter of the screen to the bar. A rail on the side keeps the height for content.
    val windowWidthDp = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp().value }
    val layoutType = if (windowWidthDp >= RailMinWidthDp) {
        NavigationSuiteType.NavigationRail
    } else {
        NavigationSuiteType.NavigationBar
    }
    NavigationSuiteScaffold(
        modifier = modifier,
        layoutType = layoutType,
        navigationSuiteColors = NavigationSuiteDefaults.colors(
            shortNavigationBarContainerColor = colors.surfaceContainerLowest,
            navigationBarContainerColor = colors.surfaceContainerLowest,
            navigationRailContainerColor = colors.background,
            navigationDrawerContainerColor = colors.background,
        ),
        containerColor = colors.background,
        navigationSuiteItems = {
            appState.topLevelDestinations.forEach { destination ->
                val selected = currentDestination.isRouteInHierarchy(destination.baseRoute)
                item(
                    selected = selected,
                    onClick = { appState.navigateToTopLevelDestination(destination) },
                    icon = {
                        Icon(
                            imageVector = if (selected) destination.selectedIcon else destination.unselectedIcon,
                            contentDescription = null,
                        )
                    },
                    label = { Text(text = stringResource(destination.labelRes), style = MaterialTheme.typography.labelMedium) },
                    colors = itemColors,
                )
            }
        },
    ) {
        // Screens draw their own top bars and insets; this Scaffold only hosts the snackbar.
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            ReelCounterNavHost(
                appState = appState,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
            )
        }
    }
}

/** Window width (dp) from which navigation moves from the bottom bar to a side rail. */
private const val RailMinWidthDp = 600
