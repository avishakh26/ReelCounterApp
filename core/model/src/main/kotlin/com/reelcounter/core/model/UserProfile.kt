package com.reelcounter.core.model

/**
 * The signed-in user's public profile. [username] is null until onboarding is finished;
 * [avatarUrl] is a ready-to-load public URL.
 */
data class UserProfile(
    val id: String,
    val username: String?,
    val displayName: String?,
    val bio: String?,
    val avatarUrl: String?,
) {
    /** A profile is usable once it has a username; everything else is optional. */
    val isComplete: Boolean get() = username != null
}
