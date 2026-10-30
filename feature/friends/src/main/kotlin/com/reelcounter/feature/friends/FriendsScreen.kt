package com.reelcounter.feature.friends

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.core.designsystem.component.EmptyState
import com.reelcounter.core.designsystem.component.ErrorState
import com.reelcounter.core.designsystem.component.RcAlertDialog
import com.reelcounter.core.designsystem.component.RcBanner
import com.reelcounter.core.designsystem.component.RcButton
import com.reelcounter.core.designsystem.component.RcButtonSize
import com.reelcounter.core.designsystem.component.RcDivider
import com.reelcounter.core.designsystem.component.RcListCard
import com.reelcounter.core.designsystem.component.RcListRow
import com.reelcounter.core.designsystem.component.RcScreenHeader
import com.reelcounter.core.designsystem.component.RcSectionHeader
import com.reelcounter.core.designsystem.component.RcSegmentedControl
import com.reelcounter.core.designsystem.component.RcTextButton
import com.reelcounter.core.designsystem.component.RcTextField
import com.reelcounter.core.designsystem.component.RcTonalButton
import com.reelcounter.core.designsystem.component.RcTone
import com.reelcounter.core.designsystem.component.SkeletonCard
import com.reelcounter.core.designsystem.icon.RcIcons
import com.reelcounter.core.designsystem.theme.Spacing
import com.reelcounter.core.domain.social.ConnectionsState
import com.reelcounter.core.domain.social.FriendRequestResult
import com.reelcounter.core.domain.social.SocialError
import com.reelcounter.core.model.Relationship
import com.reelcounter.core.model.UserSummary
import com.reelcounter.core.model.Visibility
import com.reelcounter.core.ui.metric.DaySummary
import com.reelcounter.core.ui.metric.formatAppTime
import com.reelcounter.core.ui.metric.formatCount
import com.reelcounter.core.ui.profile.Avatar
import com.reelcounter.core.ui.R as CoreUiR

