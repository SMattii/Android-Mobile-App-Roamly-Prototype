package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati che rappresenta un messaggio inviato all'interno della chat di gruppo di un evento.
 * Ogni messaggio è associato a un evento specifico e a un mittente.
 *
 * Questo modello viene utilizzato per il recupero e la visualizzazione dei messaggi da Supabase.
 *
 * @property id ID univoco del messaggio.
 * @property event_id ID dell'evento a cui appartiene il messaggio.
 * @property sender_id ID del profilo utente che ha inviato il messaggio.
 * @property message Contenuto testuale del messaggio.
 * @property created_at Timestamp di creazione del messaggio in formato ISO 8601.
 */
@Serializable
data class EventMessage(
    val id: String,
    val event_id: String,
    val sender_id: String,
    val message: String,
    val created_at: String
)