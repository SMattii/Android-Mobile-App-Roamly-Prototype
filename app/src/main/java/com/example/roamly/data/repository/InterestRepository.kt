package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.models.Interest
import com.example.roamly.data.utils.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class InterestRepository {

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