package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.models.ProfileComplete
import com.example.roamly.data.utils.SupabaseClientProvider
import com.example.roamly.data.models.InterestLink
import com.example.roamly.data.models.Language
import com.example.roamly.data.models.LanguageLink
import com.example.roamly.data.models.NearbyUserProfile
import com.example.roamly.data.models.LocationEntry
import com.example.roamly.data.models.Profile
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ProfileRepository {

    suspend fun saveLocationToSupabase(userId: String, latitude: Double, longitude: Double) {
        try {
            val entry = LocationEntry(user_id = userId, latitude = latitude, longitude = longitude)
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("locations").upsert(entry) {
                    filter { eq("user_id", userId) }
                }
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_SAVE", "Errore salvataggio posizione: ${e.message}", e)
        }
    }

    suspend fun fetchNearbyProfilesWithDetails(userIds: List<String>): Map<String, NearbyUserProfile> {
        val db = SupabaseClientProvider.db

        val profiles = db.from("profiles")
            .select(Columns.raw("id, full_name, age, country, category, vibe, profile_image_url")) {
                filter { isIn("id", userIds) }
            }
            .decodeList<NearbyUserProfile>()

        val interests = db.from("profile_interests")
            .select(Columns.raw("profile_id, interests(id, name)")) {
                filter { isIn("profile_id", userIds) }
            }
            .decodeList<InterestLink>()

        val languages = db.from("profile_languages")
            .select(Columns.raw("profile_id, language_id")) {
                filter { isIn("profile_id", userIds) }
            }
            .decodeList<LanguageLink>()

        val interestsMap = interests.groupBy { it.profile_id }
            .mapValues { it.value.map { link -> link.interest.name } }

        val languagesMap = languages.groupBy { it.profile_id }
            .mapValues { it.value.map { link -> link.language_id } }

        return profiles.associateBy { it.id }.mapValues { (_, profile) ->
            profile.copy(
                interests = interestsMap[profile.id] ?: emptyList(),
                languages = languagesMap[profile.id] ?: emptyList()
            )
        }
    }

    suspend fun getProfilesByIds(ids: List<String>): List<Profile> {
        if (ids.isEmpty()) {
            Log.w("SUPABASE_PROFILES", "Nessun ID ricevuto, ritorno lista vuota.")
            return emptyList()
        }

        return try {
            val profiles = SupabaseClientProvider.db
                .from("profiles")
                .select {
                    filter {
                        Profile::id isIn ids
                    }
                }
                .decodeList<Profile>()

            Log.d("SUPABASE_PROFILES", "Richiesti ${ids.size} profili, ricevuti ${profiles.size}")
            profiles
        } catch (e: Exception) {
            Log.e("SUPABASE_PROFILES", "Errore fetch profili: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun getCompleteProfile(userId: String): ProfileComplete? {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Carica il profilo base
                val profile = SupabaseClientProvider.db.from("profiles")
                    .select {
                        filter { eq("id", userId) }
                    }
                    .decodeSingleOrNull<Profile>()

                if (profile == null) {
                    Log.e("ProfileRepository", "Profilo non trovato per l'utente: $userId")
                    return@withContext null
                }

                // 2. Carica le lingue selezionate
                val languageLinks = SupabaseClientProvider.db.from("profile_languages")
                    .select {
                        filter { eq("profile_id", userId) }
                    }
                    .decodeList<LanguageLink>()

                val languageIds = languageLinks.map { it.language_id }
                val selectedLanguages = if (languageIds.isNotEmpty()) {
                    SupabaseClientProvider.db.from("languages")
                        .select(Columns.list("id", "name")) {
                            filter { isIn("id", languageIds) }
                        }
                        .decodeList<Language>()
                } else {
                    emptyList()
                }

                // 3. Carica gli interessi selezionati
                val interestLinks = SupabaseClientProvider.db.from("profile_interests")
                    .select(Columns.raw("profile_id, interests(id, name)")) {
                        filter { eq("profile_id", userId) }
                    }
                    .decodeList<InterestLink>()

                val selectedInterests = interestLinks.map { it.interest }

                ProfileComplete(
                    profile = profile,
                    selectedLanguages = selectedLanguages,
                    selectedInterests = selectedInterests
                )
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Errore caricamento profilo completo: ${e.message}", e)
            null
        }
    }

    // Aggiorna il profilo base
    suspend fun updateProfile(profile: Profile): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("profiles")
                    .update(profile) {
                        filter { eq("id", profile.id) }
                    }
                Log.d("ProfileRepository", "Profilo aggiornato con successo")
                true
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Errore aggiornamento profilo: ${e.message}", e)
            false
        }
    }

    // Aggiorna le lingue del profilo
    suspend fun updateProfileLanguages(userId: String, languageIds: List<String>): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Cancella le lingue esistenti
                SupabaseClientProvider.db.from("profile_languages")
                    .delete {
                        filter { eq("profile_id", userId) }
                    }

                // 2. Inserisci le nuove lingue
                if (languageIds.isNotEmpty()) {
                    val languageLinks = languageIds.map { languageId ->
                        LanguageLink(profile_id = userId, language_id = languageId)
                    }

                    SupabaseClientProvider.db.from("profile_languages")
                        .insert(languageLinks)
                }

                Log.d("ProfileRepository", "Lingue aggiornate con successo")
                true
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Errore aggiornamento lingue: ${e.message}", e)
            false
        }
    }

    // Aggiorna gli interessi del profilo
    suspend fun updateProfileInterests(userId: String, interestIds: List<String>): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Cancella gli interessi esistenti
                SupabaseClientProvider.db.from("profile_interests")
                    .delete {
                        filter { eq("profile_id", userId) }
                    }

                // 2. Inserisci i nuovi interessi
                if (interestIds.isNotEmpty()) {
                    val interestLinks = interestIds.map { interestId ->
                        mapOf(
                            "profile_id" to userId,
                            "interest_id" to interestId
                        )
                    }

                    SupabaseClientProvider.db.from("profile_interests")
                        .insert(interestLinks)
                }

                Log.d("ProfileRepository", "Interessi aggiornati con successo")
                true
            }
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Errore aggiornamento interessi: ${e.message}", e)
            false
        }
    }

    // Metodo unificato per salvare tutto
    suspend fun saveCompleteProfile(
        profile: Profile,
        languageIds: List<String>,
        interestIds: List<String>
    ): Boolean {
        return try {
            // Salva tutto in sequenza
            val profileSuccess = updateProfile(profile)
            val languagesSuccess = updateProfileLanguages(profile.id, languageIds)
            val interestsSuccess = updateProfileInterests(profile.id, interestIds)

            val allSuccess = profileSuccess && languagesSuccess && interestsSuccess

            if (allSuccess) {
                Log.d("ProfileRepository", "Profilo completo salvato con successo")
            } else {
                Log.e("ProfileRepository", "Errore nel salvataggio del profilo completo")
            }

            allSuccess
        } catch (e: Exception) {
            Log.e("ProfileRepository", "Errore salvataggio profilo completo: ${e.message}", e)
            false
        }
    }
}