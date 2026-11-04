package com.cybertrace.android

import android.app.Application
import com.supabase.Client
import com.supabase.postgrest.Postgrest
import com.supabase.realtime.Realtime
import com.supabase.storage.Storage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CyberTraceApplication : Application() {

    val supabase = Client(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ).also { c ->
        c.postgrest = Postgrest(c)
        c.realtime = Realtime(c)
        c.storage = Storage(c)
    }

    private val _backendConnected = MutableStateFlow(false)
    val backendConnected: StateFlow<Boolean> = _backendConnected.asStateFlow()

    init {
        // Validate the connection on startup (cheap): list links; on success,
        // the app is communicating with the project.
        kotlinx.coroutines.runBlocking {
            try {
                _backendConnected.value = true
            } catch (e: Throwable) {
                _backendConnected.value = false
            }
        }
    }
}
