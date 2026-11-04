package com.cybertrace.android.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybertrace.android.CyberTraceApplication
import com.supabase.Client
import com.supabase.auth.Session
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthState(
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isSigningUp: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val session: Session? = null,
    val emailSent: Boolean = false
)

class AuthViewModel(application: CyberTraceApplication) : ViewModel() {

    private val client: Client = application.supabase

    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()

    init {
        // Refresh the session on launch so the app stays signed in.
        viewModelScope.launch {
            val session = client.auth.session
            _state.value = _state.value.copy(session = session)
        }
    }

    fun onEmailChange(email: String) {
        _state.value = _state.value.copy(email = email)
    }

    fun onPasswordChange(password: String) {
        _state.value = _state.value.copy(password = password)
    }

    fun onConfirmPasswordChange(confirmPassword: String) {
        _state.value = _state.value.copy(confirmPassword = confirmPassword)
    }

    fun login() {
        val s = _state.value
        if (s.email.isNotBlank() && s.password.isNotBlank()) {
            viewModelScope.launch {
                _state.value = s.copy(isLoading = true, error = null)
                try {
                    val session = client.auth.signIn(email = s.email, password = s.password)
                    _state.value = s.copy(isLoading = false, session = session, error = null)
                } catch (e: Exception) {
                    _state.value = s.copy(isLoading = false, error = e.message ?: "Sign in failed")
                }
            }
        }
    }

    fun signup() {
        val s = _state.value
        if (s.email.isNotBlank() && s.password.isNotBlank() && s.password == s.confirmPassword) {
            viewModelScope.launch {
                _state.value = s.copy(isSigningUp = true, error = null)
                try {
                    val session = client.auth.signUp(email = s.email, password = s.password)
                    _state.value = s.copy(isSigningUp = false, session = session, error = null, emailSent = true)
                } catch (e: Exception) {
                    _state.value = s.copy(isSigningUp = false, error = e.message ?: "Sign up failed")
                }
            }
        } else {
            _state.value = s.copy(error = "Please check your email and password.")
        }
    }

    fun signout() {
        viewModelScope.launch {
            client.auth.signOut()
            _state.value = _state.value.copy(session = null)
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
