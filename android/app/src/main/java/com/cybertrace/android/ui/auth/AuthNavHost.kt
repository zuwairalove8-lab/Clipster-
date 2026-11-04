package com.cybertrace.android.ui.auth

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

enum class AuthScreen { LOGIN, SIGNUP }

@Composable
fun AuthNavHost(
    navController: NavHostController,
    onSignedIn: () -> Unit
) {
    NavHost(navController = navController, startDestination = AuthScreen.LOGIN.name) {
        composable(AuthScreen.LOGIN.name) {
            AuthScreen(onSignedIn = { onSignedIn() })
        }
        composable(AuthScreen.SIGNUP.name) {
            AuthScreen(onSignedIn = { onSignedIn() })
        }
    }
}
