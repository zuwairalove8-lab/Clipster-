package com.cybertrace.android.ui.livefeed

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybertrace.android.model.CaptureStatus
import com.cybertrace.android.model.CaptureType
import com.cybertrace.android.model.Visit

@Composable
fun LiveFeedScreen(
    viewModel: LiveFeedViewModel = viewModel { LiveFeedViewModel(CyberTraceApplication()) },
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live feed") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadFeed() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { /* Open filters */ }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = modifier.fillMaxSize().padding(paddingValues)) {
            // Filters
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = state.linkFilter ?: "",
                        onValueChange = {},
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("By link") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    DropdownMenuItemText("By capture type", state.typeFilter)
                    Spacer(modifier = Modifier.width(4.dp))
                    DropdownMenuItemText("By status", state.statusFilter)
                    Spacer(modifier = Modifier.width(4.dp))
                    FilledIconButton(onClick = { viewModel.loadFeed() }, enabled = !state.isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }

            if (state.isLoading && state.captureHistory.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.captureHistory.isEmpty() && state.visits.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No data yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Visits and captures will appear here as they arrive from tracking links.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        if (state.visits.isNotEmpty()) {
                            Text(
                                text = "Visits",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    items(state.visits, key = { it.id.toString() }) { visit ->
                        VisitRow(visit = visit, onClick = { viewModel.selectVisit(visit.id.toString()) })
                    }
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Captures",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(state.captureHistory, key = { "${it.linkId}-${it.id}" }) { capture ->
                        CaptureCard(capture = capture, visitId = capture.visitId.toString())
                    }
                }
            }
        }
    }
}

@Composable
fun VisitRow(
    visit: Visit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Visit",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "IP ${visit.ipAddress.take(15)}…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "First seen ${visit.firstSeenAt.take(16)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CaptureCard(
    capture: com.cybertrace.android.model.Capture,
    visitId: String
) {
    val typeLabel = when (capture.captureType) {
        CaptureType.PRECISE_LOCATION -> "Precise GPS"
        CaptureType.CAMERA_FRONT -> "Front camera"
        CaptureType.CAMERA_BACK -> "Back camera"
        CaptureType.VIDEO -> "Video"
        CaptureType.AUDIO -> "Audio"
    }

    val statusLabel = when (capture.status) {
        CaptureStatus.GRANTED -> "Granted"
        CaptureStatus.DENIED -> "Denied"
    }

    val isDenied = capture.status == CaptureStatus.DENIED

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isDenied) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isDenied) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = capture.capturedAt.take(16),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
fun DropdownMenuItemText(label: String, value: Any?) {
    Text(
        text = value?.toString() ?: "All",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
