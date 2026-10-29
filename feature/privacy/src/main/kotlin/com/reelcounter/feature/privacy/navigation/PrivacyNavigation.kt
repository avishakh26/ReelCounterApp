package com.reelcounter.feature.privacy.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.reelcounter.feature.privacy.PrivacyScreen
import kotlinx.serialization.Serializable

@Serializable
data object PrivacyRoute

fun NavController.navigateToPrivacy(navOptions: NavOptions? = null) =
    navigate(route = PrivacyRoute, navOptions = navOptions)

fun NavGraphBuilder.privacyScreen(onBack: () -> Unit) {
    composable<PrivacyRoute> { PrivacyScreen(onBack = onBack) }
}
