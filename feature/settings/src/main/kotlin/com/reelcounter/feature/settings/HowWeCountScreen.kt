package com.reelcounter.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.reelcounter.core.designsystem.component.RcCard
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcIconTile
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcTopAppBar
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.ReelCounterTheme
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.model.MetricQuality
import com.reelcounter.core.ui.metric.icon
import com.reelcounter.core.ui.metric.labelRes
import com.reelcounter.core.ui.metric.roleFor

/** Plain-language explanation of the four metric categories, reachable from Settings and, later, from every badge. */
@Composable
internal fun HowWeCountScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            RcTopAppBar(
                title = stringResource(R.string.feature_settings_how_we_count),
                navigationIcon = RcIcons.Back,
                navigationIconContentDescription = stringResource(R.string.feature_settings_back),
                onNavigationClick = onBackClick,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(start = Spacing.screen, end = Spacing.screen, top = Spacing.sm, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(
                text = stringResource(R.string.feature_settings_how_we_count_intro),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val metricColors = ReelCounterTheme.metricColors
            RcListCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                MetricQuality.entries.forEachIndexed { index, quality ->
                    if (index > 0) RcDivider(startInset = 68.dp)
                    val role = metricColors.roleFor(quality)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics(mergeDescendants = true) {}
                            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    ) {
                        RcIconTile(icon = quality.icon, containerColor = role.container, contentColor = role.accent)
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            Text(
                                text = stringResource(quality.labelRes),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = stringResource(quality.descriptionRes),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            RcCard(modifier = Modifier.fillMaxWidth().padding(top = Spacing.sm)) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    RcIconTile(
                        icon = RcIcons.AppTime,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                        Text(
                            text = stringResource(R.string.feature_settings_app_time_title),
                            modifier = Modifier.semantics { heading() },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.feature_settings_app_time_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@get:StringRes
private val MetricQuality.descriptionRes: Int
    get() = when (this) {
        MetricQuality.VERIFIED -> R.string.feature_settings_quality_verified
        MetricQuality.COUNTED -> R.string.feature_settings_quality_counted
        MetricQuality.ESTIMATED -> R.string.feature_settings_quality_estimated
        MetricQuality.DURATION_ONLY -> R.string.feature_settings_quality_duration_only
        MetricQuality.UNAVAILABLE -> R.string.feature_settings_quality_unavailable
    }
