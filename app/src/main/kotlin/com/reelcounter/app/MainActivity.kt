package com.reelcounter.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelcounter.app.ui.ReelCounterRoot
import com.reelcounter.core.common.network.NetworkMonitor
import com.reelcounter.core.designsystem.theme.ReelCounterTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var networkMonitor: NetworkMonitor

    private val viewModel: MainActivityViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Keep the splash up until the saved session (if any) has been restored, so the user never
        // sees the sign-in screen flash before the app.
        installSplashScreen().setKeepOnScreenCondition { viewModel.appState.value == AppState.Loading }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            val appState by viewModel.appState.collectAsStateWithLifecycle()
            ReelCounterTheme {
                ReelCounterRoot(
                    appState = appState,
                    networkMonitor = networkMonitor,
                    onRetryProfile = viewModel::retryProfile,
                    onSignOut = viewModel::signOut,
                )
            }
        }
    }
}
