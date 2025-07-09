package com.example.roamly.data.utils

import com.example.roamly.data.models.Event
import com.example.roamly.data.models.NearbyUserProfile

/**
 * `DataCache` è una cache in-memory condivisa a livello di app,
 * utile per evitare fetch ripetuti da Supabase o dalla rete.
 *
 * Contiene:
 * - una cache degli utenti vicini (`NearbyUserProfile`) indicizzati per `userId`
 * - una cache degli eventi (`Event`) indicizzati per `eventId`
 *
 * Viene svuotata in caso di logout o reset, ed è pensata per essere ripopolata
 * periodicamente durante l'uso dell'app (es. polling ogni 30 secondi).
 */
object DataCache {

    // Mappa che memorizza i profili utente vicini per ID
    private val userCache = mutableMapOf<String, NearbyUserProfile>()

    // Mappa che memorizza gli eventi per ID
    private val eventCache = mutableMapOf<String, Event>()

    /* ------------ USERS ------------ */

    /**
     * Restituisce il profilo utente vicino associato all'ID specificato.
     *
     * @param id L'ID dell'utente.
     * @return Il profilo utente se presente in cache, altrimenti null.
     */
    fun getUser(id: String): NearbyUserProfile? = userCache[id]

    /**
     * Aggiunge o aggiorna più profili utente nella cache.
     *
     * @param users Collezione di utenti da memorizzare nella cache.
     */
    fun putUsers(users: Collection<NearbyUserProfile>) {
        users.forEach { userCache[it.id] = it }
    }

    /* ------------ EVENTS ------------ */

    /**
     * Restituisce l'evento associato all'ID specificato.
     *
     * @param id L'ID dell'evento.
     * @return L'evento se presente in cache, altrimenti null.
     */
    fun getEvent(id: String): Event? = eventCache[id]

    /**
     * Aggiunge o aggiorna più eventi nella cache.
     * Solo eventi con un ID non nullo vengono memorizzati.
     *
     * @param events Collezione di eventi da inserire in cache.
     */
    fun putEvents(events: Collection<Event>) {
        events.forEach { ev -> ev.id?.let { eventCache[it] = ev } }
    }

    /**
     * Pulisce completamente entrambe le cache (utenti ed eventi).
     * Da chiamare ad esempio durante il logout o un reset globale.
     */
    fun clear() {
        userCache.clear()
        eventCache.clear()
    }
}