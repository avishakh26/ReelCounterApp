package com.reelcounter.core.ui.state

import androidx.compose.runtime.Immutable

/**
 * What a screen is showing. Screen-level ViewModels expose `StateFlow<UiState<T>>`, and
 * [StateContent] renders every case except [Success] the same way across the app.
 *
 * A screen with cached data shows [Success] even while offline; the app-wide offline notice
 * covers that case. [Offline] means there is no connection AND nothing cached.
 */
@Immutable
sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>

    /** Loaded, with nothing to show. [message] replaces the screen's default empty copy. */
    data class Empty(val message: UiText? = null) : UiState<Nothing>

    data class Success<out T>(val data: T) : UiState<T>

    /** Loading failed. Set [isRetryable] to false when retrying can't help, e.g. a permission error. */
    data class Error(val message: UiText, val isRetryable: Boolean = true) : UiState<Nothing>

    data object Offline : UiState<Nothing>
}
