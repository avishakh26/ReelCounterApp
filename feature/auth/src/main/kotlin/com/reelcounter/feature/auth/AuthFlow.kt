package com.reelcounter.feature.auth

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.reelcounter.feature.auth.code.ResetCodeScreen
import com.reelcounter.feature.auth.code.VerifyEmailScreen
import com.reelcounter.feature.auth.reset.ForgotPasswordScreen
import com.reelcounter.feature.auth.signin.SignInScreen
import com.reelcounter.feature.auth.signup.SignUpScreen
import kotlinx.serialization.Serializable

@Serializable internal data object SignInRoute

@Serializable internal data object SignUpRoute

@Serializable internal data class VerifyEmailRoute(val email: String)

@Serializable internal data class ForgotPasswordRoute(val email: String)

@Serializable internal data class ResetCodeRoute(val email: String)

/**
 * Everything a signed-out user can do, as one self-contained flow with its own back stack.
 * The app shows it while signed out and replaces it as soon as a session exists, so no screen
 * here navigates "into" the app.
 */
@Composable
fun AuthFlow(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = SignInRoute, modifier = modifier) {
        composable<SignInRoute> {
            SignInScreen(
                onCreateAccount = { navController.navigate(SignUpRoute) },
                onForgotPassword = { email -> navController.navigate(ForgotPasswordRoute(email)) },
                onVerifyEmail = { email -> navController.navigate(VerifyEmailRoute(email)) },
            )
        }
        composable<SignUpRoute> {
            SignUpScreen(
                onBack = { navController.popBackStack() },
                // Back from verification returns to sign-in, not to a half-filled sign-up form.
                onVerifyEmail = { email ->
                    navController.navigate(VerifyEmailRoute(email)) { popUpTo<SignInRoute>() }
                },
            )
        }
        composable<VerifyEmailRoute> {
            VerifyEmailScreen(onBack = { navController.popBackStack() })
        }
        composable<ForgotPasswordRoute> {
            ForgotPasswordScreen(
                onBack = { navController.popBackStack() },
                onCodeSent = { email -> navController.navigate(ResetCodeRoute(email)) },
            )
        }
        composable<ResetCodeRoute> {
            ResetCodeScreen(onBack = { navController.popBackStack() })
        }
    }
}
