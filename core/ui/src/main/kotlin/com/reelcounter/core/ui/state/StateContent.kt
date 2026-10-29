package com.reelcounter.core.ui.state

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.ErrorState
import com.reelcounter.core.designsystem.component.OfflineState
import com.reelcounter.core.designsystem.component.SkeletonList
import com.reelcounter.core.ui.R

/**
 * Renders a [UiState]. Screens supply only [content] for the success case, plus any slot whose
 * default doesn't fit (most screens replace [empty] with copy specific to them).
 *
 * Switching between states cross-fades. Updates within [UiState.Success] don't animate.
 */
@Composable
fun <T> StateContent(
    state: UiState<T>,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
    loading: @Composable () -> Unit = { SkeletonList() },
    empty: @Composable (UiState.Empty) -> Unit = { DefaultEmptyState(it) },
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = state,
        modifier = modifier,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        contentKey = { it::class },
        label = "StateContent",
    ) { target ->
        when (target) {
            UiState.Loading -> loading()
            is UiState.Empty -> empty(target)
            is UiState.Success -> content(target.data)
            is UiState.Error -> ErrorState(
                title = stringResource(R.string.core_ui_state_error_title),
                message = target.message.asString(),
                modifier = Modifier.fillMaxSize(),
                retryLabel = stringResource(R.string.core_ui_state_retry),
                onRetry = onRetry.takeIf { target.isRetryable },
            )
            UiState.Offline -> OfflineState(
                title = stringResource(R.string.core_ui_state_offline_title),
                message = stringResource(R.string.core_ui_state_offline_message),
                modifier = Modifier.fillMaxSize(),
                retryLabel = stringResource(R.string.core_ui_state_retry),
                onRetry = onRetry,
            )
        }
    }
}

@Composable
private fun DefaultEmptyState(state: UiState.Empty) {
    EmptyState(
        title = stringResource(R.string.core_ui_state_empty_title),
        message = state.message?.asString() ?: stringResource(R.string.core_ui_state_empty_message),
        modifier = Modifier.fillMaxSize(),
    )
}
