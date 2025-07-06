package com.example.roamly.data.repository

import android.util.Log
import com.example.roamly.data.utils.SupabaseClientProvider
import com.example.roamly.data.models.Event
import com.example.roamly.data.models.EventMessage
import com.example.roamly.data.models.EventMessageInsert
import com.example.roamly.data.models.EventParticipant
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object EventRepository {

    suspend fun createEvent(event: Event): Event? {
        return try {
            withContext(Dispatchers.IO) {
                // 1. Calcola visible_until a partire da date + time
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                val localDateTime = java.time.LocalDateTime.parse("${event.date} ${event.time}", formatter)

                val visibleUntil = localDateTime
                    .plusHours(1)
                    .atZone(java.time.ZoneId.systemDefault())
                    .withZoneSameInstant(java.time.ZoneOffset.UTC)
                    .toOffsetDateTime()
                    .toString()

                val eventWithVisibleUntil = event.copy(visible_until = visibleUntil)

                // 2. Inserisci evento e recupera la riga restituita
                val insertedEvents = SupabaseClientProvider.db.from("events")
                    .insert(eventWithVisibleUntil) {
                        select() // restituisce l'oggetto inserito
                    }
                    .decodeList<Event>()

                val insertedEvent = insertedEvents.firstOrNull()

                // 3. Inserisci il creator come partecipante
                val userId = SupabaseClientProvider.auth.currentUserOrNull()?.id
                if (userId != null && insertedEvent?.id != null) {
                    val entry = EventParticipant(event_id = insertedEvent.id, profile_id = userId)
                    SupabaseClientProvider.db.from("event_participants").insert(entry)
                    Log.d("EVENT_CREATE", "Creatore aggiunto come partecipante: $userId")
                } else {
                    Log.w("EVENT_CREATE", "UserID o EventID null: partecipante non aggiunto.")
                }

                insertedEvent
            }
        } catch (e: Exception) {
            Log.e("SUPABASE_CREATE_EVENT", "Errore durante la creazione evento: ${e.message}", e)
            null
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
                val now = java.time.OffsetDateTime.now(java.time.ZoneOffset.UTC).toString()

                SupabaseClientProvider.db.from("events")
                    .select(Columns.raw("*")) {
                        filter {
                            gte("visible_until", now)
                        }
                    }
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

    suspend fun getEventParticipants(eventId: String?): List<String> {
        if (eventId.isNullOrBlank()) {
            Log.e("SUPABASE_GET_PARTICIPANTS", "❌ ID evento nullo o vuoto")
            return emptyList()
        }
        return try {
            SupabaseClientProvider.db.from("event_participants")
                .select(Columns.list("event_id", "profile_id")){
                    filter { eq("event_id", eventId) }
                }
                .decodeList<EventParticipant>()
                .map { it.profile_id }
        } catch (e: Exception) {
            Log.e("SUPABASE_GET_PARTICIPANTS", "Errore fetch partecipanti: ${e.message}", e)
            emptyList()
        }
    }

    suspend fun updateEvent(event: Event): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Ricalcola `visible_until` in base a data + ora modificata
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

                // ✅ Pulisce i secondi (es: da "20:00:00" → "20:00")
                val cleanTime = event.time.take(5)

                val localDateTime = java.time.LocalDateTime.parse("${event.date} $cleanTime", formatter)

                val visibleUntil = localDateTime
                    .plusHours(1)
                    .atZone(java.time.ZoneId.systemDefault())
                    .withZoneSameInstant(java.time.ZoneOffset.UTC)
                    .toOffsetDateTime()
                    .toString()

                val updatedEvent = event.copy(visible_until = visibleUntil)

                SupabaseClientProvider.db.from("events")
                    .update(updatedEvent) {
                        filter {
                            eq("id", event.id!!)
                        }
                    }
            }
            Log.d("SUPABASE_UPDATE_EVENT", "✅ Evento aggiornato con successo")
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_UPDATE_EVENT", "❌ Errore aggiornamento evento: ${e.message}", e)
            false
        }
    }

    suspend fun getMessagesForEvent(eventId: String): List<EventMessage> {
        return SupabaseClientProvider.db
            .from("event_messages")
            .select {
                filter {
                    eq("event_id", eventId)
                }
                order("created_at", Order.ASCENDING) // ordine crescente
            }
            .decodeList<EventMessage>()
    }

    suspend fun sendMessageToEvent(eventId: String, senderId: String, message: String) {
        SupabaseClientProvider.db
            .from("event_messages")
            .insert(
                EventMessageInsert(
                    event_id = eventId,
                    sender_id = senderId,
                    message = message
                )
            )
    }

    suspend fun getEventsWithUserParticipation(userId: String): List<Event> {
        val participations = SupabaseClientProvider.db.from("event_participants")
            .select {
                filter { eq("profile_id", userId) }
            }
            .decodeList<EventParticipant>()

        if (participations.isEmpty()) {
            return emptyList()
        }

        val eventIds = participations.map { it.event_id }

        return SupabaseClientProvider.db.from("events")
            .select {
                filter {
                    Event::id isIn eventIds
                }
            }
            .decodeList<Event>()
    }
}