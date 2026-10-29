package com.reelcounter.core.model

/** Another user's public card: what search and friend lists may show. Never an email. */
data class UserSummary(
    val id: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
) {
    val nameForDisplay: String get() = displayName ?: username
}

/** How another user relates to the signed-in user. */
enum class Relationship { None, Friend, IncomingRequest, OutgoingRequest }

data class Connection(val user: UserSummary, val relationship: Relationship)

/** Mirrors the `visibility_level` enum in Postgres. */
enum class Visibility { Public, Friends, Private }

data class PrivacySettings(
    /** Who can see daily activity. The app currently offers Friends and Private ("Only me"). */
    val activityVisibility: Visibility,
    /** Opt-in: friends see when you're using the app. Off by default. */
    val shareOnlineStatus: Boolean,
    /** Whether username search can find you. */
    val discoverable: Boolean,
    val acceptFriendRequests: Boolean,
)
