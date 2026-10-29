package com.reelcounter.core.designsystem.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MarkEmailRead
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.ui.graphics.vector.ImageVector

/** Every icon the app uses, in one place, so swapping the icon set later is a single-file change. */
object RcIcons {
    // Navigation: filled when selected, outlined otherwise.
    val Today: ImageVector = Icons.Rounded.Home
    val TodayOutlined: ImageVector = Icons.Outlined.Home
    val Insights: ImageVector = Icons.Rounded.BarChart
    val InsightsOutlined: ImageVector = Icons.Outlined.BarChart
    val Friends: ImageVector = Icons.Rounded.Group
    val FriendsOutlined: ImageVector = Icons.Outlined.Group
    val Settings: ImageVector = Icons.Rounded.Settings
    val SettingsOutlined: ImageVector = Icons.Outlined.Settings

    // Metric categories
    val Verified: ImageVector = Icons.Rounded.Verified
    val Estimated: ImageVector = Icons.Rounded.Calculate
    val DurationOnly: ImageVector = Icons.Rounded.Schedule
    val Unavailable: ImageVector = Icons.Rounded.VisibilityOff
    val UserAdjusted: ImageVector = Icons.Rounded.Edit

    // Metrics and trends
    val Reels: ImageVector = Icons.Rounded.SlowMotionVideo
    val AppTime: ImageVector = Icons.Rounded.Timer
    val TrendUp: ImageVector = Icons.AutoMirrored.Rounded.TrendingUp
    val TrendDown: ImageVector = Icons.AutoMirrored.Rounded.TrendingDown
    val TrendFlat: ImageVector = Icons.AutoMirrored.Rounded.TrendingFlat

    // Tracking
    val Tracking: ImageVector = Icons.Rounded.Sensors
    val Paused: ImageVector = Icons.Rounded.PauseCircle
    val Apps: ImageVector = Icons.Rounded.Apps
    val Adjust: ImageVector = Icons.Rounded.Tune

    // States and actions
    val Empty: ImageVector = Icons.Rounded.Inbox
    val Error: ImageVector = Icons.Rounded.ErrorOutline
    val Offline: ImageVector = Icons.Rounded.CloudOff
    val Synced: ImageVector = Icons.Rounded.CloudDone
    val Retry: ImageVector = Icons.Rounded.Refresh
    val Info: ImageVector = Icons.Rounded.Info
    val Back: ImageVector = Icons.AutoMirrored.Rounded.ArrowBack
    val ChevronForward: ImageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight
    val Success: ImageVector = Icons.Rounded.CheckCircle
    val Check: ImageVector = Icons.Rounded.Check

    // Account
    val ShowPassword: ImageVector = Icons.Rounded.Visibility
    val HidePassword: ImageVector = Icons.Rounded.VisibilityOff
    val ChangePhoto: ImageVector = Icons.Rounded.PhotoCamera
    val SignOut: ImageVector = Icons.AutoMirrored.Rounded.Logout
    val Delete: ImageVector = Icons.Rounded.Delete
    val EmailCode: ImageVector = Icons.Rounded.MarkEmailRead

    // Social and privacy
    val Search: ImageVector = Icons.Rounded.Search
    val AddFriend: ImageVector = Icons.Rounded.PersonAdd
    val More: ImageVector = Icons.Rounded.MoreVert
    val Privacy: ImageVector = Icons.Rounded.Lock
    val Shield: ImageVector = Icons.Rounded.Shield
    val Block: ImageVector = Icons.Rounded.Block
    val Visible: ImageVector = Icons.Rounded.Visibility
}
