package com.cybertrace.android.ui.links

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.model.Link
import com.supabase.Client
import com.supabase.postgrest.Row
import com.supabase.postgrest.Postgrest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class LinksState(
    val links: List<Link> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedId: String? = null
)

class LinksViewModel(application: CyberTraceApplication) : ViewModel() {

    private val client: Client = application.supabase
    private val postgrest: Postgrest = client.postgrest

    private val _state = MutableStateFlow(LinksState())
    val state: StateFlow<LinksState> = _state.asStateFlow()

    init {
        loadLinks()
    }

    fun loadLinks() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val rows = postgrest
                    .from("links")
                    .order("created_at", ascending = false)
                    .select()
                val links = rows.mapNotNull { row ->
                    toLink(row)
                }
                _state.value = _state.value.copy(links = links, isLoading = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load links")
            }
        }
    }

    fun selectLink(id: String) {
        _state.value = _state.value.copy(selectedId = id)
    }

    fun deselectLink() {
        _state.value = _state.value.copy(selectedId = null)
    }

    fun createLink(label: String, linkToken: String, configJson: String) {
        viewModelScope.launch {
            try {
                // Upsert the link so the URL is deterministic while letting
                // the label update.
                postgrest
                    .from("links")
                    .upsert(mapOf("link_token" to linkToken, "label" to label, "config" to configJson))
                    .execute()
                loadLinks()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Failed to create link")
            }
        }
    }

    private fun toLink(row: Row): Link? {
        return try {
            val id = UUID.fromString(row["id"].toString())
            val configJson = row["config"]
            val config = if (configJson != null && configJson.toString().isNotEmpty()) {
                Json.decodeFromString<LinkConfig>(configJson.toString())
            } else {
                LinkConfig()
            }
            Link(
                id = id,
                linkToken = row["link_token"].toString(),
                label = row["label"].toString(),
                config = config,
                createdAt = row["created_at"].toString(),
                createdBy = row["created_by"]?.toString()?.takeIf { it.isNotEmpty() } ?.let { UUID.fromString(it) }
            )
        } catch (e: Exception) {
            null
        }
    }
}
