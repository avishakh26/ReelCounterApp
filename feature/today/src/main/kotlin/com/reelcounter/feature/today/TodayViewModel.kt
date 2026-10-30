package com.reelcounter.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelcounter.core.domain.profile.ProfileRepository
import com.reelcounter.core.domain.profile.ProfileState
import com.reelcounter.core.domain.tracking.CurrentDateProvider
import com.reelcounter.core.domain.tracking.ReelCountingStatus
import com.reelcounter.core.domain.tracking.TrackingRepository
import com.reelcounter.core.domain.tracking.TrackingStatus
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.model.UserProfile
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.summarize
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject
import kotlin.time.Duration

sealed interface TodayUiState {
    data object Loading : TodayUiState

    /** Tracking has never been set up (or the disclosure needs accepting). */
    data class NeedsSetup(
        val greetingName: String? = null,
        /** This build can count reels one by one (it includes the accessibility service). */
        val countingAvailable: Boolean = true,
    ) : TodayUiState

    data class Content(
        val date: LocalDate,
        val activities: List<DailyPlatformActivity>,
        /** Sum of measured app time across tracked apps; null when nothing was measured. */
        val totalAppTime: Duration?,
        val lastUpdatedMs: Long?,
        val status: TrackingStatus,
        val isRefreshing: Boolean,
        /** Today across platforms. */
        val today: DaySummary = activities.summarize(date),
        /** Yesterday across platforms, for the day-over-day comparison; null while loading. */
        val yesterday: DaySummary? = null,
        /** The seven days ending today, oldest first. Days without data have null figures. */
        val week: List<DaySummary> = emptyList(),
        val profile: UserProfile? = null,
        /** Reels are counted one by one (accessibility service on) rather than estimated. */
        val countingEnabled: Boolean = false,
    ) : TodayUiState
}

/** The days a week chart shows: [days] dates ending with [today], oldest first. */
internal fun daysEnding(today: LocalDate, days: Int = 7): List<LocalDate> =
    (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val trackingRepository: TrackingRepository,
    currentDateProvider: CurrentDateProvider,
    profileRepository: ProfileRepository,
    countingStatus: ReelCountingStatus,
) : ViewModel() {

    private val countingAvailable = countingStatus.isAvailable

    private val isRefreshing = MutableStateFlow(false)

    // A new date at midnight switches to the new day's (empty) figures without a restart.
    private val todaysActivity = currentDateProvider.today.flatMapLatest { date ->
        trackingRepository.observeDay(date).map { date to it }
    }

    // The week shown under the headline. Each day is observed separately, so a recalculated
    // day (for example after changing estimation settings) updates the chart in place.
    private val week: Flow<List<DaySummary>> = currentDateProvider.today.flatMapLatest { today ->
        combine(daysEnding(today).map { day -> trackingRepository.observeDay(day).map { it.summarize(day) } }) { it.toList() }
    }

    private val profile: Flow<UserProfile?> = profileRepository.profileState
        .map { (it as? ProfileState.Loaded)?.profile }
        .distinctUntilChanged()

    private val baseState = combine(
        trackingRepository.status,
        todaysActivity,
        trackingRepository.lastUpdatedMs,
        isRefreshing,
    ) { status, (date, activities), lastUpdated, refreshing ->
        // Once anything was ever recorded, keep showing it even if access was later revoked.
        if (status == TrackingStatus.NeedsDisclosure || (status == TrackingStatus.NeedsUsageAccess && lastUpdated == null)) {
            TodayUiState.NeedsSetup()
        } else {
            val today = activities.summarize(date)
            TodayUiState.Content(
                date = date,
                activities = activities,
                totalAppTime = today.appTime,
                lastUpdatedMs = lastUpdated,
                status = status,
                isRefreshing = refreshing,
                today = today,
            )
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(baseState, week, profile, countingStatus.isEnabled) { state, week, profile, counting ->
        when (state) {
            is TodayUiState.Content -> state.copy(
                week = week,
                yesterday = week.getOrNull(week.lastIndex - 1),
                profile = profile,
                countingEnabled = counting,
            )
            is TodayUiState.NeedsSetup -> state.copy(greetingName = profile?.greetingName(), countingAvailable = countingAvailable)
            TodayUiState.Loading -> state
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState.Loading)

    /** Reads the usage log now. Called when the screen resumes and on pull-to-refresh. */
    fun refresh() {
        if (isRefreshing.value) return
        isRefreshing.value = true
        viewModelScope.launch {
            try {
                trackingRepository.refresh()
            } finally {
                isRefreshing.value = false
            }
        }
    }
}

/** The name to greet the user with: their display name's first word, else their username. */
internal fun UserProfile.greetingName(): String? =
    displayName?.trim()?.substringBefore(' ')?.takeIf { it.isNotEmpty() } ?: username
