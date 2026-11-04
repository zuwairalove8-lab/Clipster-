package com.cybertrace.android.model

import java.util.UUID

enum class CaptureStatus { GRANTED, DENIED }

enum class CaptureType {
    PRECISE_LOCATION,
    CAMERA_FRONT,
    CAMERA_BACK,
    VIDEO,
    AUDIO;

    companion object {
        fun from(value: String): CaptureType {
            return when (value.lowercase()) {
                "precise_location" -> PRECISE_LOCATION
                "camera_front" -> CAMERA_FRONT
                "camera_back" -> CAMERA_BACK
                "video" -> VIDEO
                "audio" -> AUDIO
                else -> throw IllegalArgumentException("Unknown capture type: $value")
            }
        }
    }
}

data class Link(
    val id: UUID,
    val linkToken: String,
    val label: String,
    val config: LinkConfig,
    val createdAt: String,
    val createdBy: UUID? = null
) {
    val visits: Int = 0
    val captures: Int = 0
    val fullUrl: String get() = "https://clipster-11f97e3aa67e.herokuapp.com/c/$linkToken"
}
    val fullUrl: String get() = "https://clipster-11f97e3aa67e.herokuapp.com/c/$linkToken"
}

data class LinkConfig(
    val preciseLocation: Boolean = false,
    val cameraFront: Boolean = false,
    val cameraBack: Boolean = false,
    val video: Boolean = false,
    val audio: Boolean = false
)

data class Visit(
    val id: UUID,
    val linkId: UUID,
    val ipAddress: String,
    val ipGeo: Map<String, Any> = emptyMap(),
    val fingerprint: Map<String, Any> = emptyMap(),
    val userAgent: String = "",
    val consentShownAt: String = "",
    val firstSeenAt: String = "",
    val lastSeenAt: String = ""
)

data class Capture(
    val id: UUID,
    val visitId: UUID,
    val linkId: UUID,
    val captureType: CaptureType,
    val storagePath: String? = null,
    val payload: Map<String, Any> = emptyMap(),
    val status: CaptureStatus,
    val capturedAt: String = ""
)

data class CaptureItem(
    val capture: Capture,
    val visit: Visit? = null,
    val isMedia: Boolean = false,
    val mediaUrl: String? = null
)

