package com.cybertrace.android.ui.livefeed

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

enum class LiveFeedScreen { LIST, DETAIL, MAP }

@Composable
fun LiveFeedNavHost(
    navController: NavHostController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = LiveFeedScreen.LIST.name, modifier = modifier) {
        composable(LiveFeedScreen.LIST.name) {
            LiveFeedScreen(
                onBack = onBack
            )
        }
        composable(LiveFeedScreen.DETAIL.name) {
            LiveFeedDetailScreen({ navController.popBackStack() })
        }
        composable(LiveFeedScreen.MAP.name) {
            MapViewScreen({ navController.popBackStack() })
        }
    }
}
