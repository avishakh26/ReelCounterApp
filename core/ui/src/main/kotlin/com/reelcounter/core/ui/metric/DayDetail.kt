package com.reelcounter.core.ui.metric

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.ui.R
import java.time.format.DateTimeFormatter

/**
 * The figures of one day picked in a week chart: its date, then reels (≈ for estimates), the likely
 * range and app time, or "No data". Shown under the chart so earlier days can be read exactly.
 */
@Composable
fun DayDetail(day: DaySummary, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val date = remember(day.date, locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale).format(day.date) }
    val parts = buildList {
        day.count?.let { count ->
            val number = formatCount(count)
            val unit = pluralStringResource(R.plurals.core_ui_metric_unit_reels, count)
            add(if (day.isEstimate) stringResource(R.string.core_ui_metric_approx, "$number $unit") else "$number $unit")
            val low = day.countLow
            val high = day.countHigh
            if (day.isEstimate && low != null && high != null && low != high) {
                add(stringResource(R.string.core_ui_metric_estimate_range, formatCount(low), formatCount(high)))
            }
        }
        day.appTime?.let { add(stringResource(R.string.core_ui_metric_app_time, formatAppTime(it))) }
    }
    Column(modifier = modifier.semantics(mergeDescendants = true) {}, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Text(
            text = date,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = if (parts.isEmpty()) stringResource(R.string.core_ui_day_no_data) else parts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
