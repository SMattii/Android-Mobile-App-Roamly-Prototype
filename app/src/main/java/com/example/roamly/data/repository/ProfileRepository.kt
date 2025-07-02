package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.SupabaseClientProvider
import com.example.roamly.data.models.InterestLinkWithName
import com.example.roamly.data.models.LanguageLink
import com.example.roamly.data.models.NearbyUserProfile
import com.example.roamly.LocationEntry
import com.example.roamly.Profile
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileRepository {

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
            .decodeList<InterestLinkWithName>()

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
}