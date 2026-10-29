package com.reelcounter.feature.friends.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import androidx.navigation.navigation
import com.reelcounter.feature.friends.FriendsScreen
import com.reelcounter.feature.friends.detail.FriendDetailScreen
import kotlinx.serialization.Serializable

/** The Friends tab is a nested graph, so a friend's dashboard opened from it keeps the tab selected. */
@Serializable
data object FriendsGraph

@Serializable
data object FriendsRoute

@Serializable
data class FriendDetailRoute(val userId: String)

fun NavController.navigateToFriends(navOptions: NavOptions? = null) =
    navigate(route = FriendsGraph, navOptions = navOptions)

fun NavController.navigateToFriendDetail(userId: String) = navigate(route = FriendDetailRoute(userId))

fun NavGraphBuilder.friendsGraph(
    onOpenPrivacy: () -> Unit,
    onOpenFriend: (userId: String) -> Unit,
    onBack: () -> Unit,
) {
    navigation<FriendsGraph>(startDestination = FriendsRoute) {
        composable<FriendsRoute> {
            FriendsScreen(onOpenPrivacy = onOpenPrivacy, onOpenFriend = onOpenFriend)
        }
        composable<FriendDetailRoute> {
            FriendDetailScreen(onBack = onBack)
        }
    }
}
