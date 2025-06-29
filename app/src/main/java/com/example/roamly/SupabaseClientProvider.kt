package com.example.roamly

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth) {
                autoLoadFromStorage = true
            }
            install(Postgrest)
            install(Storage)

            httpEngine = OkHttp.create()
        }
    }

    val auth     get() = supabase.auth
    val db       get() = supabase.postgrest
    val storage  get() = supabase.storage
}