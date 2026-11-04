package com.cybertrace.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybertrace.android.data.Repo

sealed interface Screen {
    data object Links : Screen
    data object Create : Screen
    data class Feed(val linkId: String?) : Screen
    data class Detail(val visitId: String, val captureId: String?) : Screen
    data object Export : Screen
    data object Settings : Screen
}

@Composable
fun AppRoot(repo: Repo) {
    val vm: AppViewModel = viewModel { AppViewModel(repo) }
    val state by vm.state.collectAsState()

    if (!state.signedIn) {
        AuthScreen(
            state = state,
            onSignIn = vm::signIn,
            onSignUp = vm::signUp,
            onClearMessages = vm::clearAuthMessages,
        )
        return
    }

    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Links)) }
    var newLinkUrl by remember { mutableStateOf<String?>(null) }

    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    when (val current = stack.last()) {
        Screen.Links -> LinksScreen(
            state = state,
            onOpenFeed = { linkId -> push(Screen.Feed(linkId)) },
            onCreate = { push(Screen.Create) },
            onRefresh = vm::refresh,
            onSettings = { push(Screen.Settings) },
            onExport = { push(Screen.Export) },
        )

        Screen.Create -> CreateLinkScreen(
            state = state,
            onCreate = { label, config ->
                vm.createLink(label, config) { url -> newLinkUrl = url }
            },
            onBack = { pop() },
            newLinkUrl = newLinkUrl,
            onDismissNewLink = {
                newLinkUrl = null
                pop()
            },
        )

        is Screen.Feed -> FeedScreen(
            state = state,
            linkId = current.linkId,
            signedUrl = vm::signedUrl,
            onBack = { pop() },
            onOpen = { visitId, captureId -> push(Screen.Detail(visitId, captureId)) },
        )

        is Screen.Detail -> DetailScreen(
            state = state,
            visitId = current.visitId,
            captureId = current.captureId,
            signedUrl = vm::signedUrl,
            onBack = { pop() },
        )

        Screen.Export -> ExportScreen(
            state = state,
            signedUrl = vm::signedUrl,
            onBack = { pop() },
        )

        Screen.Settings -> SettingsScreen(
            state = state,
            onBack = { pop() },
            onSignOut = {
                stack = listOf(Screen.Links)
                vm.signOut()
            },
        )
    }
}
