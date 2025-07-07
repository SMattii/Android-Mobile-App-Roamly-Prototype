package com.example.roamly.data.models

import kotlinx.serialization.Serializable

/**
 * Modello dati utilizzato per l'inserimento di un nuovo messaggio
 * all'interno della chat di gruppo di un evento.
 *
 * Viene serializzato e inviato a Supabase per la creazione di un nuovo record nella tabella `event_messages`.
 *
 * @property event_id ID dell'evento a cui è destinato il messaggio.
 * @property sender_id ID del profilo utente che invia il messaggio.
 * @property message Contenuto testuale del messaggio da inviare.
 */

@Serializable
data class EventMessageInsert(
    val event_id: String,
    val sender_id: String,
    val message: String
)