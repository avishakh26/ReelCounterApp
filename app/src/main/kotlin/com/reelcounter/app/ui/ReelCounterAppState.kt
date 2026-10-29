package com.reelcounter.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import com.reelcounter.app.navigation.TopLevelDestination
import com.reelcounter.core.common.network.NetworkMonitor
import com.reelcounter.feature.friends.navigation.navigateToFriends
import com.reelcounter.feature.insights.navigation.navigateToInsights
import com.reelcounter.feature.settings.navigation.navigateToSettings
import com.reelcounter.feature.today.navigation.navigateToToday
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.reflect.KClass

@Composable
fun rememberReelCounterAppState(
    networkMonitor: NetworkMonitor,
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    navController: NavHostController = rememberNavController(),
): ReelCounterAppState = remember(navController, coroutineScope, networkMonitor) {
    ReelCounterAppState(
        navController = navController,
        coroutineScope = coroutineScope,
        networkMonitor = networkMonitor,
    )
}

/** App-level UI state: navigation and connectivity. Screen state lives in each feature's ViewModel. */
@Stable
class ReelCounterAppState(
    val navController: NavHostController,
    coroutineScope: CoroutineScope,
    networkMonitor: NetworkMonitor,
) {
    val topLevelDestinations: List<TopLevelDestination> = TopLevelDestination.entries

    val currentDestination: NavDestination?
        @Composable get() = navController.currentBackStackEntryAsState().value?.destination

    val isOffline: StateFlow<Boolean> = networkMonitor.isOnline
        .map(Boolean::not)
        .stateIn(
            scope = coroutineScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    /**
     * Switches tabs. Each tab keeps its own back stack: leaving a tab saves it, and returning
     * restores it. Tapping the current tab again doesn't stack a duplicate screen.
     */
    fun navigateToTopLevelDestination(destination: TopLevelDestination) {
        val options = navOptions {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        when (destination) {
            TopLevelDestination.TODAY -> navController.navigateToToday(options)
            TopLevelDestination.INSIGHTS -> navController.navigateToInsights(options)
            TopLevelDestination.FRIENDS -> navController.navigateToFriends(options)
            TopLevelDestination.SETTINGS -> navController.navigateToSettings(options)
        }
    }
}

internal fun NavDestination?.isRouteInHierarchy(route: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(route) } == true
