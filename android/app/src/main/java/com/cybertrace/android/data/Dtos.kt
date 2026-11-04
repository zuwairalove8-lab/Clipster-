package com.cybertrace.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LinkConfig(
    val precise_location: Boolean = false,
    val camera_front: Boolean = false,
    val camera_back: Boolean = false,
    val video: Boolean = false,
    val audio: Boolean = false,
) {
    val enabledCount: Int
        get() = listOf(precise_location, camera_front, camera_back, video, audio).count { it }

    /** The flags that are on, in display order, as (key, label) pairs. */
    val enabledItems: List<Pair<String, String>>
        get() = buildList {
            if (precise_location) add("precise_location" to "Precise GPS")
            if (camera_front) add("camera_front" to "Front camera")
            if (camera_back) add("camera_back" to "Back camera")
            if (video) add("video" to "Video")
            if (audio) add("audio" to "Audio")
        }
}

@Serializable
data class LinkDto(
    val id: String,
    val link_token: String,
    val label: String = "",
    val config: LinkConfig = LinkConfig(),
    val created_at: String = "",
    val created_by: String? = null,
)

@Serializable
data class LinkInsert(
    val link_token: String,
    val label: String,
    val config: LinkConfig,
    val created_by: String? = null,
)

@Serializable
data class GeoDto(
    val city: String? = null,
    val region: String? = null,
    val country: String? = null,
    val country_code: String? = null,
    val isp: String? = null,
    val org: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val timezone: String? = null,
    val source: String? = null,
) {
    val place: String
        get() = listOfNotNull(city, region, country).filter { it.isNotBlank() }
            .joinToString(", ").ifBlank { "Unknown" }
}

@Serializable
data class VisitDto(
    val id: String,
    val link_id: String,
    val ip_address: String = "",
    val ip_geo: GeoDto? = null,
    val fingerprint: JsonObject = JsonObject(emptyMap()),
    val user_agent: String = "",
    val consent_shown_at: String = "",
    val first_seen_at: String = "",
    val last_seen_at: String = "",
)

@Serializable
data class CaptureDto(
    val id: String,
    val visit_id: String,
    val link_id: String,
    val capture_type: String = "",
    val storage_path: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    val status: String = "granted",
    val captured_at: String = "",
) {
    val isDenied: Boolean get() = status.equals("denied", ignoreCase = true)
    val isMedia: Boolean get() = storage_path != null && storage_path.isNotBlank()
}

object CaptureTypes {
    val all = listOf("precise_location", "camera_front", "camera_back", "video", "audio")

    fun label(type: String): String = when (type) {
        "precise_location" -> "Precise location"
        "camera_front" -> "Front camera"
        "camera_back" -> "Back camera"
        "video" -> "Video clip"
        "audio" -> "Audio clip"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    fun isImage(type: String, path: String?): Boolean =
        type == "camera_front" || type == "camera_back" ||
            path?.lowercase()?.let { it.endsWith(".jpg") || it.endsWith(".png") || it.endsWith(".webp") } == true

    fun isVideo(type: String, path: String?): Boolean =
        type == "video" || path?.lowercase()?.let { it.endsWith(".webm") || it.endsWith(".mp4") } == true

    fun isAudio(type: String, path: String?): Boolean =
        type == "audio" || path?.lowercase()?.let { it.endsWith(".m4a") || it.endsWith(".mp3") } == true
}
