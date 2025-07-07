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

/**
 * Provider centralizzato per l'accesso ai servizi Supabase: autenticazione, database (PostgREST) e storage.
 *
 * Inizializza e configura il client Supabase con serializer custom e moduli abilitati.
 * Espone shortcut per `auth`, `db` e `storage` per semplificare l'accesso in tutta l'app.
 */
object SupabaseClientProvider {

    /**
     * Configurazione personalizzata di `Json` per la serializzazione/deserializzazione,
     * compatibile con il formato usato da Supabase.
     */
    private val customJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true // Forza anche i default come `visible = true`
    }

    /**
     * Client Supabase inizializzato lazy con Auth, Postgrest e Storage abilitati.
     */
    val supabase: SupabaseClient by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            defaultSerializer = KotlinXSerializer(customJson)

            install(Auth) {
                autoLoadFromStorage = true
            }

            install(Postgrest)
            install(Storage)

            httpEngine = OkHttp.create()
        }
    }

    /**
     * Shortcut per il modulo di autenticazione Supabase.
     */
    val auth get() = supabase.auth

    /**
     * Shortcut per il modulo PostgREST (database Supabase).
     */
    val db get() = supabase.postgrest

    /**
     * Shortcut per il modulo di storage Supabase.
     */
    val storage get() = supabase.storage
}