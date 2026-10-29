package com.reelcounter.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import com.reelcounter.app.ui.ReelCounterAppState
import com.reelcounter.feature.friends.navigation.friendsGraph
import com.reelcounter.feature.friends.navigation.navigateToFriendDetail
import com.reelcounter.feature.insights.navigation.insightsScreen
import com.reelcounter.feature.privacy.navigation.navigateToPrivacy
import com.reelcounter.feature.privacy.navigation.privacyScreen
import com.reelcounter.feature.profile.navigation.editProfileScreen
import com.reelcounter.feature.profile.navigation.navigateToEditProfile
import com.reelcounter.feature.settings.navigation.navigateToHowWeCount
import com.reelcounter.feature.settings.navigation.settingsGraph
import com.reelcounter.feature.today.navigation.TodayGraph
import com.reelcounter.feature.today.navigation.todayGraph
import com.reelcounter.feature.tracking.navigation.navigateToTrackingSettings
import com.reelcounter.feature.tracking.navigation.navigateToTrackingSetup
import com.reelcounter.feature.tracking.navigation.trackingSettingsScreen
import com.reelcounter.feature.tracking.navigation.trackingSetupScreen

/**
 * The app's navigation graph. Features expose their destinations as NavGraphBuilder extensions and
 * never reference each other; any navigation between features is wired here.
 */
@Composable
fun ReelCounterNavHost(
    appState: ReelCounterAppState,
    modifier: Modifier = Modifier,
) {
    val navController = appState.navController
    val back: () -> Unit = { navController.popBackStack() }
    NavHost(
        navController = navController,
        startDestination = TodayGraph,
        modifier = modifier,
    ) {
        todayGraph(
            onSetUpTracking = { navController.navigateToTrackingSetup() },
            onOpenInsights = { appState.navigateToTopLevelDestination(TopLevelDestination.INSIGHTS) },
            onOpenProfile = { appState.navigateToTopLevelDestination(TopLevelDestination.SETTINGS) },
            nestedGraphs = {
                trackingSetupScreen(onFinished = back)
            },
        )
        insightsScreen()
        friendsGraph(
            onOpenPrivacy = { navController.navigateToPrivacy() },
            onOpenFriend = { navController.navigateToFriendDetail(it) },
            onBack = back,
        )
        settingsGraph(
            onEditProfileClick = { navController.navigateToEditProfile() },
            onTrackingClick = { navController.navigateToTrackingSettings() },
            onPrivacyClick = { navController.navigateToPrivacy() },
            onHowWeCountClick = { navController.navigateToHowWeCount() },
            onBackClick = back,
            nestedGraphs = {
                editProfileScreen(onBackClick = back)
                trackingSettingsScreen(onBack = back, onSetUpTracking = { navController.navigateToTrackingSetup() })
                privacyScreen(onBack = back)
            },
        )
    }
}
