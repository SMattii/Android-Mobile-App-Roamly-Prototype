package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.utils.SupabaseClientProvider
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.EventParticipant
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EventRepository {

    suspend fun createEvent(event: Event): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("events").insert(event)
            }
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_CREATE_EVENT", "Errore durante la creazione evento: ${e.message}", e)
            false
        }
    }

    suspend fun deleteEvent(eventId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("events").delete {
                    filter { eq("id", eventId) }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_DELETE_EVENT", "Errore durante la cancellazione evento: ${e.message}", e)
            false
        }
    }

    suspend fun getEvents(): List<Event> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("events")
                    .select(Columns.raw("*"))
                    .decodeList<Event>()
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_GET_EVENTS", "Errore durante il fetch degli eventi: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun addParticipant(eventId: String, profileId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                val entry = EventParticipant(event_id = eventId, profile_id = profileId)
                SupabaseClientProvider.db.from("event_participants").insert(entry)
            }
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_ADD_PARTICIPANT", "Errore aggiunta partecipante: ${e.message}", e)
            false
        }
    }

    suspend fun removeParticipant(eventId: String, profileId: String): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("event_participants").delete {
                    filter {
                        eq("event_id", eventId)
                        eq("profile_id", profileId)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_REMOVE_PARTICIPANT", "Errore rimozione partecipante: ${e.message}", e)
            false
        }
    }

    suspend fun getEventParticipants(eventId: String): List<String> {
        return try {
            withContext(Dispatchers.IO) {
                SupabaseClientProvider.db.from("event_participants")
                    .select(Columns.raw("profile_id")) {
                        filter { eq("event_id", eventId) }
                    }
                    .decodeList<EventParticipant>()
                    .map { it.profile_id }
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_GET_PARTICIPANTS", "Errore fetch partecipanti: ${e.message}", e)
            emptyList()
        }
    }
}