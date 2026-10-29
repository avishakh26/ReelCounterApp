package com.reelcounter.feature.insights.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.reelcounter.feature.insights.InsightsScreen
import kotlinx.serialization.Serializable

@Serializable
data object InsightsRoute

fun NavController.navigateToInsights(navOptions: NavOptions? = null) =
    navigate(route = InsightsRoute, navOptions = navOptions)

fun NavGraphBuilder.insightsScreen() {
    composable<InsightsRoute> {
        InsightsScreen()
    }
}
