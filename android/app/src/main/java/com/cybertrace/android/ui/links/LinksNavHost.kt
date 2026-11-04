package com.cybertrace.android.ui.links

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable

enum class LinksScreen { LIST, CREATE, DETAIL }

@Composable
fun LinksNavHost(
    navController: NavHostController,
    onLinkSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    NavHost(navController = navController, startDestination = LinksScreen.LIST.name, modifier = modifier) {
        composable(LinksScreen.LIST.name) {
            LinksScreen(
                onLinkClick = { id ->
                    onLinkSelected(id)
                    navController.navigate(LinksScreen.DETAIL.name)
                }
            )
        }
        composable(LinksScreen.CREATE.name) {
            LinkCreateScreen(onCreated = { navController.popBackStack() })
        }
        composable(LinksScreen.DETAIL.name) {
            LiveFeedDetailScreen({ navController.popBackStack() })
        }
    }
}
