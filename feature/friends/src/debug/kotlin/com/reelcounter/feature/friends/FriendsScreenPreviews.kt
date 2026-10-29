package com.reelcounter.feature.friends

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.PreviewLightDark
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.model.Connection
import com.reelcounter.core.model.Relationship
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.preview.PreviewTheme

private val maya = UserSummary(id = "1", username = "maya.k", displayName = "Maya", avatarUrl = null)
private val sam = UserSummary(id = "2", username = "sam_r", displayName = null, avatarUrl = null)
private val jo = UserSummary(id = "3", username = "jo", displayName = "Jo Park", avatarUrl = null)
private val lee = UserSummary(id = "4", username = "lee99", displayName = "Lee", avatarUrl = null)

@PreviewLightDark
@Composable
private fun FriendsScreenConnectionsPreview() = PreviewTheme {
    FriendsScreen(
        uiState = FriendsUiState(
            connections = ConnectionsState.Loaded(friends = listOf(maya, sam), incoming = listOf(jo), outgoing = listOf(lee)),
            onlineIds = setOf(maya.id),
            activityVisibility = Visibility.Friends,
        ),
        actions = FriendsActions(),
    )
}

@PreviewLightDark
@Composable
private fun FriendsScreenEmptyPreview() = PreviewTheme {
    FriendsScreen(
        uiState = FriendsUiState(
            connections = ConnectionsState.Loaded(friends = emptyList(), incoming = emptyList(), outgoing = emptyList()),
            activityVisibility = Visibility.Private,
        ),
        actions = FriendsActions(),
    )
}

@PreviewLightDark
@Composable
private fun FriendsScreenSearchPreview() = PreviewTheme {
    FriendsScreen(
        uiState = FriendsUiState(
            search = SearchState(
                query = "ma",
                results = listOf(
                    Connection(maya, Relationship.Friend),
                    Connection(jo, Relationship.IncomingRequest),
                    Connection(lee, Relationship.OutgoingRequest),
                    Connection(sam, Relationship.None),
                ),
            ),
            activityVisibility = Visibility.Friends,
        ),
        actions = FriendsActions(),
    )
}
