package com.reelcounter.core.data.tracking

import com.reelcounter.core.data.social.toActivity
import com.reelcounter.core.domain.auth.AuthRepository
import com.reelcounter.core.domain.auth.AuthState
import com.reelcounter.core.domain.tracking.DayBucketer
import com.reelcounter.core.domain.tracking.TimeSource
import com.reelcounter.core.model.DailyPlatformActivity
import com.reelcounter.core.network.RemoteException
import com.reelcounter.core.network.SupabaseConfig
import com.reelcounter.core.network.social.FriendActivityRemoteDataSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in user's own daily figures as saved to their account: what this phone uploaded
 * before a reinstall, and what their other devices recorded. Today and Insights show them for days
 * this install has no figures of its own (the phone's own record always wins, so nothing is
 * counted twice on screen).
 */
@Singleton
internal class AccountHistory @Inject constructor(
    private val remote: FriendActivityRemoteDataSource,
    private val authRepository: AuthRepository,
    private val config: SupabaseConfig,
    private val time: TimeSource,
) {
    private val _days = MutableStateFlow<Map<LocalDate, List<DailyPlatformActivity>>>(emptyMap())
    val days: StateFlow<Map<LocalDate, List<DailyPlatformActivity>>> = _days.asStateFlow()

    private var loadedFor: String? = null

    /** Fetches the last [DAYS] days. Keeps what it had when offline; forgets it on sign-out. */
    suspend fun refresh() {
        val userId = (authRepository.authState.value as? AuthState.SignedIn)?.userId
        if (userId != loadedFor) _days.value = emptyMap()
        loadedFor = userId
        if (userId == null || !config.isConfigured) return
        val today = DayBucketer.dateOf(time.nowMs(), time.zone())
        try {
            _days.value = remote.dailyTotals(listOf(userId), today.minusDays(DAYS - 1L))
                .map { it.toActivity() }
                .groupBy { it.date }
        } catch (_: RemoteException) {
            // Offline or the server refused: keep the last figures.
        }
    }

    private companion object {
        /** Insights' two weeks. */
        const val DAYS = 14
    }
}
