package com.cybertrace.android.data

/**
 * Project endpoints.
 *
 * The anon key is a *publishable* key: it is shipped in the client on purpose and
 * carries no privileges beyond what Row Level Security allows. Every table is
 * locked down, so this key alone can read nothing. Never put the service_role
 * key in an app.
 */
object Config {
    const val SUPABASE_URL = "https://yrmdsvkxpyejpmphfstb.supabase.co"

    const val SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InlybWRzdmt4cHllanBtcGhmc3RiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTM3NDMxMjUsImV4cCI6MjEwOTMxOTEyNX0.g8VO892KEg5n9Lc3bjYbDCxEti-WI6FqKCSoYvnaIvg"

    /** Host for the capture page; links resolve to "$PAGES_BASE/c/{token}". */
    const val PAGES_BASE = "https://mtn.freebuff.app"

    const val BUCKET = "captures"

    fun linkUrl(token: String): String = "$PAGES_BASE/c/$token"
}
