package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.models.Interest
import com.example.roamly.data.utils.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Repository per la gestione degli interessi utente.
 *
 * Fornisce metodi per recuperare gli interessi disponibili dalla tabella `interests` su Supabase.
 */
object InterestRepository {

    /**
     * Recupera tutti gli interessi disponibili dal database Supabase.
     *
     * @return Lista di oggetti [Interest], oppure vuota in caso di errore.
     */
    suspend fun fetchAllInterests(): List<Interest> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("interests")
                    .select(Columns.list("id", "name"))
                    .decodeList<Interest>()
            }
        } catch (e: Exception) {
            Log.e("InterestRepository", "Errore caricamento interessi: ${e.message}", e)
            emptyList()
        }
    }
}