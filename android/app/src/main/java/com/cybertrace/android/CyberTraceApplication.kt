package com.cybertrace.android

import android.app.Application
import com.cybertrace.android.data.Config
import com.cybertrace.android.data.Repo
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

class CyberTraceApplication : Application() {

    lateinit var repo: Repo
        private set

    override fun onCreate() {
        super.onCreate()

        val client = createSupabaseClient(
            supabaseUrl = Config.SUPABASE_URL,
            supabaseKey = Config.SUPABASE_ANON_KEY,
        ) {
            // Session is persisted on device and refreshed automatically.
            install(Auth)
            install(Postgrest)
            install(Storage)
            install(Realtime)
        }

        repo = Repo(client)
    }
}
