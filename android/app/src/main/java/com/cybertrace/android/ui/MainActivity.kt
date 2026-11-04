package com.cybertrace.android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.ui.auth.AuthNavHost
import com.cybertrace.android.ui.links.LinkCreateScreen
import com.cybertrace.android.ui.links.LinksNavHost
import com.cybertrace.android.ui.livefeed.LiveFeedNavHost
import com.cybertrace.android.ui.settings.SettingsScreen
import com.cybertrace.android.ui.theme.CyberTraceTheme
import com.supabase.Client
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Pre-warm the Supabase client and verify the backend is reachable.
        val application = application as CyberTraceApplication
        lifecycleScope.launch {
            try {
                // A light read that proves the back end is up.
                val rows = application.supabase.postgrest
                    .from("links")
                    .limit(1)
                    .select()
            } catch (e: Exception) {
                // keep false; the UI will show the offline banner
            }
        }

        setContent {
            CyberTraceTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    val application = application as CyberTraceApplication

                    // Auth is a small inline NavHost so the browser (the web
                    // landing page) can drive the login screen.
                    val authNavController = rememberNavController()
                    val authState by remember {
                        mutableStateOf(
                            runBlocking {
                                application.supabase.auth.session
                            }
                        )
                    }

                    NavHost(navController = navController, startDestination = "auth") {
                        composable("auth") {
                            AuthNavHost(
                                navController = authNavController,
                                onSignedIn = {
                                    navController.navigate("links") {
                                        popUpTo("auth") { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable("links") {
                            LinksNavHost(
                                navController = navController,
                                onLinkSelected = { linkId ->
                                    navController.navigate("feed/$linkId")
                                }
                            )
                        }

                        composable("feed/{linkId}") { backStackEntry ->
                            val linkId = backStackEntry.arguments?.getString("linkId")
                            LiveFeedNavHost(
                                navController = navController,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        composable("create") {
                            LinkCreateScreen(
                                onCreated = { navController.popBackStack() }
                            )
                        }

                        composable("settings") {
                            SettingsScreen(
                                onSignOut = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
