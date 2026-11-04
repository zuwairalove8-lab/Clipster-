package com.cybertrace.android.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.random.Random
import kotlin.time.Duration.Companion.hours

class Repo(val client: SupabaseClient) {

    /* ------------------------------------------------------------------ auth */

    fun isSignedIn(): Boolean = client.auth.currentSessionOrNull() != null

    fun userId(): String? = client.auth.currentUserOrNull()?.id

    fun email(): String? = client.auth.currentUserOrNull()?.email

    suspend fun signIn(emailAddress: String, pass: String) = withContext(Dispatchers.IO) {
        client.auth.signInWith(Email) {
            email = emailAddress
            password = pass
        }
    }

    /** Returns true when a session was created, false when Supabase wants email confirmation first. */
    suspend fun signUp(emailAddress: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        client.auth.signUpWith(Email) {
            email = emailAddress
            password = pass
        }
        client.auth.currentSessionOrNull() != null
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        runCatching { client.auth.signOut() }
        Unit
    }

    /* ---------------------------------------------------------------- tables */

    suspend fun links(): List<LinkDto> = withContext(Dispatchers.IO) {
        client.from("links").select().decodeList<LinkDto>()
            .sortedByDescending { it.created_at }
    }

    suspend fun visits(): List<VisitDto> = withContext(Dispatchers.IO) {
        client.from("visits").select().decodeList<VisitDto>()
            .sortedByDescending { it.first_seen_at }
    }

    suspend fun captures(): List<CaptureDto> = withContext(Dispatchers.IO) {
        client.from("captures").select().decodeList<CaptureDto>()
            .sortedByDescending { it.captured_at }
    }

    /**
     * Creates a link and returns its token. The row is inserted with the token
     * the caller generated, so no round-trip is needed to read it back.
     */
    suspend fun createLink(label: String, config: LinkConfig): String = withContext(Dispatchers.IO) {
        val token = newToken()
        client.from("links").insert(
            LinkInsert(
                link_token = token,
                label = label.trim().ifBlank { "Untitled link" },
                config = config,
                created_by = userId(),
            )
        )
        token
    }

    /* --------------------------------------------------------------- storage */

    /** Short-lived signed URL for a private object; null when it cannot be created. */
    suspend fun signedUrl(path: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            client.storage.from(Config.BUCKET).createSignedUrl(path, 1.hours)
        }.getOrNull()
    }

    /* ----------------------------------------------------------------- misc */

    fun newToken(): String {
        val alphabet = "abcdefghijklmnopqrstuvwxyz0123456789"
        return (1..20).map { alphabet[Random.nextInt(alphabet.length)] }.joinToString("")
    }

    companion object {
        fun payloadValue(payload: JsonObject, key: String): String? =
            (payload[key] as? JsonPrimitive)?.content

        fun coordinates(payload: JsonObject): Pair<Double, Double>? {
            val lat = (payload["lat"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            val lng = (payload["lng"] as? JsonPrimitive)?.content?.toDoubleOrNull()
            return if (lat != null && lng != null) lat to lng else null
        }

        fun emptyObject(): JsonObject = buildJsonObject { }
    }
}
