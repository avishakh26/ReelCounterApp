package com.reelcounter.core.common.network

import kotlinx.coroutines.flow.Flow

/** Whether the device currently has a network that claims internet access. */
interface NetworkMonitor {
    val isOnline: Flow<Boolean>
}
