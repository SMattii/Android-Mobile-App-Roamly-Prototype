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

/**
 * Repository centralizzato per la gestione degli eventi su Supabase.
 *
 * Include operazioni CRUD per eventi, partecipazioni, e messaggi di chat evento.
 * Tutte le chiamate sono sospese e ottimizzate per essere eseguite su Dispatchers.IO.
 */
object EventRepository {

    /**
     * Crea un nuovo evento su Supabase con calcolo automatico del campo `visible_until`.
     * Registra anche il creatore come primo partecipante.
     *
     * @param event Oggetto evento da creare.
     * @return Evento creato con ID assegnato da Supabase, oppure null in caso di errore.
     */
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

    /**
     * Elimina un evento esistente in base al suo ID.
     *
     * @param eventId ID dell'evento da cancellare.
     * @return `true` se l'eliminazione ha avuto successo, `false` in caso di errore.
     */
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

    /**
     * Recupera tutti gli eventi ancora visibili (dove `visible_until` è maggiore dell'orario corrente).
     *
     * @return Lista di eventi visibili, oppure vuota in caso di errore.
     */
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

    /**
     * Aggiunge un utente come partecipante a un evento.
     *
     * @param eventId ID dell'evento.
     * @param profileId ID del profilo dell'utente partecipante.
     * @return `true` se l'aggiunta ha avuto successo, `false` in caso di errore.
     */
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

    /**
     * Rimuove un utente dalla lista dei partecipanti di un evento.
     *
     * @param eventId ID dell'evento.
     * @param profileId ID del profilo da rimuovere.
     * @return `true` se la rimozione ha avuto successo, `false` in caso di errore.
     */
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

    /**
     * Recupera la lista di ID utente che partecipano a un dato evento.
     *
     * @param eventId ID dell'evento.
     * @return Lista di ID dei partecipanti, oppure vuota in caso di errore o ID non valido.
     */
    suspend fun getEventParticipants(eventId: String?): List<String> {
        if (eventId.isNullOrBlank()) {
            Log.e("SUPABASE_GET_PARTICIPANTS", "ID evento nullo o vuoto")
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

    /**
     * Aggiorna un evento su Supabase, ricalcolando il campo `visible_until`
     * in base a data e orario aggiornati.
     *
     * @param event Evento aggiornato da salvare.
     * @return `true` se l’aggiornamento ha avuto successo, `false` in caso di errore.
     */
    suspend fun updateEvent(event: Event): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                // Ricalcola `visible_until` in base a data + ora modificata
                val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

                // Pulisce i secondi (es: da "20:00:00" → "20:00")
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
            Log.d("SUPABASE_UPDATE_EVENT", "Evento aggiornato con successo")
            true
        } catch (e: Exception) {
            Log.e("SUPABASE_UPDATE_EVENT", "Errore aggiornamento evento: ${e.message}", e)
            false
        }
    }

    /**
     * Recupera tutti i messaggi della chat associata a un evento, ordinati cronologicamente.
     *
     * @param eventId ID dell'evento.
     * @return Lista di messaggi associati all'evento.
     */
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

    /**
     * Invia un nuovo messaggio nella chat dell’evento.
     *
     * @param eventId ID dell'evento.
     * @param senderId ID dell’utente che invia il messaggio.
     * @param message Contenuto testuale del messaggio.
     */
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

    /**
     * Recupera tutti gli eventi a cui un dato utente partecipa.
     *
     * @param userId ID del profilo utente.
     * @return Lista di eventi a cui l’utente partecipa.
     */
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