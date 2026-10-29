package com.reelcounter.feature.profile.navigation

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavOptions
import androidx.navigation.compose.composable
import com.reelcounter.feature.profile.ProfileEditorMode
import com.reelcounter.feature.profile.ProfileEditorScreen
import kotlinx.serialization.Serializable

@Serializable
data object EditProfileRoute

fun NavController.navigateToEditProfile(navOptions: NavOptions? = null) =
    navigate(route = EditProfileRoute, navOptions = navOptions)

fun NavGraphBuilder.editProfileScreen(onBackClick: () -> Unit) {
    composable<EditProfileRoute> {
        ProfileEditorScreen(mode = ProfileEditorMode.Edit, onBack = onBackClick)
    }
}
