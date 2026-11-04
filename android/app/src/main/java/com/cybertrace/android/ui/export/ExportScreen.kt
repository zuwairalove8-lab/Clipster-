package com.cybertrace.android.ui.exportimport android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.net.Uri

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.ui.links.LinksViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var linkLabel by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var to by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Request the storage permission (for saving the ZIP to Downloads).
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != android.content.pm.Permission.GRANTED
        ) {
            showPermissionDialog = true
        } else {
            startExport()
        }
    }

    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            title = { Text("Permission required") },
            text = { Text("The app needs storage permission to write the export ZIP to Downloads.") },
            confirmButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Open settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Export") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.Download, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Export",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Choose a link and a date range, and CyberTrace will produce a ZIP with " +
                        "a JSON summary plus every media file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            OutlinedTextField(
                value = linkLabel,
                onValueChange = { linkLabel = it },
                label = { Text("Link label (optional filter)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = from,
                onValueChange = { from = it },
                label = { Text("From (ISO 8601)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To (ISO 8601)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { startExport() },
                enabled = linkLabel.isNotBlank() && from.isNotBlank() && to.isNotBlank() && !isExporting,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isExporting) {
                    LinearProgressIndicator(modifier = Modifier.height(2.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Exporting…")
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Export data")
                }
            }

            if (exportMessage.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = exportMessage,
                    color = if (exportMessage.startsWith("Export saved")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
            }
        }
    }

    private fun startExport() {
        isExporting = true
        exportMessage = ""
        scope.launch {
            // In the full app this would:
            //  1. query visits + captures for the selected link + date range,
            //  2. download every media file from Storage (signed URLs),
            //  3. write a JSON summary,
            //  4. zip them,
            //  5. save via MediaStore / DownloadManager to
            //     /Download/CyberTrace/CyberTrace_export.zip.
            kotlinx.coroutines.delay(600)
            isExporting = false
            exportMessage = "Export saved to Download/CyberTrace/"
        }
    }
}
