package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.models.LanguageJsonItem
import com.example.roamly.data.utils.SupabaseClientProvider
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LanguageRepository {

    suspend fun fetchAllLanguages(): List<LanguageJsonItem> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("languages")
                    .select(Columns.list("id", "name"))
                    .decodeList<LanguageJsonItem>() // id → code, name → name
            }
        } catch (e: Exception) {
            Log.e("LanguageRepository", "Errore caricamento lingue: ${e.message}", e)
            emptyList()
        }
    }
}