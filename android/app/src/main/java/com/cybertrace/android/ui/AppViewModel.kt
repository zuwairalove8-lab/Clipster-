package com.cybertrace.android.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybertrace.android.data.CaptureDto
import com.cybertrace.android.data.Config
import com.cybertrace.android.data.LinkConfig
import com.cybertrace.android.data.LinkDto
import com.cybertrace.android.data.Repo
import com.cybertrace.android.data.VisitDto
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class AppState(
    val signedIn: Boolean = false,
    val userEmail: String? = null,
    val authBusy: Boolean = false,
    val authError: String? = null,
    val authNotice: String? = null,
    val links: List<LinkDto> = emptyList(),
    val visits: List<VisitDto> = emptyList(),
    val captures: List<CaptureDto> = emptyList(),
    val loading: Boolean = false,
    val loadedOnce: Boolean = false,
    val dataError: String? = null,
    val backendOk: Boolean? = null,
    val live: Boolean = false,
    val lastEventAt: Long = 0L,
    val creating: Boolean = false,
    val createError: String? = null,
) {
    fun linkById(id: String?): LinkDto? = links.firstOrNull { it.id == id }

    fun visitsFor(linkId: String?): List<VisitDto> =
        if (linkId == null) visits else visits.filter { it.link_id == linkId }

    fun capturesFor(linkId: String?): List<CaptureDto> =
        if (linkId == null) captures else captures.filter { it.link_id == linkId }
}

class AppViewModel(private val repo: Repo) : ViewModel() {

    private val _state = MutableStateFlow(
        AppState(
            signedIn = repo.isSignedIn(),
            userEmail = repo.email(),
        )
    )
    val state: StateFlow<AppState> = _state.asStateFlow()

    private var visitChannel: RealtimeChannel? = null
    private var captureChannel: RealtimeChannel? = null
    private var refreshJob: Job? = null
    private var started = false

    init {
        if (repo.isSignedIn()) {
            refresh()
            startRealtime()
        }
    }

    /* ------------------------------------------------------------------ auth */

    fun signIn(email: String, password: String) {
        if (_state.value.authBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            runCatching { repo.signIn(email.trim(), password) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        authBusy = false,
                        signedIn = true,
                        userEmail = repo.email(),
                    )
                    refresh()
                    startRealtime()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        authBusy = false,
                        authError = e.message ?: "Sign in failed",
                    )
                }
        }
    }

    fun signUp(email: String, password: String) {
        if (_state.value.authBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(authBusy = true, authError = null, authNotice = null)
            runCatching { repo.signUp(email.trim(), password) }
                .onSuccess { hasSession ->
                    if (hasSession) {
                        _state.value = _state.value.copy(
                            authBusy = false,
                            signedIn = true,
                            userEmail = repo.email(),
                        )
                        refresh()
                        startRealtime()
                    } else {
                        _state.value = _state.value.copy(
                            authBusy = false,
                            authNotice = "Account created. Confirm the email we sent, then sign in.",
                        )
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        authBusy = false,
                        authError = e.message ?: "Sign up failed",
                    )
                }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            stopRealtime()
            repo.signOut()
            _state.value = AppState(signedIn = false)
        }
    }

    fun clearAuthMessages() {
        _state.value = _state.value.copy(authError = null, authNotice = null)
    }

    /* ------------------------------------------------------------------ data */

    fun refresh() {
        if (_state.value.loading) return
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, dataError = null)
            runCatching {
                val links = repo.links()
                val visits = repo.visits()
                val captures = repo.captures()
                Triple(links, visits, captures)
            }
                .onSuccess { (links, visits, captures) ->
                    _state.value = _state.value.copy(
                        links = links,
                        visits = visits,
                        captures = captures,
                        loading = false,
                        loadedOnce = true,
                        backendOk = true,
                        dataError = null,
                    )
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        loading = false,
                        loadedOnce = true,
                        backendOk = false,
                        dataError = e.message ?: "Could not load data",
                    )
                }
        }
    }

    fun createLink(label: String, config: LinkConfig, onCreated: (String) -> Unit) {
        if (_state.value.creating) return
        viewModelScope.launch {
            _state.value = _state.value.copy(creating = true, createError = null)
            runCatching { repo.createLink(label, config) }
                .onSuccess { token ->
                    _state.value = _state.value.copy(creating = false)
                    onCreated(Config.linkUrl(token))
                    refresh()
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        creating = false,
                        createError = e.message ?: "Could not create the link",
                    )
                }
        }
    }

    /* -------------------------------------------------------------- realtime */

    fun startRealtime() {
        if (started || !repo.isSignedIn()) return
        started = true

        viewModelScope.launch {
            runCatching {
                val v = repo.client.channel("cybertrace-visits")
                val vFlow = v.postgresChangeFlow<PostgresAction>(schema = "public") { table = "visits" }
                vFlow.onEach { scheduleRefresh() }.launchIn(viewModelScope)
                v.subscribe()
                visitChannel = v
            }.onFailure { setLive(false) }

            runCatching {
                val c = repo.client.channel("cybertrace-captures")
                val cFlow = c.postgresChangeFlow<PostgresAction>(schema = "public") { table = "captures" }
                cFlow.onEach { scheduleRefresh() }.launchIn(viewModelScope)
                c.subscribe()
                captureChannel = c
            }.onFailure { setLive(false) }

            setLive(visitChannel != null || captureChannel != null)
        }
    }

    private fun setLive(value: Boolean) {
        _state.value = _state.value.copy(live = value)
    }

    private fun scheduleRefresh() {
        _state.value = _state.value.copy(lastEventAt = System.currentTimeMillis())
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            delay(350)
            refreshNow()
        }
    }

    /** Refresh that ignores the "already loading" guard, used by realtime. */
    private suspend fun refreshNow() {
        runCatching {
            val links = repo.links()
            val visits = repo.visits()
            val captures = repo.captures()
            Triple(links, visits, captures)
        }.onSuccess { (links, visits, captures) ->
            _state.value = _state.value.copy(
                links = links,
                visits = visits,
                captures = captures,
                backendOk = true,
                loadedOnce = true,
            )
        }
    }

    private suspend fun stopRealtime() {
        runCatching { visitChannel?.unsubscribe() }
        runCatching { captureChannel?.unsubscribe() }
        visitChannel = null
        captureChannel = null
        started = false
        setLive(false)
    }

    /* --------------------------------------------------------------- storage */

    suspend fun signedUrl(path: String): String? = repo.signedUrl(path)

    fun linkUrl(token: String): String = Config.linkUrl(token)

    override fun onCleared() {
        visitChannel?.let { ch -> viewModelScope.launch { runCatching { ch.unsubscribe() } } }
        captureChannel?.let { ch -> viewModelScope.launch { runCatching { ch.unsubscribe() } } }
        super.onCleared()
    }
}
