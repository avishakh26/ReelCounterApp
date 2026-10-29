package com.reelcounter.feature.settings.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.reelcounter.feature.settings.HowWeCountScreen
import com.reelcounter.feature.settings.SettingsScreen
import kotlinx.serialization.Serializable

/** The settings tab is a nested graph, so its sub-screens keep the tab selected and restore on return. */
@Serializable
data object SettingsGraph

@Serializable
data object SettingsRoute

@Serializable
data object HowWeCountRoute

fun NavController.navigateToSettings(navOptions: NavOptions? = null) =
    navigate(route = SettingsGraph, navOptions = navOptions)

fun NavController.navigateToHowWeCount(navOptions: NavOptions? = null) =
    navigate(route = HowWeCountRoute, navOptions = navOptions)

/**
 * @param nestedGraphs destinations from other features that belong under this tab (e.g. Edit
 * profile), added by the app so this feature doesn't depend on them.
 */
fun NavGraphBuilder.settingsGraph(
    onEditProfileClick: () -> Unit,
    onTrackingClick: () -> Unit,
    onPrivacyClick: () -> Unit,
    onHowWeCountClick: () -> Unit,
    onBackClick: () -> Unit,
    nestedGraphs: NavGraphBuilder.() -> Unit = {},
) {
    navigation<SettingsGraph>(startDestination = SettingsRoute) {
        composable<SettingsRoute> {
            SettingsScreen(
                onEditProfileClick = onEditProfileClick,
                onTrackingClick = onTrackingClick,
                onPrivacyClick = onPrivacyClick,
                onHowWeCountClick = onHowWeCountClick,
            )
        }
        composable<HowWeCountRoute> {
            HowWeCountScreen(onBackClick = onBackClick)
        }
        nestedGraphs()
    }
}
