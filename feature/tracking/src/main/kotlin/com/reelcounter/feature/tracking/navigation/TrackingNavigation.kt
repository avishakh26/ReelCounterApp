package com.reelcounter.feature.tracking.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.reelcounter.feature.tracking.settings.TrackingSettingsScreen
import com.reelcounter.feature.tracking.setup.TrackingSetupScreen
import kotlinx.serialization.Serializable

@Serializable
data object TrackingSetupRoute

@Serializable
data object TrackingSettingsRoute

fun NavController.navigateToTrackingSetup(navOptions: NavOptions? = null) =
    navigate(route = TrackingSetupRoute, navOptions = navOptions)

fun NavController.navigateToTrackingSettings(navOptions: NavOptions? = null) =
    navigate(route = TrackingSettingsRoute, navOptions = navOptions)

fun NavGraphBuilder.trackingSetupScreen(onFinished: () -> Unit) {
    composable<TrackingSetupRoute> { TrackingSetupScreen(onFinished = onFinished) }
}

fun NavGraphBuilder.trackingSettingsScreen(onBack: () -> Unit, onSetUpTracking: () -> Unit) {
    composable<TrackingSettingsRoute> { TrackingSettingsScreen(onBack = onBack, onSetUpTracking = onSetUpTracking) }
}
