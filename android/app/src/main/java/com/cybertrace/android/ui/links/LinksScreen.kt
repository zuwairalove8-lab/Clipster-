package com.cybertrace.android.ui.links

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cybertrace.android.model.Link
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LinksScreen(
    onLinkClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: LinksViewModel = viewModel { LinksViewModel(CyberTraceApplication()) }
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.links) {
        if (state.links.isNotEmpty()) viewModel.selectLink(state.links.first().id.toString())
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Filters bar
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
                    value = state.selectedId ?: "",
                    onValueChange = {},
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Filter by label or token…") },
                    singleLine = true
                )
                FilledIconButton(
                    onClick = { viewModel.loadLinks() },
                    enabled = !state.isLoading
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (state.links.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No links yet",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create your first tracking link to start collecting data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(state.links, key = { it.id.toString() }) { link ->
                    LinksCard(
                        link = link,
                        onClick = { viewModel.selectLink(link.id.toString()) },
                        isSelected = link.id.toString() == state.selectedId
                    )
                    if (link != state.links.last()) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun LinksCard(
    link: Link,
    onClick: () -> Unit,
    isSelected: Boolean
) {
    val fmt = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.US)
    val dateText = try { fmt.format(Date(link.createdAt.filter { it != '.' }.toLong())) } catch (_: Exception) { "" }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = link.label.ifBlank { "Untitled link" },
                style = MaterialTheme.typography.titleMedium,
                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = dateText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Enabled items as chips
            val enabled = listOf(
                link.config.preciseLocation to "Precise GPS",
                link.config.cameraFront to "Front camera",
                link.config.cameraBack to "Back camera",
                link.config.video to "Video",
                link.config.audio to "Audio"
            )
            val visible = enabled.filter { it.first }
            if (visible.isNotEmpty()) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    visible.forEach { (enabled, label) ->
                        Chip(
                            selected = true,
                            onClick = {}, // read-only
                            colors = ChipDefaults.chipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text(label)
                        }
                    }
                }
            } else {
                Text(
                    text = "No captures enabled — only standard connection data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = link.visits.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = link.captures.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSelected) {
                    FilledIconButton(
                        onClick = onClick,
                        colors = FilledIconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                        )
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            }
        }
    }
}
