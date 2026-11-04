package com.cybertrace.android.ui.livefeed

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cybertrace.android.CyberTraceApplication
import com.cybertrace.android.model.Capture
import com.cybertrace.android.model.CaptureStatus
import com.cybertrace.android.model.CaptureType
import com.cybertrace.android.model.Visit
import com.supabase.Client
import com.supabase.postgrest.Row
import com.supabase.realtime.Realtime
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

data class LiveFeedState(
    val visits: List<Visit> = emptyList(),
    val captures: Map<String, Capture> = emptyMap(), // visitId -> Capture
    val captureHistory: List<Capture> = emptyList(), // newest first
    val isLoading: Boolean = false,
    val error: String? = null,
    val linkFilter: String? = null,
    val typeFilter: CaptureType? = null,
    val statusFilter: CaptureStatus? = null,
    val from: String? = null,
    val to: String? = null,
    val selectedVisitId: String? = null
)

class LiveFeedViewModel(application: CyberTraceApplication) : ViewModel() {

    private val client: Client = application.supabase
    private val realtime: Realtime = client.realtime

    private val _state = MutableStateFlow(LiveFeedState())
    val state: StateFlow<LiveFeedState> = _state.asStateFlow()

    private var unsubscribe: () -> Unit = {}

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val rows = client.postgrest
                    .from("visits")
                    .order("first_seen_at", ascending = false)
                    .select()
                val visits = rows.mapNotNull { row -> toVisit(row) }
                _state.value = _state.value.copy(
                    visits = visits,
                    captures = emptyMap(),
                    captureHistory = emptyList(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message ?: "Failed to load feed")
            }
        }
    }

    fun startRealtime() {
        stopRealtime()
        subscribeToVisits { visits ->
            _state.value = _state.value.copy(visits = visits)
        }
        subscribeToCaptures { captures ->
            val history = captures.sortedByDescending { it.capturedAt }
            _state.value = _state.value.copy(
                captureHistory = history,
                captures = captures.associateBy { it.visitId.toString() }
            )
        }
    }

    fun stopRealtime() {
        if (unsubscribe.isNotEmpty()) {
            unsubscribe()
            unsubscribe = {}
        }
    }

    fun setLinkFilter(id: String?) {
        _state.value = _state.value.copy(linkFilter = id)
    }

    fun setTypeFilter(type: CaptureType?) {
        _state.value = _state.value.copy(typeFilter = type)
    }

    fun setStatusFilter(status: CaptureStatus?) {
        _state.value = _state.value.copy(statusFilter = status)
    }

    fun setDateRange(from: String?, to: String?) {
        _state.value = _state.value.copy(from = from, to = to)
    }

    fun selectVisit(id: String?) {
        _state.value = _state.value.copy(selectedVisitId = id)
    }

    private fun subscribeToVisits(onChange: (List<Visit>) -> Unit) {
        unsubscribe = {
            realtime.realtimeSubscription("visits", onChange)
        }
    }

    private fun subscribeToCaptures(onChange: (List<Capture>) -> Unit) {
        unsubscribe = {
            realtime.realtimeSubscription("captures", onChange)
        }
    }

    private fun toVisit(row: Row): Visit? {
        return try {
            Visit(
                id = UUID.fromString(row["id"].toString()),
                linkId = UUID.fromString(row["link_id"].toString()),
                ipAddress = row["ip_address"].toString(),
                ipGeo = parseJsonMap(row["ip_geo"].toString()),
                fingerprint = parseJsonMap(row["fingerprint"].toString()),
                userAgent = row["user_agent"].toString(),
                consentShownAt = row["consent_shown_at"].toString(),
                firstSeenAt = row["first_seen_at"].toString(),
                lastSeenAt = row["last_seen_at"].toString()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun parseJsonMap(json: String): Map<String, Any> {
        return try {
            Json.decodeFromString<Map<String, String>>(json).mapValues { (_, v) -> v }
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
