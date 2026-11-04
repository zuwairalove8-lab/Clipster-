package com.cybertrace.android.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.ui.settings.SettingsViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel { SettingsViewModel(CyberTraceApplication()) },
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val backend by viewModel.backendStatus.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        // Refresh backend status on startup (once the app is fully initialized).
        runBlocking {
            viewModel.refreshBackendStatus()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        // Compliance notice (prominent, restrained)
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Authorized use only",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "CyberTrace is for authorized penetration testing and anti-fraud work only. " +
                                "It must never be used to collect data from people who have not given " +
                                "informed consent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Backend status
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Backend connection",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (backend) "Connected to Supabase" else "Offline / unverified",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = if (backend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        ListItem(
            headlineContent = "Sign out",
            supportingContent = { Text("End your session") },
            leadingIcon = { Icon(Icons.Default.ExitToApp, contentDescription = null) },
            onClick = {
                viewModel.signOut()
                onSignOut()
            }
        )

        ListItem(
            headlineContent = "Version",
            supportingContent = { Text("v1.0") },
            leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
            onClick = {}
        )
    }
}
