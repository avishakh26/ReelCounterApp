package com.reelcounter.core.model

/**
 * A short-form video platform. Platforms come from the server's `platforms` registry, so this is a
 * value rather than an enum. [displayName] is used nominatively; the app never shows platform logos.
 */
data class Platform(
    val id: String,
    val displayName: String,
)