@Composable
internal fun FriendsScreen(
    onOpenPrivacy: () -> Unit,
    onOpenFriend: (userId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FriendsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Friends' phones may have synced while this screen was in the background.
    LifecycleResumeEffect(Unit) {
        viewModel.refreshActivity()
        onPauseOrDispose { }
    }
    FriendsScreen(
        uiState = uiState,
        actions = FriendsActions(
            onQueryChange = viewModel::onQueryChange,
            onRetry = viewModel::refresh,
            onAdd = viewModel::sendRequest,
            onAccept = viewModel::accept,
            onDecline = viewModel::decline,
            onCancel = viewModel::cancel,
            onRemove = viewModel::remove,
            onBlock = viewModel::block,
            onOpenPrivacy = onOpenPrivacy,
            onOpenFriend = onOpenFriend,
            onMessageShown = viewModel::onMessageShown,
        ),
        modifier = modifier,
    )
}

internal class FriendsActions(
    val onQueryChange: (String) -> Unit = {},
    val onRetry: () -> Unit = {},
    val onAdd: (String) -> Unit = {},
    val onAccept: (String) -> Unit = {},
    val onDecline: (String) -> Unit = {},
    val onCancel: (String) -> Unit = {},
    val onRemove: (String) -> Unit = {},
    val onBlock: (String) -> Unit = {},
    val onOpenPrivacy: () -> Unit = {},
    val onOpenFriend: (String) -> Unit = {},
    val onMessageShown: () -> Unit = {},
)

/** The three parts of the Friends tab. */
internal enum class FriendsSection { Friends, Requests, Suggestions }

@Composable
internal fun FriendsScreen(uiState: FriendsUiState, actions: FriendsActions, modifier: Modifier = Modifier) {
    val snackbar = remember { SnackbarHostState() }
    val messageText = uiState.message?.let { messageText(it) }
    LaunchedEffect(uiState.message) {
        if (messageText != null) {
            snackbar.showSnackbar(messageText)
            actions.onMessageShown()
        }
    }
    var confirm by remember { mutableStateOf<PendingConfirmation?>(null) }
    var sectionIndex by rememberSaveable { mutableIntStateOf(0) }
    val section = FriendsSection.entries[sectionIndex]
    val requestCount = (uiState.connections as? ConnectionsState.Loaded)?.incoming?.size ?: 0

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screen, end = Spacing.screen, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item(key = "header") {
                RcScreenHeader(title = stringResource(R.string.feature_friends_title), modifier = Modifier.widthIn(max = ContentMaxWidth))
            }
            item(key = "search") {
                RcTextField(
                    value = uiState.search.query,
                    onValueChange = actions.onQueryChange,
                    label = stringResource(R.string.feature_friends_search_label),
                    modifier = Modifier.widthIn(max = ContentMaxWidth),
                    leadingIcon = { Icon(imageVector = RcIcons.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Search,
                    ),
                )
            }

            if (uiState.search.isActive) {
                // Matches appear as you type (a short pause after each keystroke).
                searchResults(uiState, actions)
            } else {
                item(key = "sections") {
                    RcSegmentedControl(
                        options = listOf(
                            stringResource(R.string.feature_friends_tab_friends),
                            if (requestCount > 0) {
                                stringResource(R.string.feature_friends_tab_requests_count, requestCount)
                            } else {
                                stringResource(R.string.feature_friends_tab_requests)
                            },
                            stringResource(R.string.feature_friends_tab_suggestions),
                        ),
                        selectedIndex = sectionIndex,
                        onSelect = { sectionIndex = it },
                        modifier = Modifier.widthIn(max = ContentMaxWidth),
                    )
                }
                when (section) {
                    FriendsSection.Friends -> {
                        item(key = "visibility") { VisibilityCard(uiState.activityVisibility, actions.onOpenPrivacy) }
                        friends(uiState, actions) { confirm = it }
                    }
                    FriendsSection.Requests -> requests(uiState, actions)
                    FriendsSection.Suggestions -> suggestions(uiState, actions)
                }
            }
        }
    }

    confirm?.let { pending ->
        RcAlertDialog(
            title = stringResource(pending.titleRes, pending.user.nameForDisplay),
            text = stringResource(pending.textRes),
            confirmText = stringResource(pending.confirmRes),
            dismissText = stringResource(R.string.feature_friends_cancel),
            icon = if (pending.isBlock) RcIcons.Block else null,
            isDestructive = true,
            onConfirm = {
                confirm = null
                if (pending.isBlock) actions.onBlock(pending.user.id) else actions.onRemove(pending.user.id)
            },
            onDismiss = { confirm = null },
        )
    }
}

