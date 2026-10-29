package com.reelcounter.core.domain.tracking

import com.reelcounter.core.model.Platform

/**
 * A platform the app can measure: its Android packages and estimation defaults.
 * Package names must also be listed in :core:tracking's manifest <queries> (Android 11+
 * package visibility), so adding a platform needs an app update.
 */
data class TrackedPlatform(
    val platform: Platform,
    val packages: Set<String>,
    /** Decision D1 (revised 2026-10-29): every platform shows an estimated reel count by default. */
    val estimateByDefault: Boolean,
    val profile: EstimationProfile,
)

/** Mirrors the `platforms` seed in docs/phase-1/03-database-schema.md. */
object PlatformCatalog {
    private const val PROFILE_VERSION = 1

    // All estimation numbers below are unvalidated placeholders (see EstimationProfile).
    val TikTok = TrackedPlatform(
        platform = Platform(id = "tiktok", displayName = "TikTok"),
        packages = setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        estimateByDefault = true,
        profile = EstimationProfile(PROFILE_VERSION, 0.95, 0.85, 1.0, 21.0, 12.0, 35.0),
    )

    val Instagram = TrackedPlatform(
        platform = Platform(id = "instagram", displayName = "Instagram"),
        packages = setOf("com.instagram.android", "com.instagram.lite"),
        estimateByDefault = true,
        profile = EstimationProfile(PROFILE_VERSION, 0.55, 0.30, 0.80, 18.0, 10.0, 30.0),
    )

    val YouTube = TrackedPlatform(
        platform = Platform(id = "youtube", displayName = "YouTube"),
        packages = setOf("com.google.android.youtube"),
        estimateByDefault = true,
        profile = EstimationProfile(PROFILE_VERSION, 0.30, 0.10, 0.60, 30.0, 15.0, 50.0),
    )

    val Facebook = TrackedPlatform(
        platform = Platform(id = "facebook", displayName = "Facebook"),
        packages = setOf("com.facebook.katana", "com.facebook.lite"),
        estimateByDefault = true,
        profile = EstimationProfile(PROFILE_VERSION, 0.20, 0.05, 0.50, 20.0, 10.0, 35.0),
    )

    val all: List<TrackedPlatform> = listOf(TikTok, Instagram, YouTube, Facebook)

    private val byPackage: Map<String, TrackedPlatform> = all.flatMap { p -> p.packages.map { it to p } }.toMap()
    private val byId: Map<String, TrackedPlatform> = all.associateBy { it.platform.id }

    fun forPackage(packageName: String): TrackedPlatform? = byPackage[packageName]

    fun forId(platformId: String): TrackedPlatform? = byId[platformId]
}
