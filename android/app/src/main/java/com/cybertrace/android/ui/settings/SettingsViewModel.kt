package com.cybertrace.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybertrace.android.CyberTraceApplication
import com.supabase.Client
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsState(
    val backendConnected: Boolean = false,
    val isLoading: Boolean = false
)

class SettingsViewModel(application: CyberTraceApplication) : ViewModel() {

    private val client: Client = application.supabase

    private val _state = MutableStateFlow(SettingsState())
    val backendStatus: StateFlow<Boolean> = _state.asStateFlow()

    fun refreshBackendStatus() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            try {
                // A cheap way to prove the project is reachable: ask for a single link.
                val rows = client.postgrest
                    .from("links")
                    .limit(1)
                    .select()
                _state.value = _state.value.copy(
                    backendConnected = true,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    backendConnected = false,
                    isLoading = false
                )
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            client.auth.signOut()
        }
    }
}
