package com.reelcounter.core.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.Platform
import com.reelcounter.core.model.PlatformMetric
import com.reelcounter.core.model.UnavailableReason
import java.time.LocalDate
import kotlin.time.Duration.Companion.minutes

/** Fictional activity covering every metric category. The numbers are illustrative only. */
object SampleActivity {
    private val date: LocalDate = LocalDate.of(2026, 10, 19)

    val tiktok = Platform(id = "tiktok", displayName = "TikTok")
    val instagram = Platform(id = "instagram", displayName = "Instagram")
    val youtube = Platform(id = "youtube", displayName = "YouTube")
    val facebook = Platform(id = "facebook", displayName = "Facebook")

    val verified = DailyPlatformActivity(
        platform = tiktok,
        date = date,
        metric = PlatformMetric.Verified(count = 142, verifiedThrough = date.minusDays(1), appTime = 65.minutes),
    )

    val estimated = DailyPlatformActivity(
        platform = instagram,
        date = date,
        metric = PlatformMetric.Estimated(appTime = 47.minutes, count = 85, low = 60, high = 110),
    )

    val estimatedAdjusted = DailyPlatformActivity(
        platform = tiktok,
        date = date,
        metric = PlatformMetric.Estimated(
            appTime = 32.minutes,
            count = 70,
            low = 55,
            high = 90,
            userAdjusted = true,
        ),
    )

    val durationOnly = DailyPlatformActivity(
        platform = youtube,
        date = date,
        metric = PlatformMetric.DurationOnly(appTime = 74.minutes),
    )

    val unavailable = DailyPlatformActivity(
        platform = facebook,
        date = date,
        metric = PlatformMetric.Unavailable(UnavailableReason.PERMISSION_MISSING),
    )

    /** One day across four platforms, as the Today screen would show it. */
    val today: List<DailyPlatformActivity> = listOf(estimatedAdjusted, estimated, durationOnly, unavailable)

    val allCategories: List<DailyPlatformActivity> =
        listOf(verified, estimated, estimatedAdjusted, durationOnly, unavailable)
}

class DailyPlatformActivityPreviewParameterProvider : PreviewParameterProvider<DailyPlatformActivity> {
    override val values: Sequence<DailyPlatformActivity> = SampleActivity.allCategories.asSequence()
}
