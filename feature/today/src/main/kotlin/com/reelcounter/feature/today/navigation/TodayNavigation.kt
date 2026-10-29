package com.reelcounter.feature.today.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.reelcounter.feature.today.TodayScreen
import kotlinx.serialization.Serializable

/** The Today tab is a nested graph, so tracking setup opened from it keeps the tab selected. */
@Serializable
data object TodayGraph

@Serializable
data object TodayRoute

fun NavController.navigateToToday(navOptions: NavOptions? = null) = navigate(route = TodayGraph, navOptions = navOptions)

/**
 * @param onOpenInsights opens the Insights tab from the week card.
 * @param onOpenProfile opens the account area from the avatar in the header.
 */
fun NavGraphBuilder.todayGraph(
    onSetUpTracking: () -> Unit,
    onOpenInsights: () -> Unit = {},
    onOpenProfile: () -> Unit = {},
    nestedGraphs: NavGraphBuilder.() -> Unit = {},
) {
    navigation<TodayGraph>(startDestination = TodayRoute) {
        composable<TodayRoute> {
            TodayScreen(onSetUpTracking = onSetUpTracking, onOpenInsights = onOpenInsights, onOpenProfile = onOpenProfile)
        }
        nestedGraphs()
    }
}
