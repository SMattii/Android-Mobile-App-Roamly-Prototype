package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta la partecipazione di un utente a un evento.
 *
 * Ogni record lega un profilo a un evento, e può contenere un timestamp di iscrizione.
 *
 * Utilizzato per gestire le relazioni molti-a-molti tra eventi e partecipanti nella tabella `event_participants` su Supabase.
 *
 * @property event_id ID dell'evento a cui l'utente partecipa.
 * @property profile_id ID del profilo utente partecipante.
 * @property joined_at Timestamp della partecipazione, assegnato automaticamente da Supabase (ISO 8601, opzionale).
 */
@Serializable
data class EventParticipant(
    val event_id: String,
    val profile_id: String,
    val joined_at: String? = null // viene riempito da Supabase
)