private fun LazyListScope.searchResults(uiState: FriendsUiState, actions: FriendsActions) {
    val search = uiState.search
    when {
        search.error != null -> item(key = "search-error") {
            RcBanner(
                message = stringResource(errorText(search.error)),
                icon = RcIcons.Error,
                tone = RcTone.Critical,
                modifier = Modifier.widthIn(max = ContentMaxWidth),
            )
        }
        search.results == null -> item(key = "searching") {
            SkeletonCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
        search.results.isEmpty() -> item(key = "no-results") {
            EmptyState(
                title = stringResource(R.string.feature_friends_no_results, search.query.trim()),
                message = stringResource(R.string.feature_friends_empty_message),
                icon = RcIcons.Search,
            )
        }
        else -> item(key = "results") {
            RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                search.results.forEachIndexed { index, result ->
                    if (index > 0) RcDivider(startInset = 76.dp)
                    val busy = result.user.id in uiState.busyIds
                    val isFriend = result.relationship == Relationship.Friend
                    PersonRow(
                        user = result.user,
                        online = false,
                        onClick = if (isFriend) ({ actions.onOpenFriend(result.user.id) }) else null,
                    ) {
                        when (result.relationship) {
                            Relationship.None -> RcTonalButton(
                                text = stringResource(R.string.feature_friends_add),
                                onClick = { actions.onAdd(result.user.id) },
                                loading = busy,
                                leadingIcon = RcIcons.AddFriend,
                            )
                            Relationship.OutgoingRequest -> RcTextButton(
                                text = stringResource(R.string.feature_friends_cancel_request),
                                onClick = { actions.onCancel(result.user.id) },
                                enabled = !busy,
                            )
                            Relationship.IncomingRequest -> RcButton(
                                text = stringResource(R.string.feature_friends_accept),
                                onClick = { actions.onAccept(result.user.id) },
                                loading = busy,
                                size = RcButtonSize.Small,
                            )
                            Relationship.Friend -> Icon(
                                imageVector = RcIcons.ChevronForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Shared loading and error handling for the sections that come from the friends list. */
private fun LazyListScope.whenConnectionsLoaded(
    uiState: FriendsUiState,
    actions: FriendsActions,
    content: LazyListScope.(ConnectionsState.Loaded) -> Unit,
) {
    when (val connections = uiState.connections) {
        ConnectionsState.Loading -> items(count = 3, key = { "loading-$it" }) {
            SkeletonCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
        is ConnectionsState.Failed -> item(key = "failed") {
            ErrorState(
                title = stringResource(R.string.feature_friends_load_failed),
                message = stringResource(errorText(connections.error)),
                retryLabel = stringResource(R.string.feature_friends_retry),
                onRetry = actions.onRetry,
            )
        }
        is ConnectionsState.Loaded -> content(connections)
    }
}

private fun LazyListScope.friends(
    uiState: FriendsUiState,
    actions: FriendsActions,
    onConfirm: (PendingConfirmation) -> Unit,
) = whenConnectionsLoaded(uiState, actions) { connections ->
    if (connections.friends.isEmpty()) {
        item(key = "friends-empty") {
            EmptyState(
                title = stringResource(R.string.feature_friends_empty_title),
                message = stringResource(R.string.feature_friends_empty_message),
                icon = RcIcons.Friends,
            )
        }
        return@whenConnectionsLoaded
    }
    section("friends", titleRes = R.string.feature_friends_section_friends, users = connections.friends) { user ->
        val openLabel = stringResource(R.string.feature_friends_open_profile, user.nameForDisplay)
        PersonRow(
            user = user,
            online = user.id in uiState.onlineIds,
            onClick = { actions.onOpenFriend(user.id) },
            onClickLabel = openLabel,
        ) {
            TodayFigure(uiState.friendToday[user.id])
            FriendMenu(
                enabled = user.id !in uiState.busyIds,
                onRemove = { onConfirm(PendingConfirmation(user, isBlock = false)) },
                onBlock = { onConfirm(PendingConfirmation(user, isBlock = true)) },
            )
        }
    }
}

private fun LazyListScope.requests(uiState: FriendsUiState, actions: FriendsActions) =
    whenConnectionsLoaded(uiState, actions) { connections ->
        if (connections.incoming.isEmpty() && connections.outgoing.isEmpty()) {
            item(key = "requests-empty") {
                EmptyState(
                    title = stringResource(R.string.feature_friends_requests_empty_title),
                    message = stringResource(R.string.feature_friends_requests_empty_message),
                    icon = RcIcons.AddFriend,
                )
            }
            return@whenConnectionsLoaded
        }
        section("incoming", titleRes = R.string.feature_friends_section_received, users = connections.incoming) { user ->
            val busy = user.id in uiState.busyIds
            PersonRow(user = user, online = false, summary = stringResource(R.string.feature_friends_request_incoming)) {
                RcTextButton(text = stringResource(R.string.feature_friends_decline), onClick = { actions.onDecline(user.id) }, enabled = !busy)
                RcButton(
                    text = stringResource(R.string.feature_friends_accept),
                    onClick = { actions.onAccept(user.id) },
                    loading = busy,
                    size = RcButtonSize.Small,
                )
            }
        }
        section("outgoing", titleRes = R.string.feature_friends_section_sent, users = connections.outgoing) { user ->
            PersonRow(user = user, online = false, summary = stringResource(R.string.feature_friends_request_sent)) {
                RcTextButton(
                    text = stringResource(R.string.feature_friends_cancel),
                    onClick = { actions.onCancel(user.id) },
                    enabled = user.id !in uiState.busyIds,
                )
            }
        }
    }

private fun LazyListScope.suggestions(uiState: FriendsUiState, actions: FriendsActions) {
    when (val state = uiState.suggestions) {
        SuggestionsState.Loading -> items(count = 3, key = { "suggest-loading-$it" }) {
            SkeletonCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth())
        }
        is SuggestionsState.Failed -> item(key = "suggest-failed") {
            ErrorState(
                title = stringResource(R.string.feature_friends_suggestions_failed),
                message = stringResource(errorText(state.error)),
                retryLabel = stringResource(R.string.feature_friends_retry),
                onRetry = actions.onRetry,
            )
        }
        is SuggestionsState.Loaded -> if (state.people.isEmpty()) {
            item(key = "suggest-empty") {
                EmptyState(
                    title = stringResource(R.string.feature_friends_suggestions_empty_title),
                    message = stringResource(R.string.feature_friends_suggestions_empty_message),
                    icon = RcIcons.AddFriend,
                )
            }
        } else {
            item(key = "suggest-list") {
                RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
                    state.people.forEachIndexed { index, suggestion ->
                        if (index > 0) RcDivider(startInset = 76.dp)
                        val user = suggestion.user
                        PersonRow(
                            user = user,
                            online = false,
                            summary = if (suggestion.mutualFriends > 0) {
                                pluralStringResource(R.plurals.feature_friends_mutual, suggestion.mutualFriends, suggestion.mutualFriends)
                            } else {
                                "@${user.username} · " + stringResource(R.string.feature_friends_suggested)
                            },
                        ) {
                            RcTonalButton(
                                text = stringResource(R.string.feature_friends_add),
                                onClick = { actions.onAdd(user.id) },
                                loading = user.id in uiState.busyIds,
                                leadingIcon = RcIcons.AddFriend,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled card of people. Small lists, so the whole section is one lazy item. */
private fun LazyListScope.section(
    key: String,
    titleRes: Int,
    users: List<UserSummary>,
    row: @Composable (UserSummary) -> Unit,
) {
    if (users.isEmpty()) return
    item(key = "header-$key") {
        RcSectionHeader(
            title = stringResource(R.string.feature_friends_section_count, stringResource(titleRes), users.size),
            modifier = Modifier
                .widthIn(max = ContentMaxWidth)
                .padding(top = Spacing.xs),
        )
    }
    item(key = "list-$key") {
        RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
            users.forEachIndexed { index, user ->
                if (index > 0) RcDivider(startInset = 76.dp)
                row(user)
            }
        }
    }
}

/** A friend's figure for today in the list: reels when counted, app time otherwise, or a dash. */
@Composable
private fun TodayFigure(day: DaySummary?) {
    val count = day?.count
    val appTime = day?.appTime
    val (value, unit) = when {
        count != null && day.isEstimate ->
            stringResource(CoreUiR.string.core_ui_metric_approx, formatCount(count)) to stringResource(R.string.feature_friends_today_reels)
        count != null -> formatCount(count) to stringResource(R.string.feature_friends_today_videos)
        appTime != null -> formatAppTime(appTime) to stringResource(R.string.feature_friends_today_time)
        else -> stringResource(CoreUiR.string.core_ui_metric_no_value) to stringResource(R.string.feature_friends_today_none)
    }
    Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 120.dp)) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = if (day == null) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = unit,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            textAlign = TextAlign.End,
        )
    }
}

/** Always visible, so users know what friends can see before anyone is added. */
@Composable
private fun VisibilityCard(visibility: Visibility?, onOpenPrivacy: () -> Unit) {
    RcListCard(modifier = Modifier.widthIn(max = ContentMaxWidth).fillMaxWidth()) {
        RcListRow(
            title = stringResource(R.string.feature_friends_visibility_title),
            summary = stringResource(
                when (visibility) {
                    Visibility.Private -> R.string.feature_friends_visibility_private
                    Visibility.Public -> R.string.feature_friends_visibility_public
                    Visibility.Friends, null -> R.string.feature_friends_visibility_friends
                },
            ),
            icon = RcIcons.Privacy,
            onClick = onOpenPrivacy,
            showChevron = false,
            trailing = {
                Text(
                    text = stringResource(R.string.feature_friends_change),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
    }
}

@Composable
private fun PersonRow(
    user: UserSummary,
    online: Boolean,
    summary: String? = null,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val onlineLabel = stringResource(R.string.feature_friends_online)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = onClickLabel, role = Role.Button, onClick = onClick) else Modifier,
            )
            .padding(start = Spacing.lg, end = Spacing.sm, top = Spacing.md, bottom = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Avatar(image = user.avatarUrl, name = user.nameForDisplay, size = 44.dp)
            if (online) {
                // Color plus a spoken label, never color alone.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                        .padding(2.dp)
                        .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        .semantics { contentDescription = onlineLabel },
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.nameForDisplay,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary ?: if (online) "@${user.username} · $onlineLabel" else "@${user.username}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically, content = trailing)
    }
}

@Composable
private fun FriendMenu(enabled: Boolean, onRemove: () -> Unit, onBlock: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = enabled) {
            Icon(imageVector = RcIcons.More, contentDescription = stringResource(R.string.feature_friends_more))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = MaterialTheme.shapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_friends_remove)) },
                leadingIcon = { Icon(imageVector = RcIcons.Friends, contentDescription = null) },
                onClick = {
                    expanded = false
                    onRemove()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.feature_friends_block), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(imageVector = RcIcons.Block, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    expanded = false
                    onBlock()
                },
            )
        }
    }
}

private data class PendingConfirmation(val user: UserSummary, val isBlock: Boolean) {
    val titleRes get() = if (isBlock) R.string.feature_friends_block_title else R.string.feature_friends_remove_title
    val textRes get() = if (isBlock) R.string.feature_friends_block_text else R.string.feature_friends_remove_text
    val confirmRes get() = if (isBlock) R.string.feature_friends_block else R.string.feature_friends_remove
}

@Composable
private fun messageText(message: FriendsMessage): String = stringResource(
    when (message) {
        is FriendsMessage.RequestResult -> when (message.result) {
            FriendRequestResult.Requested -> R.string.feature_friends_msg_requested
            FriendRequestResult.Accepted -> R.string.feature_friends_msg_now_friends
            FriendRequestResult.AlreadyFriends -> R.string.feature_friends_msg_already_friends
            FriendRequestResult.AlreadyRequested -> R.string.feature_friends_msg_already_requested
            FriendRequestResult.NotAccepting -> R.string.feature_friends_msg_not_accepting
            FriendRequestResult.NotFound -> R.string.feature_friends_msg_not_found
        }
        FriendsMessage.Blocked -> R.string.feature_friends_msg_blocked
        is FriendsMessage.Failed -> errorText(message.error)
    },
)

private fun errorText(error: SocialError): Int = when (error) {
    SocialError.Network -> R.string.feature_friends_error_network
    SocialError.RateLimited -> R.string.feature_friends_error_rate_limited
    SocialError.ServerOutdated -> R.string.feature_friends_error_server_outdated
    SocialError.NotSignedIn, is SocialError.Unknown -> R.string.feature_friends_error_unknown
}

private val ContentMaxWidth = 640.dp
