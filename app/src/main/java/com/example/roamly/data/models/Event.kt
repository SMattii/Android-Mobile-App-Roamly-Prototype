package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un evento geolocalizzato creato da un utente.
 * Include informazioni su posizione, data, partecipanti, interessi, lingue e limiti di accesso.
 *
 * Questo modello viene serializzato/deserializzato per comunicare con Supabase.
 *
 * @property id ID univoco dell'evento.
 * @property desc Descrizione testuale dell'evento.
 * @property profile_id ID del profilo utente che ha creato l'evento.
 * @property latitude Latitudine della posizione dell'evento.
 * @property longitude Longitudine della posizione dell'evento.
 * @property event_type Tipo di evento (es. "chill", "party") al momento coincide con vibe.
 * @property interests Lista di ID degli interessi associati all'evento.
 * @property languages Lista di codici ISO 639-1 delle lingue parlate all'evento.
 * @property date Data dell'evento in formato ISO 8601 (es. "2025-07-03").
 * @property time Ora dell'evento in formato "HH:mm" (24h).
 * @property min_age Età minima richiesta per partecipare.
 * @property max_age Età massima ammessa per partecipare.
 * @property max_participants Numero massimo di partecipanti.
 * @property vibe Vibe associata all'evento (es. "chill", "party").
 * @property created_at Timestamp di creazione (ISO 8601, assegnato da Supabase).
 * @property visible_until Data e ora fino a cui l'evento è visibile sulla mappa (ISO 8601, UTC).
 */

@Serializable
data class Event(
    val id: String? = null,
    val desc: String,
    val profile_id: String,
    val latitude: Double,
    val longitude: Double,
    val event_type: String,
    val interests: List<String>,
    val languages: List<String>,
    val date: String,         // ISO 8601 (es: "2025-07-03")
    val time: String,         // "HH:mm"
    val min_age: Int? = null,
    val max_age: Int? = null,
    val max_participants: Int? = null,
    val vibe: String? = null,
    val created_at: String? = null,
    val visible_until: String? = null
)