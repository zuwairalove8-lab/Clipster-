package com.cybertrace.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybertrace.android.CyberTraceApplication

@Composable
fun AuthScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: AuthViewModel = viewModel {
        AuthViewModel(CyberTraceApplication())
    }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.session) {
        state.session?.let { session ->
            onSignedIn()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "CyberTrace",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Investigator dashboard",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = state.email,
            onValueChange = viewModel::onEmailChange,
            label = { Text("Email") },
            keyboardType = KeyboardType.Email,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.password,
            onValueChange = viewModel::onPasswordChange,
            label = { Text("Password") },
            visualTransformation = androidx.compose.ui.text.PrivateVisualTransformation,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (state.emailSent) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Check your inbox for the signup confirmation link, then sign in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        val isEnabled = state.email.isNotBlank() && state.password.isNotBlank() && state.password == state.confirmPassword
        Button(
            onClick = { if (state.isSigningUp) viewModel.signup() else viewModel.login() },
            enabled = isEnabled && !state.isLoading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isLoading) {
                androidx.compose.animation.AnimatedProgressIndicator(modifier = Modifier.size(18.dp))
            } else {
                Text(if (state.isSigningUp) "Create account" else "Sign in")
            }
        }

        if (state.isSigningUp) {
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = { viewModel._state.value = viewModel._state.value.copy(isSigningUp = false) }) {
                Text("Back to sign in")
            }
        }

        if (state.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = state.error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(onClick = { viewModel._state.value = viewModel._state.value.copy(error = null, emailSent = false, password = "", confirmPassword = "") }) {
            Text("Forgot password?")
        }
    }
}
