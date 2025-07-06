package com.example.roamly.data.utils

import com.example.roamly.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.serializer.KotlinXSerializer
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.Json

object SupabaseClientProvider {

    private val customJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // 👈 Forza anche i default come `visible = true`
    }

    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            // Imposta il serializer globale per tutto il client
            defaultSerializer = KotlinXSerializer(customJson)

            install(Auth) {
                autoLoadFromStorage = true
            }

            install(Postgrest)
            install(Storage)

            httpEngine = OkHttp.create()
        }
    }

    val auth get() = supabase.auth
    val db get() = supabase.postgrest
    val storage get() = supabase.storage
}